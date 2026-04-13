# Devin FinOps Dashboard

Full-stack FinOps dashboard for monitoring Devin Enterprise usage, costs, and productivity metrics.

## Architecture

- **Microservices**: 7 Spring Boot 3 (Java 17) services with WebFlux, WebSocket streaming, and reactive API clients.
- **Frontend**: Angular 17 single-page application with real-time dashboard.
- **Cache & Pub/Sub**: Redis 7 for shared state and inter-service messaging.

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+ and Angular CLI (for the frontend)
- Docker & Docker Compose (for running the full stack)
- A **Devin service user token** (see below)

## Authentication: Devin Service Users

The microservices authenticate against the Devin API using **two service user tokens** (Bearer),
one for enterprise-scoped endpoints and one for organization-scoped endpoints.

### Provisioning Service Users

#### 1. Enterprise Service User

Used for enterprise-scoped endpoints (`/v3/enterprise/...`): sessions metrics, billing, ACU limits, users, organizations, etc.

1. Log in to https://app.devin.ai with an admin account.
2. Navigate to **Enterprise Settings -> Service Users** and create a new service user.
   Alternatively, call the API:

   ```bash
   curl -X POST https://api.devin.ai/v3beta1/enterprise/service-users \
     -H "Authorization: Bearer <ADMIN_BEARER_TOKEN>" \
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

| Variable                              | Required | Description                                                    |
|---------------------------------------|----------|----------------------------------------------------------------|
| `DEVIN_ENTERPRISE_SERVICE_TOKEN`      | Yes      | Bearer token for enterprise-scoped API endpoints               |
| `DEVIN_ORG_SERVICE_TOKEN`             | No       | Bearer token for organization-scoped API endpoints (skipped if missing) |
| `DEVIN_ORG_ID`                        | No       | Organization ID (optional -- leave blank for multi-org auto-discovery) |
| `FINOPS_PRICE_PER_ACU`                | No       | Price per ACU (default: `0.05`)                                |
| `FINOPS_CURRENCY`                     | No       | Currency code (default: `USD`)                                 |

## Running the Application

### Docker Compose (recommended)

```bash
# 1. Copy and fill in your service user tokens
cp .env.example .env
# Edit .env with your actual tokens

# 2. Build and start all services
docker-compose up --build

# 3. Access the dashboard
open http://localhost:4200
```

### Automatic Raw Endpoint Data Dump

When running with `docker-compose up`, the file `dump/raw-endpoint-data.json` is automatically generated every 30 seconds with the raw data from all endpoints cached in Redis. No manual scripts or `curl` commands are needed.

The dump behavior is configurable via environment variables:

| Variable | Default | Description |
|---|---|---|
| `COLLECTOR_DUMP_ENABLED` | `true` | Enable/disable automatic dump |
| `COLLECTOR_DUMP_INTERVAL_SECONDS` | `30` | Interval between dump writes (seconds) |
| `COLLECTOR_DUMP_FILE_PATH` | `/app/dump/raw-endpoint-data.json` | File path inside the container |

To disable the automatic dump:

```bash
COLLECTOR_DUMP_ENABLED=false docker-compose up --build
```

> **Note**: `raw-endpoint-data.json` is already in `.gitignore` and will not be committed to the repository.

### Services Architecture

| Service | Port | Description |
|---|---|---|
| redis | 6379 | Shared cache and Pub/Sub |
| api-gateway | 8080 | Spring Cloud Gateway - routing, CORS |
| data-collector | 8081 | Polls Devin API, publishes to Redis |
| websocket-service | 8082 | Subscribes to Redis, broadcasts via WebSocket |
| sessions-service | 8083 | REST API for sessions domain |
| billing-service | 8084 | REST API for billing domain |
| metrics-service | 8085 | REST API for metrics domain |
| admin-service | 8086 | REST API for admin domain |
| frontend | 4200 | Angular SPA served by Nginx |

## Running the Frontend

```bash
cd frontend
npm install
ng serve
```

The frontend starts on port **4200** and proxies API calls to the API Gateway.

## Startup Validation

On startup the data-collector validates that all required tokens and configuration are present:

- `DEVIN_ENTERPRISE_SERVICE_TOKEN` must be set.
- `DEVIN_ORG_SERVICE_TOKEN` is optional (if not set, organization-scoped endpoints are skipped gracefully).
- `DEVIN_ORG_ID` is optional -- if not set, the system uses multi-org auto-discovery via `list_organizations`.

If the enterprise service user token is missing, the application will fail to start with a descriptive error message.
A warning is logged if any token appears suspiciously short (fewer than 20 characters).

## Error Handling

- **401 Unauthorized**: Logged as `"Service user token invalid/expired for {endpoint} (HTTP 401)"`. The request is **not** retried -- re-provision the service user.
- **429 Too Many Requests / 5xx Server Errors**: Retried up to 3 times with exponential backoff (1s, 2s, 4s).

## Docker Deployment

### Quick Start (docker-compose)

```bash
# 1. Copy and fill in your service user tokens
cp .env.example .env
# Edit .env with your service user tokens

# 2. Build and start all services
docker-compose up --build

# 3. Access the dashboard
open http://localhost:4200
```

### Building Images Individually

```bash
# API Gateway
docker build -t devin-finops-api-gateway -f services/api-gateway/Dockerfile .

# Data Collector
docker build -t devin-finops-data-collector -f services/data-collector/Dockerfile .

# WebSocket Service
docker build -t devin-finops-websocket-service -f services/websocket-service/Dockerfile .

# Sessions Service
docker build -t devin-finops-sessions-service -f services/sessions-service/Dockerfile .

# Billing Service
docker build -t devin-finops-billing-service -f services/billing-service/Dockerfile .

# Metrics Service
docker build -t devin-finops-metrics-service -f services/metrics-service/Dockerfile .

# Admin Service
docker build -t devin-finops-admin-service -f services/admin-service/Dockerfile .

# Frontend
docker build -t devin-finops-frontend -f frontend/Dockerfile frontend/
```

### Docker Architecture

- **API Gateway** (`services/api-gateway/Dockerfile`): Spring Cloud Gateway for routing, CORS, and load balancing across microservices.
- **Data Collector** (`services/data-collector/Dockerfile`): Polls Devin API endpoints and publishes results to Redis.
- **WebSocket Service** (`services/websocket-service/Dockerfile`): Subscribes to Redis Pub/Sub and broadcasts data to connected WebSocket clients.
- **Sessions / Billing / Metrics / Admin Services**: Domain-specific REST APIs backed by Redis cache.
- **Frontend** (`frontend/Dockerfile`): Multi-stage build -- Node 18 for Angular build, Nginx 1.25 Alpine for serving. Includes reverse proxy configuration for WebSocket traffic.
- **Redis**: Shared cache and Pub/Sub messaging backbone.

## AWS Deployment (ECS Fargate)

### Infrastructure

The `infra/cloudformation.yaml` template provisions:

- **VPC** with 2 public subnets across availability zones
- **ECS Cluster** (Fargate) with Container Insights
- **Application Load Balancer** with path-based routing
- **ECR Repositories** for microservice and frontend images (auto-cleanup keeps last 10 images)
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
  --secret-string '{"DEVIN_ENTERPRISE_SERVICE_TOKEN":"YOUR_SERVICE_TOKEN","DEVIN_ORG_SERVICE_TOKEN":"YOUR_SERVICE_TOKEN","DEVIN_ORG_ID":""}'

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

1. **Microservices**: Compile + unit tests (Maven, JDK 17)
2. **Frontend**: Lint + production build (Node 18, Angular CLI)
3. **Docker**: Validate all Dockerfiles build successfully

## Configuration

All polling intervals are configurable via `application.properties` or environment variables:

| Property | Default | Description |
|---|---|---|
| `dashboard.polling-interval-seconds` | 5 | WebSocket polling interval |
| `dashboard.org-discovery-refresh-seconds` | 60 | Org auto-discovery refresh |
| `dashboard.org-discovery-timeout-seconds` | 10 | Org discovery HTTP timeout |
| `dashboard.scheduler-pool-size` | 2 | Scheduler thread pool size |
