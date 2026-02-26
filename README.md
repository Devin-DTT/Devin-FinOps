# Devin FinOps Dashboard

Full-stack FinOps dashboard for monitoring Devin Enterprise usage, costs, and productivity metrics.

## Architecture

- **Backend**: Spring Boot 3 (Java 17) with WebFlux, WebSocket streaming, and reactive API client.
- **Frontend**: Angular 17 single-page application with real-time dashboard.

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+ and Angular CLI (for the frontend)
- A **Devin service user token** (see below)

## Authentication: Devin Service Users

The backend authenticates against the Devin API using **two service user tokens** (Bearer),
one for enterprise-scoped endpoints and one for organization-scoped endpoints.
Personal API keys (`apk_user_*`) are deprecated; you must provision service users instead.

### Provisioning Service Users

#### 1. Enterprise Service User

Used for enterprise-scoped endpoints (`/v3/enterprise/...`): sessions metrics, billing, ACU limits, users, organizations, etc.

1. Obtain a temporary **admin API key** from your Devin Enterprise admin panel.
2. Call the Devin API to create an enterprise service user:

   ```bash
   curl -X POST https://api.devin.ai/v3beta1/enterprise/service-users \
     -H "Authorization: Bearer <ADMIN_API_KEY>" \
     -H "Content-Type: application/json" \
     -d '{
       "name": "finops-dashboard-enterprise",
       "permissions": [
         "ViewAccountMetrics",
         "ManageBilling",
         "ManageEnterpriseSettings"
       ]
     }'
   ```

3. Copy the `token` field from the JSON response.
4. Set the token as an environment variable:

   ```bash
   export DEVIN_ENTERPRISE_SERVICE_TOKEN="<token_from_step_3>"
   ```

#### 2. Organization Service User

Used for organization-scoped endpoints (`/v3/organizations/{org_id}/...`): sessions, knowledge, playbooks, secrets, schedules, etc.

1. Create an organization service user with the appropriate permissions for your org.
2. Copy the `token` and note your `org_id`.
3. Set both as environment variables:

   ```bash
   export DEVIN_ORG_SERVICE_TOKEN="<org_token>"
   export DEVIN_ORG_ID="<your_org_id>"
   ```

   Or add them to your `.envrc` / CI/CD secrets.

> **Important**: Never commit actual tokens to the repository. Use environment variables or a secrets manager.

### Environment Variables

| Variable                         | Required | Description                                                    |
|----------------------------------|----------|----------------------------------------------------------------|
| `DEVIN_ENTERPRISE_SERVICE_TOKEN` | Yes      | Bearer token for enterprise-scoped API endpoints               |
| `DEVIN_ORG_SERVICE_TOKEN`        | Yes      | Bearer token for organization-scoped API endpoints             |
| `DEVIN_ORG_ID`                   | No       | Organization ID (optional -- leave blank for multi-org auto-discovery) |
| `DEVIN_SERVICE_TOKEN`            | No       | Legacy alias for `DEVIN_ENTERPRISE_SERVICE_TOKEN` (deprecated) |
| `FINOPS_PRICE_PER_ACU`           | No       | Price per ACU (default: `0.05`)                                |
| `FINOPS_CURRENCY`                | No       | Currency code (default: `USD`)                                 |

## Running the Backend

```bash
cd backend

# First time: create .env from the template and fill in your tokens
./setup-env.sh
# Edit .env with your actual tokens

# Start the backend (spring-dotenv loads backend/.env automatically)
mvn spring-boot:run
```

> **Note**: The `.env` file must be in the `backend/` directory (where Maven runs). `spring-dotenv` loads it automatically from the working directory.

The backend starts on port **8080** by default.

## Running the Frontend

```bash
cd frontend
npm install
ng serve
```

The frontend starts on port **4200** and proxies API calls to the backend.

## Startup Validation

On startup the backend validates that all required tokens and configuration are present:

- `DEVIN_ENTERPRISE_SERVICE_TOKEN` must be set (falls back to `DEVIN_SERVICE_TOKEN` for backward compatibility).
- `DEVIN_ORG_SERVICE_TOKEN` must be set (if missing, organization-scoped endpoints are skipped gracefully).
- `DEVIN_ORG_ID` is optional -- if not set, the system uses multi-org auto-discovery via `list_organizations`.

If the enterprise token is missing, the application will fail to start with a descriptive error message.
A warning is logged if any token appears suspiciously short (fewer than 20 characters).

## Error Handling

- **401 Unauthorized**: Logged as `"Service user token is invalid or expired for endpoint {}"`. The request is **not** retried -- re-provision the service user token.
- **429 Too Many Requests / 5xx Server Errors**: Retried up to 3 times with exponential backoff (1s, 2s, 4s).

## Docker Deployment

### Quick Start (docker-compose)

```bash
# 1. Copy and fill in environment variables
cp backend/.env.example .env
# Edit .env with your tokens

# 2. Build and start both services
docker-compose up --build

# 3. Access the dashboard
open http://localhost:4200
```

### Building Images Individually

```bash
# Backend (from project root)
docker build -t devin-finops-backend -f backend/Dockerfile .

# Frontend
docker build -t devin-finops-frontend -f frontend/Dockerfile frontend/
```

### Docker Architecture

- **Backend** (`backend/Dockerfile`): Multi-stage build -- Maven 3.9 + JDK 17 for build, Eclipse Temurin JRE 17 Alpine for runtime. Runs as non-root user. Health check via Spring Boot Actuator.
- **Frontend** (`frontend/Dockerfile`): Multi-stage build -- Node 18 for Angular build, Nginx 1.25 Alpine for serving. Includes reverse proxy configuration for WebSocket traffic.
- **Nginx** (`frontend/nginx.conf`): Serves the Angular SPA, reverse-proxies `/ws/` to the backend for WebSocket connections, and `/actuator/` for health checks.

## AWS Deployment (ECS Fargate)

### Infrastructure

The `infra/cloudformation.yaml` template provisions:

- **VPC** with 2 public subnets across availability zones
- **ECS Cluster** (Fargate) with Container Insights
- **Application Load Balancer** with path-based routing (`/ws/*` and `/actuator/*` to backend, everything else to frontend)
- **ECR Repositories** for backend and frontend images (auto-cleanup keeps last 10 images)
- **Secrets Manager** for API tokens (injected into ECS tasks as environment variables)
- **CloudWatch Log Groups** with 30-day retention
- **IAM Roles** with least-privilege access

### Deploying

```bash
# 1. Configure AWS CLI
aws configure

# 2. Update secrets (first time only)
aws secretsmanager put-secret-value \
  --secret-id devin-finops/api-tokens \
  --secret-string '{"DEVIN_ENTERPRISE_SERVICE_TOKEN":"YOUR_TOKEN","DEVIN_ORG_SERVICE_TOKEN":"YOUR_TOKEN","DEVIN_ORG_ID":""}'

# 3. Deploy (builds images, pushes to ECR, deploys to ECS)
./infra/deploy-aws.sh

# Options:
./infra/deploy-aws.sh --region eu-west-1    # custom region
./infra/deploy-aws.sh --env staging         # custom environment name
./infra/deploy-aws.sh --skip-build          # skip Docker build
./infra/deploy-aws.sh --tag v1.2.3          # custom image tag
```

### CI/CD

The `.github/workflows/ci.yml` pipeline runs on every push/PR to `main`:

1. **Backend**: Compile + unit tests (Maven, JDK 17)
2. **Frontend**: Lint + production build (Node 18, Angular CLI)
3. **Docker**: Validate both Dockerfiles build successfully

## Configuration

All polling intervals are configurable via `application.properties` or environment variables:

| Property | Default | Description |
|---|---|---|
| `dashboard.polling-interval-seconds` | 5 | WebSocket polling interval |
| `dashboard.org-discovery-refresh-seconds` | 60 | Org auto-discovery refresh |
| `dashboard.org-discovery-timeout-seconds` | 10 | Org discovery HTTP timeout |
| `dashboard.scheduler-pool-size` | 2 | Scheduler thread pool size |
