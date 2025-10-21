"""
Generate FinOps Metrics Report
Phase 3: Reporting and Output Generation

This script loads raw usage data, transforms it to the expected format,
calculates all 20 FinOps metrics using the metrics_calculator module,
and exports the results to a CSV file.
"""

import json
import logging
import pandas as pd
from datetime import datetime
from typing import Dict, Any, List
from collections import defaultdict
from metrics_calculator import MetricsCalculator
from config import MetricsConfig

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def transform_raw_data(raw_sessions: List[Dict]) -> Dict[str, Any]:
    """
    Transform raw usage data into the format expected by MetricsCalculator.
    
    Args:
        raw_sessions: List of session dictionaries from raw_usage_data.json
    
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
    start_date = timestamps[0][:10] if timestamps else '2025-01-01'
    end_date = timestamps[-1][:10] if timestamps else '2025-01-31'
    
    reporting_period = {
        'start_date': start_date,
        'end_date': end_date,
        'month': f"{start_date} to {end_date}"
    }
    
    transformed_data = {
        'organization': 'Deloitte',
        'reporting_period': reporting_period,
        'sessions': transformed_sessions,
        'user_details': user_details
    }
    
    logger.info(f"Transformation complete: {len(transformed_sessions)} sessions, {len(user_details)} unique users")
    return transformed_data


def flatten_metrics(metrics_dict: Dict[str, Any], config: MetricsConfig) -> List[Dict[str, Any]]:
    """
    Flatten the nested metrics dictionary into a list of rows for CSV export.
    
    Args:
        metrics_dict: Dictionary returned by calculate_all_metrics()
        config: MetricsConfig object with currency information
    
    Returns:
        List of dictionaries, each representing one metric row
    """
    rows = []
    metrics = metrics_dict.get('metrics', {})
    currency = config.currency
    
    metric_definitions = {
        '01_total_monthly_cost': ('Total Monthly Cost', currency),
        '02_total_acus': ('Total ACUs', 'ACUs'),
        '03_cost_per_user': ('Cost Per User', currency),
        '04_acus_per_session': ('ACUs Per Session', 'ACUs'),
        '05_average_acus_per_session': ('Average ACUs Per Session', 'ACUs'),
        '06_total_sessions': ('Total Sessions', 'sessions'),
        '07_sessions_per_user': ('Sessions Per User', 'sessions'),
        '08_total_duration_minutes': ('Total Duration', 'minutes'),
        '09_average_session_duration': ('Average Session Duration', 'minutes'),
        '10_acus_per_minute': ('ACUs Per Minute', 'ACUs/min'),
        '11_cost_per_minute': ('Cost Per Minute', f'{currency}/min'),
        '12_unique_users': ('Unique Users', 'users'),
        '13_sessions_by_task_type': ('Sessions By Task Type', 'sessions'),
        '14_acus_by_task_type': ('ACUs By Task Type', 'ACUs'),
        '15_cost_by_task_type': ('Cost By Task Type', currency),
        '16_sessions_by_department': ('Sessions By Department', 'sessions'),
        '17_acus_by_department': ('ACUs By Department', 'ACUs'),
        '18_cost_by_department': ('Cost By Department', currency),
        '19_average_cost_per_user': ('Average Cost Per User', currency),
        '20_efficiency_ratio': ('Efficiency Ratio', 'ACUs/hour')
    }
    
    for metric_key, (metric_name, unit) in metric_definitions.items():
        value = metrics.get(metric_key)
        
        if isinstance(value, dict):
            for sub_key, sub_value in value.items():
                rows.append({
                    'metric_name': f"{metric_name} - {sub_key}",
                    'value': sub_value,
                    'unit': unit
                })
        elif isinstance(value, (int, float)):
            rows.append({
                'metric_name': metric_name,
                'value': value,
                'unit': unit
            })
        else:
            rows.append({
                'metric_name': metric_name,
                'value': str(value),
                'unit': unit
            })
    
    return rows


def main():
    """Main execution function for report generation."""
    logger.info("=" * 60)
    logger.info("FinOps Metrics Report Generator - Phase 3")
    logger.info("=" * 60)
    
    raw_data_file = 'raw_usage_data.json'
    output_file = 'finops_metrics_report.csv'
    temp_transformed_file = 'transformed_usage_data.json'
    
    logger.info(f"Loading raw data from {raw_data_file}...")
    with open(raw_data_file, 'r') as f:
        raw_sessions = json.load(f)
    
    logger.info(f"Loaded {len(raw_sessions)} raw sessions")
    
    transformed_data = transform_raw_data(raw_sessions)
    
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
    
    logger.info("Flattening metrics for CSV export...")
    flattened_rows = flatten_metrics(all_metrics, config)
    
    df = pd.DataFrame(flattened_rows)
    
    df.to_csv(output_file, index=False)
    logger.info(f"Report exported to {output_file}")
    
    num_metrics = len(df)
    logger.info(f"Report created successfully with {num_metrics} metric entries")
    
    logger.info("\nSummary Statistics:")
    logger.info(f"  - Total Sessions: {all_metrics['metrics']['06_total_sessions']}")
    logger.info(f"  - Total ACUs: {all_metrics['metrics']['02_total_acus']}")
    logger.info(f"  - Total Cost: {all_metrics['metrics']['01_total_monthly_cost']:.2f} {config.currency}")
    logger.info(f"  - Unique Users: {all_metrics['metrics']['12_unique_users']}")
    
    logger.info("\n" + "=" * 60)
    logger.info("Report generation completed successfully!")
    logger.info("=" * 60)
    
    return output_file


if __name__ == '__main__':
    try:
        output_file = main()
        print(f"\n✓ SUCCESS: Report generated at {output_file}")
    except Exception as e:
        logger.error(f"Error generating report: {e}", exc_info=True)
        print(f"\n✗ FAILED: {e}")
        raise
