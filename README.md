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
| `DEVIN_ORG_ID`                   | Yes      | Organization ID for organization-scoped API endpoints          |
| `DEVIN_SERVICE_TOKEN`            | No       | Legacy alias for `DEVIN_ENTERPRISE_SERVICE_TOKEN` (deprecated) |
| `FINOPS_PRICE_PER_ACU`           | No       | Price per ACU (default: `0.05`)                                |
| `FINOPS_CURRENCY`                | No       | Currency code (default: `USD`)                                 |

## Running the Backend

```bash
cd backend
export DEVIN_ENTERPRISE_SERVICE_TOKEN="<enterprise_service_user_token>"
export DEVIN_ORG_SERVICE_TOKEN="<org_service_user_token>"
export DEVIN_ORG_ID="<your_org_id>"
mvn spring-boot:run
```

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
- `DEVIN_ORG_SERVICE_TOKEN` must be set.
- `DEVIN_ORG_ID` must be set.

If any required value is missing, the application will fail to start with a descriptive error message.
A warning is logged if any token appears suspiciously short (fewer than 20 characters).

## Error Handling

- **401 Unauthorized**: Logged as `"Service user token is invalid or expired for endpoint {}"`. The request is **not** retried — re-provision the service user token.
- **429 Too Many Requests / 5xx Server Errors**: Retried up to 3 times with exponential backoff (1s, 2s, 4s).
