"""
Data Adapter Module for Cognition API Integration
Fetches usage data from the Cognition API and writes to raw_usage_data.json
"""

import os
import json
import logging
import requests
from typing import List, Dict, Any, Optional
from datetime import datetime

from error_handling import handle_api_errors, APIError, AuthenticationError
from validators import APIRequestParams, EndpointListInput


logger = logging.getLogger(__name__)


def setup_logging():
    """Configure centralized logging for the FinOps pipeline."""
    root_logger = logging.getLogger()
    root_logger.setLevel(logging.DEBUG)

    root_logger.handlers.clear()

    file_handler = logging.FileHandler('finops_pipeline.log', mode='a')
    file_handler.setLevel(logging.DEBUG)
    file_formatter = logging.Formatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    file_handler.setFormatter(file_formatter)

    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.ERROR)
    console_formatter = logging.Formatter(
        '%(asctime)s - %(levelname)s - %(message)s'
    )
    console_handler.setFormatter(console_formatter)

    root_logger.addHandler(file_handler)
    root_logger.addHandler(console_handler)


def _resolve_api_key(api_key: Optional[str] = None) -> str:
    """
    Resolve the API key from parameter or environment variable.

    Args:
        api_key: Explicit API key, or None to read from environment.

    Returns:
        The resolved API key string.

    Raises:
        ValueError: If no API key can be resolved.
    """
    if api_key:
        return api_key
    env_key = os.getenv('DEVIN_ENTERPRISE_API_KEY')
    if not env_key:
        error_msg = "DEVIN_ENTERPRISE_API_KEY environment variable not set"
        logger.error(error_msg)
        raise ValueError(error_msg)
    return env_key


def _build_headers(api_key: str) -> Dict[str, str]:
    """Return standard API request headers."""
    return {
        'Authorization': f'Bearer {api_key}',
        'Content-Type': 'application/json',
    }


@handle_api_errors(max_retries=3, base_delay=1.0)
def _fetch_page(
    url: str,
    headers: Dict[str, str],
    params: Dict[str, Any],
) -> Dict[str, Any]:
    """
    Fetch a single page from the API with retry logic via decorator.

    Raises:
        requests.exceptions.HTTPError: On non-2xx responses.
        requests.exceptions.Timeout: On request timeout.
        requests.exceptions.ConnectionError: On connection failure.
    """
    response = requests.get(url, headers=headers, params=params, timeout=30)
    response.raise_for_status()
    return response.json()


def fetch_cognition_data(
    base_url: str = "https://api.devin.ai/v2/enterprise",
    endpoint: str = "/consumption/daily",
    api_key: Optional[str] = None,
    page_size: int = 100,
    start_date: Optional[str] = None,
    end_date: Optional[str] = None
) -> List[Dict[str, Any]]:
    """
    Fetch usage data from Cognition API with pagination.

    Args:
        base_url: Base URL for the Cognition API
        endpoint: API endpoint path
        api_key: API key for authentication (from DEVIN_ENTERPRISE_API_KEY)
        page_size: Number of records per page

    Returns:
        List of usage data records

    Raises:
        ValidationError: If input parameters fail validation
        APIError: For API-related errors after retries
        ValueError: For authentication errors
    """
    validated = APIRequestParams(
        base_url=base_url,
        endpoint=endpoint,
        api_key=api_key,
        page_size=page_size,
        start_date=start_date,
        end_date=end_date,
    )

    logger.info("Starting data fetch from Cognition API")
    logger.info(f"API Base URL: {validated.base_url}")
    logger.info(f"Endpoint: {validated.endpoint}")

    resolved_key = _resolve_api_key(validated.api_key)

    full_url = f"{validated.base_url.rstrip('/')}{validated.endpoint}"
    logger.info(f"Full API URL: {full_url}")

    headers = _build_headers(resolved_key)

    all_data: List[Dict[str, Any]] = []
    skip = 0
    has_more = True
    page_count = 0

    while has_more:
        page_count += 1
        request_params: Dict[str, Any] = {
            'skip': skip,
            'limit': validated.page_size,
        }
        if validated.start_date:
            request_params['start_date'] = validated.start_date
        if validated.end_date:
            request_params['end_date'] = validated.end_date

        logger.info(f"Fetching page {page_count} (skip={skip}, limit={validated.page_size})")

        response_data = _fetch_page(full_url, headers, request_params)

        page_data = response_data.get('data', [])
        all_data.extend(page_data)

        logger.info(f"Page {page_count} fetched: {len(page_data)} records")

        has_more = response_data.get('has_more', False)
        skip += validated.page_size

    logger.info(f"Data fetch complete: {len(all_data)} total records from {page_count} pages")
    return all_data


def write_raw_data(data: List[Dict[str, Any]], output_file: str = 'raw_usage_data.json') -> None:
    """
    Write fetched data to JSON file.

    Args:
        data: List of usage data records
        output_file: Output file path
    """
    logger.info(f"Writing {len(data)} records to {output_file}")

    try:
        with open(output_file, 'w') as f:
            json.dump(data, f, indent=2)

        logger.info(f"Successfully wrote data to {output_file}")

    except Exception as e:
        logger.error(f"Failed to write data to {output_file}: {e}", exc_info=True)
        raise


@handle_api_errors(max_retries=3, base_delay=1.0)
def _fetch_single_endpoint(
    url: str,
    headers: Dict[str, str],
    params: Optional[Dict[str, str]] = None,
) -> requests.Response:
    """
    Fetch a single endpoint with retry logic via decorator.

    Returns the raw Response object so callers can inspect status_code.
    """
    response = requests.get(url, headers=headers, params=params, timeout=30)
    return response


def fetch_api_data(
    endpoint_list: Dict[str, str],
    params_by_endpoint: Optional[Dict[str, Dict[str, str]]] = None,
) -> Dict[str, Dict[str, Any]]:
    """
    Fetch data from multiple API endpoints.

    Args:
        endpoint_list: Dictionary of {endpoint_name: endpoint_path}
        params_by_endpoint: Optional dictionary of {endpoint_name: {param_name: param_value}}

    Returns:
        Dictionary of {endpoint_name: {status_code, timestamp, response}}
    """
    validated = EndpointListInput(
        endpoint_list=endpoint_list,
        params_by_endpoint=params_by_endpoint,
    )

    api_key = _resolve_api_key()
    headers = _build_headers(api_key)

    results: Dict[str, Dict[str, Any]] = {}

    logger.info(f"Starting multi-endpoint data fetch for {len(validated.endpoint_list)} endpoints")

    for endpoint_name, endpoint_path in validated.endpoint_list.items():
        logger.info(f"Fetching endpoint: {endpoint_name} ({endpoint_path})")

        if endpoint_path.startswith('/audit-logs'):
            base_url = "https://api.devin.ai/v2"
        else:
            base_url = "https://api.devin.ai/v2/enterprise"

        full_url = f"{base_url.rstrip('/')}{endpoint_path}"

        ep_params = None
        if validated.params_by_endpoint and endpoint_name in validated.params_by_endpoint:
            ep_params = validated.params_by_endpoint[endpoint_name]
            logger.info(f"    - Using params: {ep_params}")

        try:
            response = _fetch_single_endpoint(full_url, headers, ep_params)

            timestamp = datetime.now().isoformat()
            status_code = response.status_code

            if status_code == 200:
                try:
                    response_data = response.json()
                except json.JSONDecodeError:
                    response_data = response.text
            else:
                response_data = response.text

            results[endpoint_name] = {
                'endpoint_path': endpoint_path,
                'full_url': full_url,
                'status_code': status_code,
                'timestamp': timestamp,
                'response': response_data,
            }

            logger.info(f"    - Status: {status_code}")

        except (APIError, AuthenticationError) as exc:
            logger.error(f"    - API error for {endpoint_name}: {exc}")
            results[endpoint_name] = {
                'endpoint_path': endpoint_path,
                'full_url': full_url,
                'status_code': exc.status_code or 'ERROR',
                'timestamp': datetime.now().isoformat(),
                'response': str(exc),
            }
        except Exception as exc:
            logger.error(f"    - Error for {endpoint_name}: {exc}")
            results[endpoint_name] = {
                'endpoint_path': endpoint_path,
                'full_url': full_url,
                'status_code': 'ERROR',
                'timestamp': datetime.now().isoformat(),
                'response': str(exc),
            }

    logger.info(f"Multi-endpoint fetch complete: {len(results)} endpoints processed")
    return results


@handle_api_errors(max_retries=3, base_delay=1.0)
def _fetch_user_org(url: str, headers: Dict[str, str]) -> requests.Response:
    """Fetch organization data for a single user with retry logic."""
    response = requests.get(url, headers=headers, timeout=30)
    return response


def fetch_user_organization_mappings(user_ids: List[str]) -> Dict[str, Any]:
    """
    Fetch organization mappings for multiple users iteratively.

    Args:
        user_ids: List of user IDs to fetch organization mappings for

    Returns:
        Dictionary mapping user_id to organization data
        Format: {user_id: {'organization_id': str, 'organization_name': str, 'status': int}}
    """
    logger.info(f"Starting iterative organization mapping fetch for {len(user_ids)} users")

    api_key = _resolve_api_key()
    headers = _build_headers(api_key)

    user_org_mappings: Dict[str, Any] = {}
    base_url = "https://api.devin.ai/v2/enterprise"

    for user_id in user_ids:
        endpoint_path = f"/members/{user_id}/organizations"
        full_url = f"{base_url}{endpoint_path}"

        logger.info(f"Fetching organization mapping for user: {user_id}")

        try:
            response = _fetch_user_org(full_url, headers)

            status_code = response.status_code

            if status_code == 200:
                try:
                    org_data = response.json()

                    if isinstance(org_data, dict):
                        if 'organizations' in org_data and org_data['organizations']:
                            first_org = org_data['organizations'][0]
                            user_org_mappings[user_id] = {
                                'organization_id': first_org.get('id', 'Unknown'),
                                'organization_name': first_org.get('name', 'Unknown'),
                                'status': status_code,
                            }
                        elif 'id' in org_data:
                            user_org_mappings[user_id] = {
                                'organization_id': org_data.get('id', 'Unknown'),
                                'organization_name': org_data.get('name', 'Unknown'),
                                'status': status_code,
                            }
                        else:
                            user_org_mappings[user_id] = {
                                'organization_id': 'Unknown',
                                'organization_name': 'Unknown',
                                'status': status_code,
                                'raw_response': org_data,
                            }
                    elif isinstance(org_data, list) and org_data:
                        first_org = org_data[0]
                        user_org_mappings[user_id] = {
                            'organization_id': first_org.get('id', 'Unknown'),
                            'organization_name': first_org.get('name', 'Unknown'),
                            'status': status_code,
                        }
                    else:
                        user_org_mappings[user_id] = {
                            'organization_id': 'Unknown',
                            'organization_name': 'Unknown',
                            'status': status_code,
                        }

                    logger.info(f"  + User {user_id}: {user_org_mappings[user_id].get('organization_name', 'Unknown')}")

                except json.JSONDecodeError as e:
                    logger.warning(f"  - User {user_id}: Failed to parse JSON response - {e}")
                    user_org_mappings[user_id] = {
                        'organization_id': 'Unmapped',
                        'organization_name': 'Unmapped',
                        'status': status_code,
                        'error': 'JSON decode error',
                    }

            elif status_code == 404:
                logger.warning(f"  - User {user_id}: Not found (404)")
                user_org_mappings[user_id] = {
                    'organization_id': 'Unmapped',
                    'organization_name': 'Unmapped',
                    'status': status_code,
                    'error': 'User not found',
                }

            else:
                logger.warning(f"  - User {user_id}: HTTP {status_code}")
                user_org_mappings[user_id] = {
                    'organization_id': 'Unmapped',
                    'organization_name': 'Unmapped',
                    'status': status_code,
                    'error': f'HTTP {status_code}',
                }

        except (APIError, AuthenticationError) as exc:
            logger.error(f"  - User {user_id}: API error - {exc}")
            user_org_mappings[user_id] = {
                'organization_id': 'Unmapped',
                'organization_name': 'Unmapped',
                'status': exc.status_code or 'ERROR',
                'error': str(exc),
            }

        except Exception as exc:
            logger.error(f"  - User {user_id}: Unexpected error - {exc}")
            user_org_mappings[user_id] = {
                'organization_id': 'Unmapped',
                'organization_name': 'Unmapped',
                'status': 'ERROR',
                'error': str(exc),
            }

    successful_mappings = sum(1 for m in user_org_mappings.values() if m['status'] == 200)
    logger.info(f"Organization mapping fetch complete: {successful_mappings}/{len(user_ids)} successful")

    return user_org_mappings


def save_raw_data(data: Dict[str, Any], output_file: str = 'all_raw_api_data.json') -> None:
    """
    Save API results dictionary to JSON file.
    
    Args:
        data: Dictionary of API results
        output_file: Output file path (default: all_raw_api_data.json)
    """
    logger.info(f"Saving API results to {output_file}")
    
    try:
        with open(output_file, 'w') as f:
            json.dump(data, f, indent=2)
        
        logger.info(f"Successfully saved results to {output_file}")
    
    except Exception as e:
        logger.error(f"Failed to save results to {output_file}: {e}", exc_info=True)
        raise


def main(start_date: Optional[str] = None, end_date: Optional[str] = None, persist: bool = False) -> Optional[List[Dict[str, Any]]]:
    """
    Main execution function for data adapter.

    Args:
        start_date: Start date filter (YYYY-MM-DD)
        end_date: End date filter (YYYY-MM-DD)
        persist: If True, write raw data to raw_usage_data.json for debugging

    Returns:
        List of fetched usage data records, or None on failure
    """
    from datetime import timedelta

    setup_logging()

    logger.info("=" * 60)
    logger.info("FinOps Data Adapter - Cognition API Integration")
    logger.info("=" * 60)
    
    if end_date and not start_date:
        end_dt = datetime.strptime(end_date, '%Y-%m-%d').date()
        start_dt = end_dt - timedelta(days=365)
        start_date = start_dt.strftime('%Y-%m-%d')
        logger.info(f"Auto-calculated start_date (12 months before end_date): {start_date}")
    
    if not start_date and not end_date:
        end_dt = datetime.now().date()
        start_dt = end_dt - timedelta(days=365)
        start_date = start_dt.strftime('%Y-%m-%d')
        end_date = end_dt.strftime('%Y-%m-%d')
        logger.info(f"Auto-calculated date range (last 12 months): {start_date} to {end_date}")
    
    if start_date or end_date:
        logger.info(f"Date range filter: {start_date} to {end_date}")

    try:
        data = fetch_cognition_data(start_date=start_date, end_date=end_date)

        if persist:
            write_raw_data(data)

        logger.info("=" * 60)
        logger.info("Data adapter completed successfully")
        logger.info("=" * 60)

        return data

    except Exception as e:
        logger.error(f"Data adapter failed: {e}", exc_info=True)
        return None


if __name__ == '__main__':
    success = main()
    exit(0 if success else 1)
