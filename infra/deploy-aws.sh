#!/usr/bin/env bash
# =============================================================================
# Devin FinOps Dashboard - AWS Deployment Script
#
# Deploys the application to AWS ECS Fargate using CloudFormation.
#
# Prerequisites:
#   - AWS CLI v2 configured with appropriate credentials
#   - Docker installed and running
#   - The CloudFormation template (cloudformation.yaml) in the same directory
#
# Usage:
#   ./deploy-aws.sh                          # deploy with defaults
#   ./deploy-aws.sh --region us-west-2       # deploy to specific region
#   ./deploy-aws.sh --env staging            # deploy with custom env name
#   ./deploy-aws.sh --skip-build             # skip Docker build (use existing images)
#
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration (override via CLI flags or environment variables)
# ---------------------------------------------------------------------------
AWS_REGION="${AWS_REGION:-us-east-1}"
ENVIRONMENT_NAME="${ENVIRONMENT_NAME:-devin-finops}"
STACK_NAME="${ENVIRONMENT_NAME}-stack"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SKIP_BUILD=false
IMAGE_TAG="${IMAGE_TAG:-latest}"

# ---------------------------------------------------------------------------
# Parse CLI arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case $1 in
    --region)
      AWS_REGION="$2"
      shift 2
      ;;
    --env)
      ENVIRONMENT_NAME="$2"
      STACK_NAME="${ENVIRONMENT_NAME}-stack"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    --tag)
      IMAGE_TAG="$2"
      shift 2
      ;;
    --help)
      echo "Usage: $0 [--region REGION] [--env ENV_NAME] [--skip-build] [--tag IMAGE_TAG]"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------
log_info() {
  echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') $*"
}

log_error() {
  echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') $*" >&2
}

check_prerequisites() {
  local missing=false

  if ! command -v aws &> /dev/null; then
    log_error "AWS CLI not found. Install: https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html"
    missing=true
  fi

  if ! command -v docker &> /dev/null; then
    log_error "Docker not found. Install: https://docs.docker.com/get-docker/"
    missing=true
  fi

  if [ "$missing" = true ]; then
    exit 1
  fi

  # Verify AWS credentials
  if ! aws sts get-caller-identity &> /dev/null; then
    log_error "AWS credentials not configured. Run 'aws configure' first."
    exit 1
  fi
}

# ---------------------------------------------------------------------------
# Main deployment flow
# ---------------------------------------------------------------------------
main() {
  log_info "Deploying Devin FinOps Dashboard to AWS"
  log_info "  Region:      ${AWS_REGION}"
  log_info "  Environment: ${ENVIRONMENT_NAME}"
  log_info "  Stack:       ${STACK_NAME}"
  log_info "  Image tag:   ${IMAGE_TAG}"

  check_prerequisites

  AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
  BACKEND_ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ENVIRONMENT_NAME}/backend"
  FRONTEND_ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ENVIRONMENT_NAME}/frontend"

  # Step 1: Deploy CloudFormation stack (creates ECR repos, ECS cluster, etc.)
  log_info "Step 1/4: Deploying CloudFormation stack..."
  aws cloudformation deploy \
    --region "${AWS_REGION}" \
    --template-file "${SCRIPT_DIR}/cloudformation.yaml" \
    --stack-name "${STACK_NAME}" \
    --parameter-overrides \
      EnvironmentName="${ENVIRONMENT_NAME}" \
      BackendImageTag="${IMAGE_TAG}" \
      FrontendImageTag="${IMAGE_TAG}" \
    --capabilities CAPABILITY_NAMED_IAM \
    --no-fail-on-empty-changeset

  if [ "$SKIP_BUILD" = true ]; then
    log_info "Skipping Docker build (--skip-build flag set)"
  else
    # Step 2: Authenticate Docker to ECR
    log_info "Step 2/4: Authenticating Docker to ECR..."
    aws ecr get-login-password --region "${AWS_REGION}" | \
      docker login --username AWS --password-stdin \
      "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

    # Step 3: Build and push Docker images
    log_info "Step 3/4: Building and pushing Docker images..."

    log_info "  Building backend image..."
    docker build \
      -t "${BACKEND_ECR_URI}:${IMAGE_TAG}" \
      -f "${PROJECT_ROOT}/backend/Dockerfile" \
      "${PROJECT_ROOT}"

    log_info "  Building frontend image..."
    docker build \
      -t "${FRONTEND_ECR_URI}:${IMAGE_TAG}" \
      -f "${PROJECT_ROOT}/frontend/Dockerfile" \
      "${PROJECT_ROOT}/frontend"

    log_info "  Pushing backend image..."
    docker push "${BACKEND_ECR_URI}:${IMAGE_TAG}"

    log_info "  Pushing frontend image..."
    docker push "${FRONTEND_ECR_URI}:${IMAGE_TAG}"
  fi

  # Step 4: Force new deployment to pick up latest images
  log_info "Step 4/4: Forcing new ECS service deployment..."
  aws ecs update-service \
    --region "${AWS_REGION}" \
    --cluster "${ENVIRONMENT_NAME}-cluster" \
    --service "${ENVIRONMENT_NAME}-backend" \
    --force-new-deployment \
    --no-cli-pager > /dev/null

  aws ecs update-service \
    --region "${AWS_REGION}" \
    --cluster "${ENVIRONMENT_NAME}-cluster" \
    --service "${ENVIRONMENT_NAME}-frontend" \
    --force-new-deployment \
    --no-cli-pager > /dev/null

  # Output the dashboard URL
  ALB_DNS=$(aws cloudformation describe-stacks \
    --region "${AWS_REGION}" \
    --stack-name "${STACK_NAME}" \
    --query "Stacks[0].Outputs[?OutputKey=='ALBDnsName'].OutputValue" \
    --output text)

  log_info "=========================================="
  log_info "Deployment complete!"
  log_info "Dashboard URL: http://${ALB_DNS}"
  log_info "=========================================="
  log_info ""
  log_info "IMPORTANT: Update the Secrets Manager secret with your API tokens:"
  log_info "  aws secretsmanager put-secret-value \\"
  log_info "    --region ${AWS_REGION} \\"
  log_info "    --secret-id ${ENVIRONMENT_NAME}/api-tokens \\"
  log_info "    --secret-string '{\"DEVIN_ENTERPRISE_SERVICE_TOKEN\":\"YOUR_TOKEN\",\"DEVIN_ORG_SERVICE_TOKEN\":\"YOUR_TOKEN\",\"DEVIN_ORG_ID\":\"\"}'"
}

main "$@"
