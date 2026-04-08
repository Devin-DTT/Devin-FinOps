# -----------------------------------------------------------------------------
# EKS Cluster Module
# Uses the official terraform-aws-modules/eks/aws for managed Kubernetes
# -----------------------------------------------------------------------------

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = var.cluster_name
  cluster_version = "1.29"

  vpc_id     = var.vpc_id
  subnet_ids = var.private_subnet_ids

  cluster_endpoint_public_access = true
  enable_irsa                    = true

  eks_managed_node_groups = {
    default = {
      instance_types = var.node_instance_types
      min_size       = var.node_min_size
      max_size       = var.node_max_size
      desired_size   = var.node_desired_size
      disk_size      = 50
    }
  }
}

# ECR repositories (one per service)
resource "aws_ecr_repository" "services" {
  for_each = toset(var.service_names)
  name     = "${var.cluster_name}/${each.value}"

  image_scanning_configuration {
    scan_on_push = true
  }

  image_tag_mutability = "MUTABLE"
}

# Secrets Manager for Devin API tokens
resource "aws_secretsmanager_secret" "devin_tokens" {
  name = "${var.cluster_name}/api-tokens"
}

# IRSA role for External Secrets Operator
module "eso_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name                      = "${var.cluster_name}-eso"
  attach_external_secrets_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["external-secrets:external-secrets"]
    }
  }
}
