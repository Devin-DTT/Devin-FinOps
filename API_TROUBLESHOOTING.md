# API Connection Troubleshooting Guide

## Current Issue: 403 Authorization Error

### Error Details
When attempting to connect to the Cognition API Daily Consumption endpoint, the following error occurs:

```
ERROR - Authorization failed (403): Access denied
API Endpoint: https://api.devin.ai/v2/enterprise/consumption/daily
```

### What the Error Means
- **403 Forbidden**: The API key is valid (authentication passed), but it lacks the necessary permissions to access this specific endpoint
- **NOT a 401**: This means the API key itself is recognized by the system
- The DEVIN_ENTERPRISE_API_KEY environment variable is properly set (96 characters)

### Step-by-Step Fix Instructions

#### Step 1: Verify API Key Permissions
The API key needs specific scopes/permissions to access consumption data. Contact your Devin Enterprise administrator to:

1. Log into the Devin Enterprise admin console
2. Navigate to **Settings** → **API Keys**
3. Find the API key being used (or create a new one)
4. Ensure the following permissions are enabled:
   - ✅ **Consumption Data Read** (or similar scope)
   - ✅ **Enterprise API Access**
   - ✅ **Daily Consumption Endpoint Access**

#### Step 2: Verify Organization Access
The API key must be associated with the correct organization:

1. Confirm the API key is tied to your organization ID
2. If using `org_ids` query parameter, verify the IDs are correct
3. Check that your account has admin or billing access rights

#### Step 3: Check API Key Expiration
1. Verify the API key hasn't expired
2. Check if there are any IP restrictions configured
3. Ensure the key hasn't been revoked

#### Step 4: Test with Minimal Request
Try a direct curl command to isolate the issue:

```bash
curl -X GET "https://api.devin.ai/v2/enterprise/consumption/daily" \
  -H "Authorization: Bearer $DEVIN_ENTERPRISE_API_KEY" \
  -v
```

Look for specific error messages in the response body.

#### Step 5: Verify Billing Cycle
The endpoint returns data for the "current billing cycle". Ensure:
- Your organization has an active billing cycle
- The billing period has started (not in pre-activation state)

#### Step 6: Contact Support
If the above steps don't resolve the issue, contact Devin support with:
- The full error message from `finops_pipeline.log`
- Your organization ID
- The API key ID (not the full key)
- Timestamp of the failed request

### Alternative: Use Existing Data
The repository includes a fallback mechanism:
- If the API fetch fails, the pipeline will use existing `raw_usage_data.json`
- This allows testing the metrics calculation without API access
- To test: `python generate_report.py` will proceed with cached data

### Verification After Fix
Once permissions are updated, verify the connection:

```bash
cd ~/repos/Devin-FinOps
python data_adapter.py
```

Expected output:
```
INFO - Data fetch complete: X total records from Y pages
INFO - Successfully wrote data to raw_usage_data.json
INFO - Data adapter completed successfully
```

### Additional Resources
- API Documentation: https://docs.devin.ai/enterprise-api/consumption/daily-consumption
- Enterprise API Overview: https://docs.devin.ai/enterprise-api/overview
- Support: support@cognition.ai
