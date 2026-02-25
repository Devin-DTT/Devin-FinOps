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

## Authentication: Devin Service User

The backend authenticates against the Devin API using a **service user token** (Bearer).
Personal API keys (`apk_user_*`) are deprecated; you must provision a service user instead.

### Provisioning a Service User

1. Obtain a temporary **admin API key** from your Devin Enterprise admin panel.
2. Call the Devin API to create a service user with the required permissions:

   ```bash
   curl -X POST https://api.devin.ai/v3beta1/enterprise/service-users \
     -H "Authorization: Bearer <ADMIN_API_KEY>" \
     -H "Content-Type: application/json" \
     -d '{
       "name": "finops-dashboard",
       "permissions": [
         "ViewAccountMetrics",
         "ManageBilling",
         "ManageEnterpriseSettings"
       ]
     }'
   ```

3. Copy the `token` field from the JSON response. This is your service user Bearer token.
4. Set the token as an environment variable:

   ```bash
   export DEVIN_SERVICE_TOKEN="<token_from_step_3>"
   ```

   Or add it to your `.envrc` / CI/CD secrets.

> **Important**: Never commit the actual token to the repository. Use environment variables or a secrets manager.

### Environment Variables

| Variable              | Required | Description                                      |
|-----------------------|----------|--------------------------------------------------|
| `DEVIN_SERVICE_TOKEN` | Yes      | Bearer token from the provisioned service user   |
| `FINOPS_PRICE_PER_ACU`| No      | Price per ACU (default: `0.05`)                  |
| `FINOPS_CURRENCY`     | No      | Currency code (default: `USD`)                   |

## Running the Backend

```bash
cd backend
export DEVIN_SERVICE_TOKEN="your_service_user_token_here"
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

On startup the backend validates that `DEVIN_SERVICE_TOKEN` is present and non-empty.
If the token is missing, the application will fail to start with a descriptive error message.
A warning is logged if the token appears suspiciously short (fewer than 20 characters).

## Error Handling

- **401 Unauthorized**: Logged as `"Service user token is invalid or expired for endpoint {}"`. The request is **not** retried — re-provision the service user token.
- **429 Too Many Requests / 5xx Server Errors**: Retried up to 3 times with exponential backoff (1s, 2s, 4s).
