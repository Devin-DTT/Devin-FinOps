"""
Data Adapter Module for Cognition API Integration
Fetches usage data from the Cognition API and writes to raw_usage_data.json
"""

import os
import json
import logging
import requests
from typing import List, Dict, Any, Optional


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


def fetch_cognition_data(
    base_url: str = "https://api.devin.ai/v2/enterprise",
    endpoint: str = "/consumption/daily",
    api_key: Optional[str] = None,
    page_size: int = 100
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
        requests.exceptions.RequestException: For connection/HTTP errors
        ValueError: For authentication errors
    """
    logger.info("Starting data fetch from Cognition API")
    logger.info(f"API Base URL: {base_url}")
    logger.info(f"Endpoint: {endpoint}")

    if api_key is None:
        api_key = os.getenv('DEVIN_ENTERPRISE_API_KEY')
        if not api_key:
            error_msg = "DEVIN_ENTERPRISE_API_KEY environment variable not set"
            logger.error(error_msg)
            raise ValueError(error_msg)

    full_url = f"{base_url.rstrip('/')}{endpoint}"
    logger.info(f"Full API URL: {full_url}")

    headers = {
        'Authorization': f'Bearer {api_key}',
        'Content-Type': 'application/json'
    }

    all_data = []
    skip = 0
    has_more = True
    page_count = 0

    try:
        while has_more:
            page_count += 1
            params = {
                'skip': skip,
                'limit': page_size
            }

            logger.info(f"Fetching page {page_count} (skip={skip}, limit={page_size})")

            try:
                response = requests.get(
                    full_url,
                    headers=headers,
                    params=params,
                    timeout=30
                )

                if response.status_code == 401:
                    error_msg = "Authentication failed (401): Invalid API key"
                    logger.error(error_msg)
                    raise ValueError(error_msg)

                if response.status_code == 403:
                    error_msg = "Authorization failed (403): Access denied"
                    logger.error(error_msg)
                    raise ValueError(error_msg)

                response.raise_for_status()

                response_data = response.json()

                page_data = response_data.get('data', [])
                all_data.extend(page_data)

                logger.info(f"Page {page_count} fetched: {len(page_data)} records")

                has_more = response_data.get('has_more', False)
                skip += page_size

            except requests.exceptions.Timeout:
                error_msg = f"Request timeout on page {page_count}"
                logger.error(error_msg)
                raise requests.exceptions.RequestException(error_msg)

            except requests.exceptions.ConnectionError as e:
                error_msg = f"Connection error on page {page_count}: {e}"
                logger.error(error_msg)
                raise

            except requests.exceptions.HTTPError as e:
                error_msg = f"HTTP error on page {page_count}: {e}"
                logger.error(error_msg)
                raise

        logger.info(f"Data fetch complete: {len(all_data)} total records from {page_count} pages")
        return all_data

    except Exception as e:
        logger.error(f"Failed to fetch data from Cognition API: {e}", exc_info=True)
        raise


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


def fetch_api_data(endpoint_list: Dict[str, str]) -> Dict[str, Dict[str, Any]]:
    """
    Fetch data from multiple API endpoints.
    
    Args:
        endpoint_list: Dictionary of {endpoint_name: endpoint_path}
    
    Returns:
        Dictionary of {endpoint_name: {status_code, timestamp, response}}
    """
    from datetime import datetime
    
    api_key = os.getenv('DEVIN_ENTERPRISE_API_KEY')
    if not api_key:
        error_msg = "DEVIN_ENTERPRISE_API_KEY environment variable not set"
        logger.error(error_msg)
        raise ValueError(error_msg)
    
    headers = {
        'Authorization': f'Bearer {api_key}',
        'Content-Type': 'application/json'
    }
    
    results = {}
    
    logger.info(f"Starting multi-endpoint data fetch for {len(endpoint_list)} endpoints")
    
    for endpoint_name, endpoint_path in endpoint_list.items():
        logger.info(f"Fetching endpoint: {endpoint_name} ({endpoint_path})")
        
        if endpoint_path.startswith('/audit-logs'):
            base_url = "https://api.devin.ai/v2"
        else:
            base_url = "https://api.devin.ai/v2/enterprise"
        
        full_url = f"{base_url.rstrip('/')}{endpoint_path}"
        
        try:
            response = requests.get(
                full_url,
                headers=headers,
                timeout=30
            )
            
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
                'response': response_data
            }
            
            logger.info(f"  └─ Status: {status_code}")
            
        except requests.exceptions.Timeout:
            logger.error(f"  └─ Timeout error for {endpoint_name}")
            results[endpoint_name] = {
                'endpoint_path': endpoint_path,
                'full_url': full_url,
                'status_code': 'TIMEOUT',
                'timestamp': datetime.now().isoformat(),
                'response': 'Request timeout'
            }
        except requests.exceptions.ConnectionError as e:
            logger.error(f"  └─ Connection error for {endpoint_name}: {e}")
            results[endpoint_name] = {
                'endpoint_path': endpoint_path,
                'full_url': full_url,
                'status_code': 'CONNECTION_ERROR',
                'timestamp': datetime.now().isoformat(),
                'response': str(e)
            }
        except Exception as e:
            logger.error(f"  └─ Error for {endpoint_name}: {e}")
            results[endpoint_name] = {
                'endpoint_path': endpoint_path,
                'full_url': full_url,
                'status_code': 'ERROR',
                'timestamp': datetime.now().isoformat(),
                'response': str(e)
            }
    
    logger.info(f"Multi-endpoint fetch complete: {len(results)} endpoints processed")
    return results


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


def main():
    """Main execution function for data adapter."""
    setup_logging()

    logger.info("=" * 60)
    logger.info("FinOps Data Adapter - Cognition API Integration")
    logger.info("=" * 60)

    try:
        data = fetch_cognition_data()

        write_raw_data(data)

        logger.info("=" * 60)
        logger.info("Data adapter completed successfully")
        logger.info("=" * 60)

        return True

    except Exception as e:
        logger.error(f"Data adapter failed: {e}", exc_info=True)
        return False


if __name__ == '__main__':
    success = main()
    exit(0 if success else 1)
