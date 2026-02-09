"""
Data Transformer Module for FinOps Report Generation.
Handles data transformation, summary generation, and CSV creation.
Extracted from generate_report.py for separation of responsibilities.
"""

import json
import logging
import pandas as pd
from typing import Dict, Any, List
from config import MetricsConfig

logger = logging.getLogger(__name__)


def transform_raw_data(raw_sessions: List[Dict], start_date: str = '2025-01-01', end_date: str = '2025-01-31') -> Dict[str, Any]:
    """
    Transform raw usage data into the format expected by MetricsCalculator.
    
    Args:
        raw_sessions: List of session dictionaries from raw_usage_data.json
        start_date: Start date for reporting period (YYYY-MM-DD)
        end_date: End date for reporting period (YYYY-MM-DD)
    
    Returns:
        Dictionary with 'sessions', 'user_details', and 'reporting_period' keys
    """
    logger.info(f"Transforming {len(raw_sessions)} raw sessions...")
    
    transformed_sessions = []
    user_departments = {}
    timestamps = []
    
    for session in raw_sessions:
        user_id = session.get('user_id', 'unknown')
        user_email = f"{user_id}@deloitte.com"
        business_unit = session.get('business_unit', 'Unknown')
        
        user_departments[user_email] = business_unit
        
        if session.get('timestamp'):
            timestamps.append(session['timestamp'])
        
        acu_consumed = session.get('acu_consumed', 0)
        duration_minutes = max(1, int(acu_consumed / 5))
        
        transformed_session = {
            'session_id': session.get('session_id', 'unknown'),
            'user_email': user_email,
            'duration_minutes': duration_minutes,
            'acus_consumed': int(acu_consumed),
            'task_type': session.get('task_type', 'unknown'),
            'status': session.get('session_outcome', 'unknown')
        }
        transformed_sessions.append(transformed_session)
    
    user_details = [
        {
            'user_email': email,
            'department': dept,
            'role': 'User'
        }
        for email, dept in user_departments.items()
    ]
    
    timestamps.sort()
    period_start = start_date
    period_end = end_date
    
    reporting_period = {
        'start_date': period_start,
        'end_date': period_end,
        'month': f"{period_start} to {period_end}"
    }
    
    transformed_data = {
        'organization': 'Deloitte',
        'reporting_period': reporting_period,
        'sessions': transformed_sessions,
        'user_details': user_details
    }
    
    logger.info(f"Transformation complete: {len(transformed_sessions)} sessions, {len(user_details)} unique users")
    return transformed_data


def create_summary_csv(raw_data: Dict[str, Dict[str, Any]], output_file: str = 'api_health_report.csv') -> None:
    """
    Create a CSV summary of API health check results.
    
    Args:
        raw_data: Dictionary of API results from fetch_api_data()
        output_file: Output CSV filename
    """
    logger.info(f"Creating API health report CSV with {len(raw_data)} endpoints...")
    
    rows = []
    for endpoint_name, endpoint_data in raw_data.items():
        row = {
            'endpoint_name': endpoint_name,
            'full_url': endpoint_data.get('full_url', ''),
            'status_code': endpoint_data.get('status_code', ''),
            'timestamp': endpoint_data.get('timestamp', '')
        }
        rows.append(row)
    
    df = pd.DataFrame(rows)
    df.to_csv(output_file, index=False)
    
    logger.info(f"API health report saved to {output_file}")
    logger.info(f"  - Total endpoints: {len(rows)}")
    logger.info("  - Columns: endpoint_name, full_url, status_code, timestamp")


def generate_business_summary(consumption_data: Dict[str, Any], config: MetricsConfig, all_api_data: Dict[str, Dict[str, Any]] = None) -> Dict[str, Any]:
    """
    Display final business summary with ASCII formatting using print().
    
    Args:
        consumption_data: Dictionary containing metrics and reporting_period
        config: MetricsConfig object with pricing information
        all_api_data: Dictionary of API results from fetch_api_data()
    
    Returns:
        Dictionary containing summary metrics for HTML dashboard generation
    """
    metrics = consumption_data.get('metrics', {})
    reporting_period = consumption_data.get('reporting_period', {})
    
    total_sessions = 0
    total_acus = 0
    total_cost = 0
    unique_users = 0
    
    if all_api_data:
        consumption_endpoint = all_api_data.get('consumption_daily', {})
        status_code = consumption_endpoint.get('status_code')
        
        if status_code == 200:
            try:
                response_data = consumption_endpoint.get('response', {})
                if isinstance(response_data, str):
                    response_data = json.loads(response_data)
                
                if isinstance(response_data, dict):
                    total_acus = response_data.get('total_acus', 0)
                    consumption_by_date = response_data.get('consumption_by_date', {})
                    consumption_by_user = response_data.get('consumption_by_user', {})
                    
                    total_sessions = len(consumption_by_date)
                    unique_users = len(consumption_by_user)
                    total_cost = total_acus * config.price_per_acu
                    
                    logger.info(f"Extracted metrics from consumption_daily: {total_acus} ACUs, {total_sessions} records, {unique_users} users")
            except (json.JSONDecodeError, AttributeError, TypeError) as e:
                logger.warning(f"Failed to extract consumption_daily metrics: {e}")
    
    total_prs_merged = 0
    if all_api_data:
        metrics_prs_endpoint = all_api_data.get('metrics_prs', {})
        if metrics_prs_endpoint.get('status_code') == 200:
            try:
                prs_response = metrics_prs_endpoint.get('response', {})
                if isinstance(prs_response, str):
                    prs_response = json.loads(prs_response)
                if isinstance(prs_response, dict):
                    total_prs_merged = prs_response.get('prs_merged', 0)
                    logger.info(f"Extracted PR metrics: {total_prs_merged} PRs merged")
            except (json.JSONDecodeError, AttributeError, TypeError) as e:
                logger.warning(f"Failed to extract metrics_prs data: {e}")
    
    total_sessions_count = 0
    if all_api_data:
        metrics_sessions_endpoint = all_api_data.get('metrics_sessions', {})
        if metrics_sessions_endpoint.get('status_code') == 200:
            try:
                sessions_response = metrics_sessions_endpoint.get('response', {})
                if isinstance(sessions_response, str):
                    sessions_response = json.loads(sessions_response)
                if isinstance(sessions_response, dict):
                    total_sessions_count = sessions_response.get('sessions_count', 0)
                    logger.info(f"Extracted session metrics: {total_sessions_count} sessions")
            except (json.JSONDecodeError, AttributeError, TypeError) as e:
                logger.warning(f"Failed to extract metrics_sessions data: {e}")
    
    if total_acus == 0:
        total_sessions = metrics.get('06_total_sessions', 0)
        total_acus = metrics.get('02_total_acus', 0)
        total_cost = metrics.get('01_total_monthly_cost', 0)
        unique_users = metrics.get('12_unique_users', 0)
    
    start_date = reporting_period.get('start_date', 'N/A')
    end_date = reporting_period.get('end_date', 'N/A')
    
    num_days = 31
    
    average_acus_per_day = total_acus / num_days if num_days > 0 else 0
    
    cost_per_pr = total_cost / total_prs_merged if total_prs_merged > 0 else 0
    
    acus_per_session = total_acus / total_sessions_count if total_sessions_count > 0 else 0
    acus_per_developer = total_acus / unique_users if unique_users > 0 else 0
    
    acus_per_pr_merged = total_acus / total_prs_merged if total_prs_merged > 0 else 0
    prs_per_acu = 1 / acus_per_pr_merged if acus_per_pr_merged > 0 else 0
    
    total_acus_previous_month = 0
    acu_metrics_by_plan = "N/A"
    acus_per_line_of_code = "N/A"
    acus_per_outcome = "N/A"
    roi_per_session = "N/A"
    percent_sessions_with_outcome = "N/A"
    acus_per_productive_hour = "N/A"
    percent_retried_sessions = "N/A"
    pr_success_rate = "N/A"
    percent_sessions_without_outcome = "N/A"
    wasted_acus = "N/A"
    idle_sessions = "N/A"
    percent_redundant_tasks = "N/A"
    finops_savings_percent = "N/A"
    acu_efficiency_index = "N/A"
    cost_velocity_ratio = "N/A"
    waste_to_outcome_ratio = "N/A"
    accumulated_finops_savings = "N/A"
    
    print("\n")
    print("=" * 70)
    print("*" * 70)
    print("**" + " " * 66 + "**")
    print("**" + " " * 20 + "BUSINESS SUMMARY" + " " * 30 + "**")
    print("**" + " " * 66 + "**")
    print("*" * 70)
    print("=" * 70)
    print("\n")
    print("KEY METRICS FROM REAL DATA:")
    print("-" * 70)
    print(f"  Total_Daily_Consumption_Records:  {total_sessions}")
    print(f"  Average_ACUs_Per_Day:              {average_acus_per_day:.2f}")
    print("-" * 70)
    print("\n")
    print("ADDITIONAL STATISTICS:")
    print("-" * 70)
    print(f"  Total ACUs Consumed:               {total_acus}")
    print(f"  Total Cost:                        {total_cost:.2f} {config.currency}")
    print(f"  Total PRs Merged:                  {total_prs_merged}")
    print(f"  Total Sessions Count:              {total_sessions_count}")
    print(f"  Cost Per PR Merged:                {cost_per_pr:.2f} {config.currency if total_prs_merged > 0 else 'N/A'}")
    print(f"  Unique Users:                      {unique_users}")
    print(f"  Reporting Period:                  {start_date} to {end_date}")
    print(f"  Number of Days:                    {num_days}")
    print("-" * 70)
    print("\n")
    print("=" * 70)
    print("*" * 70)
    print("**" + " " * 18 + "REPORT COMPLETED SUCCESSFULLY" + " " * 19 + "**")
    print("*" * 70)
    print("=" * 70)
    print("\n")
    
    return {
        'total_acus': total_acus,
        'total_cost': total_cost,
        'total_cost_previous_month': 0,
        'total_prs_merged': total_prs_merged,
        'total_sessions_count': total_sessions_count,
        'cost_per_pr': cost_per_pr,
        'unique_users': unique_users,
        'start_date': start_date,
        'end_date': end_date,
        'num_days': num_days,
        'average_acus_per_day': average_acus_per_day,
        'acus_per_session': acus_per_session,
        'acus_per_developer': acus_per_developer,
        'currency': config.currency,
        'acus_per_pr_merged': acus_per_pr_merged,
        'prs_per_acu': prs_per_acu,
        'total_acus_previous_month': total_acus_previous_month,
        'acu_metrics_by_plan': acu_metrics_by_plan,
        'acus_per_line_of_code': acus_per_line_of_code,
        'acus_per_outcome': acus_per_outcome,
        'roi_per_session': roi_per_session,
        'percent_sessions_with_outcome': percent_sessions_with_outcome,
        'acus_per_productive_hour': acus_per_productive_hour,
        'percent_retried_sessions': percent_retried_sessions,
        'pr_success_rate': pr_success_rate,
        'percent_sessions_without_outcome': percent_sessions_without_outcome,
        'wasted_acus': wasted_acus,
        'idle_sessions': idle_sessions,
        'percent_redundant_tasks': percent_redundant_tasks,
        'finops_savings_percent': finops_savings_percent,
        'acu_efficiency_index': acu_efficiency_index,
        'cost_velocity_ratio': cost_velocity_ratio,
        'waste_to_outcome_ratio': waste_to_outcome_ratio,
        'accumulated_finops_savings': accumulated_finops_savings
    }


def generate_consumption_summary(raw_data: Dict[str, Dict[str, Any]], calculated_metrics: Dict[str, Any] = None) -> None:
    """
    Generate and print a consumption summary from API endpoint data with fallback to calculated metrics.
    
    Args:
        raw_data: Dictionary of API results from fetch_api_data()
        calculated_metrics: Dictionary containing calculated metrics as fallback when API fails
    """
    logger.info("Generating consumption summary from API endpoint data...")
    
    consumption_endpoint = raw_data.get('consumption_daily', {})
    status_code = consumption_endpoint.get('status_code')
    
    summary = {
        'Status': 'No data available',
        'Total_Daily_Consumption_Records': 0,
        'Average_ACUs_Per_Day': 0.0
    }
    
    if status_code == 200:
        try:
            response_str = consumption_endpoint.get('response', '{}')
            consumption_data = json.loads(response_str) if isinstance(response_str, str) else response_str
            
            if isinstance(consumption_data, dict):
                total_acus = consumption_data.get('total_acus', 0)
                consumption_by_date = consumption_data.get('consumption_by_date', {})
                
                if consumption_by_date:
                    total_records = len(consumption_by_date)
                    avg_acus = total_acus / total_records if total_records > 0 else 0.0
                    
                    summary = {
                        'Status': 'SUCCESS',
                        'Total_Daily_Consumption_Records': total_records,
                        'Average_ACUs_Per_Day': round(avg_acus, 2)
                    }
                    
                    logger.info(f"Business summary from API: {total_records} records, {avg_acus:.2f} avg ACUs/day")
                else:
                    logger.info("No consumption_by_date found in API response")
            else:
                logger.info("API response is not a dictionary")
                
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse consumption data: {e}")
        except Exception as e:
            logger.error(f"Error processing consumption data: {e}")
    else:
        logger.warning(f"Consumption endpoint returned status {status_code}")
    
    if summary['Total_Daily_Consumption_Records'] == 0 and calculated_metrics:
        total_sessions = calculated_metrics.get('metrics', {}).get('06_total_sessions', 0)
        total_acus = calculated_metrics.get('metrics', {}).get('02_total_acus', 0)
        num_days = 31
        avg_acus = total_acus / num_days if num_days > 0 else 0.0
        
        summary = {
            'Status': 'SUCCESS (using calculated metrics)',
            'Total_Daily_Consumption_Records': total_sessions,
            'Average_ACUs_Per_Day': round(avg_acus, 2)
        }
        logger.info(f"Using calculated metrics fallback: {total_sessions} records, {avg_acus:.2f} avg ACUs/day")
    
    print("\n" + "=" * 60)
    print("Business Summary (Consumption Data)")
    print("=" * 60)
    print(f"Summary: {summary}")
    print("=" * 60 + "\n")
