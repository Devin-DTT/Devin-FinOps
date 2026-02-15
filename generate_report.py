"""
Generate FinOps Metrics Report
Phase 3: Reporting and Output Generation

This script orchestrates the FinOps report generation pipeline:
1. Fetches data from the Cognition Enterprise API
2. Transforms raw data using data_transformer
3. Calculates metrics using metrics_service and metrics_calculator
4. Exports results to CSV, Excel, and HTML dashboard
"""

import json
import logging
import argparse
from typing import Dict, Any
from metrics_calculator import MetricsCalculator
from config import MetricsConfig
import data_adapter
from export_metrics import export_daily_acus_to_csv
from export_metrics_summary import (
    export_summary_to_excel,
    ExportConfig,
    ReportData,
    BreakdownData,
    ReportBuilder,
)
from metrics_service import (
    calculate_monthly_acus,
    calculate_base_metrics,
    calculate_finops_metrics,
)
from data_transformer import (
    transform_raw_data,
    create_summary_csv,
    generate_business_summary,
    generate_consumption_summary,
)

logger = logging.getLogger(__name__)


TARGET_USER_ID = "email|68edea2414c1686557f8d5a6"

API_ENDPOINTS = {
    'consumption_cycles': '/consumption/cycles',
    'consumption_daily': '/consumption/daily',
    'metrics_prs': '/metrics/prs',
    'metrics_sessions': '/metrics/sessions',
    'metrics_searches': '/metrics/searches',
    'metrics_usage': '/metrics/usage',
    'roles': '/roles',
    'members': '/members',
    'members_user': f'/members/{TARGET_USER_ID}',
    'members_user_orgs': f'/members/{TARGET_USER_ID}/organizations',
    'groups': '/groups',
    'api_keys': '/api-keys',
    'playbooks': '/playbooks'
}


def main():
    """Main execution function for report generation."""
    parser = argparse.ArgumentParser(description='Generate FinOps metrics report')
    parser.add_argument(
        '--start',
        type=str,
        default='2025-10-01',
        help='Start date for reporting period (YYYY-MM-DD)'
    )
    parser.add_argument(
        '--end',
        type=str,
        default='2025-10-31',
        help='End date for reporting period (YYYY-MM-DD)'
    )
    args = parser.parse_args()
    
    data_adapter.setup_logging()

    logger.info("=" * 60)
    logger.info("FinOps Metrics Report Generator - Phase 3")
    logger.info("=" * 60)
    logger.info("Date range: %s to %s", args.start, args.end)
    
    logger.info("Fetching data from all Enterprise API endpoints...")
    all_api_data = None
    daily_chart_data = []
    user_chart_data = []
    user_breakdown_list = []
    
    from datetime import datetime, timedelta
    
    logger.info("Step 1: Fetching consumption_cycles to determine dynamic date range...")
    cycles_endpoint = {'consumption_cycles': API_ENDPOINTS['consumption_cycles']}
    
    try:
        cycles_data = data_adapter.fetch_api_data(cycles_endpoint)
        
        if cycles_data and 'consumption_cycles' in cycles_data:
            cycles_response = cycles_data['consumption_cycles']
            
            if cycles_response.get('status_code') == 200:
                cycles_list = cycles_response.get('response', [])
                
                if isinstance(cycles_list, str):
                    cycles_list = json.loads(cycles_list)
                
                if isinstance(cycles_list, list) and len(cycles_list) > 0:
                    logger.info(f"Found {len(cycles_list)} consumption cycles")
                    
                    all_starts = []
                    all_ends = []
                    
                    for cycle in cycles_list:
                        if 'start' in cycle:
                            start_dt = datetime.fromisoformat(cycle['start'].replace('Z', '+00:00'))
                            all_starts.append(start_dt)
                        if 'end' in cycle:
                            end_dt = datetime.fromisoformat(cycle['end'].replace('Z', '+00:00'))
                            all_ends.append(end_dt)
                    
                    if all_starts and all_ends:
                        earliest_start = min(all_starts)
                        latest_end = max(all_ends)
                        
                        start_for_trend = earliest_start.strftime('%Y-%m-%d')
                        end_for_trend = latest_end.strftime('%Y-%m-%d')
                        
                        logger.info(f"Dynamic date range determined from consumption_cycles:")
                        logger.info(f"  Earliest start: {start_for_trend}")
                        logger.info(f"  Latest end: {end_for_trend}")
                    else:
                        logger.warning("No valid dates found in consumption_cycles, using fallback")
                        end_dt = datetime.strptime(args.end, '%Y-%m-%d')
                        start_for_trend = (end_dt - timedelta(days=1825)).strftime('%Y-%m-%d')
                        end_for_trend = args.end
                else:
                    logger.warning("consumption_cycles returned empty list, using fallback date range")
                    end_dt = datetime.strptime(args.end, '%Y-%m-%d')
                    start_for_trend = (end_dt - timedelta(days=1825)).strftime('%Y-%m-%d')
                    end_for_trend = args.end
            else:
                logger.warning(f"consumption_cycles returned status {cycles_response.get('status_code')}, using fallback")
                end_dt = datetime.strptime(args.end, '%Y-%m-%d')
                start_for_trend = (end_dt - timedelta(days=1825)).strftime('%Y-%m-%d')
                end_for_trend = args.end
        else:
            logger.warning("Failed to fetch consumption_cycles, using fallback date range")
            end_dt = datetime.strptime(args.end, '%Y-%m-%d')
            start_for_trend = (end_dt - timedelta(days=1825)).strftime('%Y-%m-%d')
            end_for_trend = args.end
            
    except Exception as e:
        logger.error(f"Error fetching consumption_cycles: {e}")
        logger.info("Using fallback date range")
        end_dt = datetime.strptime(args.end, '%Y-%m-%d')
        start_for_trend = (end_dt - timedelta(days=1825)).strftime('%Y-%m-%d')
        end_for_trend = args.end
    
    params_by_endpoint = {
        'consumption_daily': {
            'start_date': start_for_trend,
            'end_date': end_for_trend
        }
    }
    logger.info(f"Step 2: Fetching consumption_daily with dynamic date range: {start_for_trend} to {end_for_trend}")
    
    try:
        all_api_data = data_adapter.fetch_api_data(API_ENDPOINTS, params_by_endpoint=params_by_endpoint)
        data_adapter.save_raw_data(all_api_data)
        create_summary_csv(all_api_data)
        logger.info("Multi-endpoint data fetch completed successfully")
        
        if all_api_data:
            consumption_endpoint = all_api_data.get('consumption_daily', {})
            if consumption_endpoint.get('status_code') == 200:
                try:
                    response_data = consumption_endpoint.get('response', {})
                    if isinstance(response_data, str):
                        response_data = json.loads(response_data)
                    
                    if isinstance(response_data, dict):
                        consumption_by_date = response_data.get('consumption_by_date', {})
                        for date, acus in consumption_by_date.items():
                            daily_chart_data.append({'Date': date, 'ACUs': acus})
                        
                        consumption_by_user = response_data.get('consumption_by_user', {})
                        for user_id, acus in consumption_by_user.items():
                            user_chart_data.append({'User ID': user_id, 'ACUs': acus})
                        
                        logger.info(f"Prepared chart data: {len(daily_chart_data)} daily records, {len(user_chart_data)} users")
                except (json.JSONDecodeError, AttributeError, TypeError) as e:
                    logger.warning(f"Failed to prepare chart data: {e}")
        
    except Exception as e:
        logger.error(f"Failed to fetch multi-endpoint data: {e}")
        logger.info("Continuing with report generation using existing data if available")
    
    logger.info("Fetching data from Cognition API...")
    try:
        data_adapter.main(start_date=args.start, end_date=args.end)
    except Exception as e:
        logger.error(f"Failed to fetch data from API: {e}")
        logger.info("Attempting to use existing raw_usage_data.json if available")

    raw_data_file = 'raw_usage_data.json'
    temp_transformed_file = 'transformed_usage_data.json'
    
    logger.info(f"Loading raw data from {raw_data_file}...")
    with open(raw_data_file, 'r') as f:
        raw_sessions = json.load(f)
    
    logger.info(f"Loaded {len(raw_sessions)} raw sessions")
    
    transformed_data = transform_raw_data(raw_sessions, start_date=args.start, end_date=args.end)
    
    with open(temp_transformed_file, 'w') as f:
        json.dump(transformed_data, f, indent=2)
    logger.info(f"Transformed data saved to {temp_transformed_file}")
    
    config = MetricsConfig(price_per_acu=0.05, currency='USD')
    logger.info(f"Configuration: price_per_acu={config.price_per_acu}, currency={config.currency}")
    
    calculator = MetricsCalculator(config)
    calculator.load_data(temp_transformed_file)
    logger.info("Data loaded into MetricsCalculator")
    
    logger.info("Calculating all 20 metrics...")
    all_metrics = calculator.calculate_all_metrics()
    
    user_org_mappings = {}
    
    if all_api_data:
        consumption_endpoint = all_api_data.get('consumption_daily', {})
        if consumption_endpoint.get('status_code') == 200:
            try:
                response_data = consumption_endpoint.get('response', {})
                if isinstance(response_data, str):
                    response_data = json.loads(response_data)
                
                if isinstance(response_data, dict):
                    consumption_by_user = response_data.get('consumption_by_user', {})
                    
                    unique_user_ids = list(consumption_by_user.keys())
                    logger.info(f"Extracted {len(unique_user_ids)} unique user IDs from consumption data")
                    
                    if unique_user_ids:
                        logger.info("Fetching organization mappings for all users...")
                        user_org_mappings = data_adapter.fetch_user_organization_mappings(unique_user_ids)
                        logger.info(f"Organization mappings fetched for {len(user_org_mappings)} users")
                    
                    for user_id, acus in consumption_by_user.items():
                        cost_usd = round(acus * config.price_per_acu, 2)
                        
                        org_info = user_org_mappings.get(user_id, {})
                        org_id = org_info.get('organization_id', 'Unmapped')
                        org_name = org_info.get('organization_name', 'Unmapped')
                        
                        user_breakdown_list.append({
                            'User ID': user_id,
                            'ACUs Consumed': round(acus, 2),
                            'Cost (USD)': cost_usd,
                            'Organization ID': org_id,
                            'Organization Name': org_name
                        })
                    logger.info(f"Prepared user breakdown list with {len(user_breakdown_list)} users from API data")
            except (json.JSONDecodeError, AttributeError, TypeError) as e:
                logger.warning(f"Failed to prepare user breakdown list from API: {e}")
    
    if not user_breakdown_list:
        logger.info("API data not available, aggregating user consumption from transformed sessions")
        user_acus_map = {}
        for session in transformed_data.get('sessions', []):
            user_email = session.get('user_email', 'unknown')
            acus = session.get('acus_consumed', 0)
            if user_email not in user_acus_map:
                user_acus_map[user_email] = 0
            user_acus_map[user_email] += acus
        
        for user_email, total_acus in user_acus_map.items():
            cost_usd = round(total_acus * config.price_per_acu, 2)
            user_breakdown_list.append({
                'User ID': user_email,
                'ACUs Consumed': round(total_acus, 2),
                'Cost (USD)': cost_usd,
                'Organization ID': 'Unmapped',
                'Organization Name': 'Unmapped'
            })
        logger.info(f"Prepared user breakdown list with {len(user_breakdown_list)} users from transformed data")
    
    org_breakdown_summary = {}
    logger.info("Aggregating data by organization...")
    for user_data in user_breakdown_list:
        org_id = user_data.get('Organization ID', 'Unmapped')
        org_name = user_data.get('Organization Name', 'Unmapped')
        acus = user_data.get('ACUs Consumed', 0)
        cost = user_data.get('Cost (USD)', 0)
        
        if org_id not in org_breakdown_summary:
            org_breakdown_summary[org_id] = {
                'Organization ID': org_id,
                'Organization Name': org_name,
                'Total ACUs Consumed': 0.0,
                'Total Cost (USD)': 0.0
            }
        
        org_breakdown_summary[org_id]['Total ACUs Consumed'] += acus
        org_breakdown_summary[org_id]['Total Cost (USD)'] += cost
    
    for org_id in org_breakdown_summary:
        org_breakdown_summary[org_id]['Total ACUs Consumed'] = round(org_breakdown_summary[org_id]['Total ACUs Consumed'], 2)
        org_breakdown_summary[org_id]['Total Cost (USD)'] = round(org_breakdown_summary[org_id]['Total Cost (USD)'], 2)
    
    logger.info(f"Organization aggregation complete: {len(org_breakdown_summary)} organizations")
    
    logger.info("Calculating base metrics with traceability...")
    base_data = calculate_base_metrics(all_api_data, config)
    logger.info(f"Base metrics calculated: {len(base_data)} metrics")
    
    logger.info("Calculating FinOps metrics with traceability...")
    finops_metrics = calculate_finops_metrics(base_data, end_date=args.end)
    logger.info(f"FinOps metrics calculated: {len(finops_metrics)} metrics")
    
    generate_consumption_summary(all_api_data, all_metrics)
    
    summary_data = generate_business_summary(all_metrics, config, all_api_data)
    
    export_daily_acus_to_csv()
    
    monthly_consumption_history = []
    consumption_by_date = base_data.get('consumption_by_date', {}).get('value', {})
    if consumption_by_date:
        logger.info("Preparing monthly consumption history...")
        unique_months = set()
        for date_str in consumption_by_date.keys():
            if len(date_str) >= 7:
                month_prefix = date_str[:7]
                unique_months.add(month_prefix)
        
        for month_prefix in sorted(unique_months):
            monthly_acus = calculate_monthly_acus(consumption_by_date, month_prefix)
            monthly_consumption_history.append({
                'Mes': month_prefix,
                'Consumo Total (ACUs)': round(monthly_acus, 2)
            })
        
        logger.info(f"Prepared monthly consumption history with {len(monthly_consumption_history)} months")
    else:
        logger.warning("No consumption_by_date data available for monthly history")
    
    export_cfg = ExportConfig(output_filename='finops_summary_report.xlsx', config=config)
    report_data = ReportData(
        consumption_data=all_metrics or {},
        all_api_data=all_api_data,
        summary_data=summary_data,
        finops_metrics=finops_metrics,
    )
    breakdown = BreakdownData(
        user_breakdown_list=user_breakdown_list,
        org_breakdown_summary=org_breakdown_summary,
        monthly_consumption_history=monthly_consumption_history,
    )
    (
        ReportBuilder(export_cfg)
        .with_report_data(report_data)
        .with_breakdown_data(breakdown)
        .build()
    )
    
    from html_dashboard import generate_html_dashboard
    generate_html_dashboard(summary_data, daily_chart_data, user_chart_data)


if __name__ == '__main__':
    try:
        main()
        print("\n+ SUCCESS: All API data saved and FinOps Summary computed.")
    except Exception as e:
        logger.error(f"Error generating report: {e}", exc_info=True)
        print(f"\n- FAILED: {e}")
        raise
