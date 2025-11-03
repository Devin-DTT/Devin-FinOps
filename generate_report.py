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
import data_adapter

logger = logging.getLogger(__name__)


API_ENDPOINTS = {
    'consumption_cycles': '/consumption/cycles',
    'consumption_daily': '/consumption/daily',
    'consumption_daily_user': '/consumption/daily/gtorreshuamantica',
    'metrics_prs': '/metrics/prs',
    'metrics_sessions': '/metrics/sessions',
    'metrics_searches': '/metrics/searches',
    'metrics_usage': '/metrics/usage',
    'roles': '/roles',
    'members': '/members',
    'members_user': '/members/gtorreshuamantica',
    'members_user_orgs': '/members/gtorreshuamantica/organizations',
    'groups': '/groups',
    'hypervisors_health': '/hypervisors/health',
    'audit_logs': '/audit-logs',
    'api_keys': '/api-keys',
    'playbooks': '/playbooks'
}


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
    Creates 63 KPI rows with 6 columns: Métrica, Descripción, Uso FinOps, Pilar, Subpilar, Coste.
    
    Args:
        metrics_dict: Dictionary returned by calculate_all_metrics()
        config: MetricsConfig object with currency information
    
    Returns:
        List of dictionaries, each representing one KPI row
    """
    rows = []
    metrics = metrics_dict.get('metrics', {})
    
    def aggregate_dict(value):
        if isinstance(value, dict):
            return sum(v for v in value.values() if isinstance(v, (int, float)))
        return value

    kpi_definitions = [
        {'metrica': 'Coste total mensual', 'metric_key': '01_total_monthly_cost', 'is_calculated': True, 'aggregate': False},
        {'metrica': 'Coste por organización', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Coste por grupo (IdP)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Coste por usuario', 'metric_key': '03_cost_per_user', 'is_calculated': True, 'aggregate': True},
        {'metrica': 'Coste por repositorio / PR', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Coste por tipo de tarea', 'metric_key': '15_cost_by_task_type', 'is_calculated': True, 'aggregate': True},
        {'metrica': 'ACUs consumidos totales', 'metric_key': '02_total_acus', 'is_calculated': True, 'aggregate': False},
        {'metrica': 'ACUs por usuario', 'metric_key': '03_cost_per_user', 'is_calculated': True, 'aggregate': True, 'divide_by_price': True},
        {'metrica': 'ACUs por sesión', 'metric_key': '05_average_acus_per_session', 'is_calculated': True, 'aggregate': False},
        {'metrica': 'Coste por unidad de negocio / tribu / área', 'metric_key': '18_cost_by_department', 'is_calculated': True, 'aggregate': True},
        {'metrica': 'Coste por proyecto / producto', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% coste compartido (shared)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Costo total Devin', 'metric_key': '01_total_monthly_cost', 'is_calculated': True, 'aggregate': False},
        {'metrica': 'ACUs por desarrollador', 'metric_key': '03_cost_per_user', 'is_calculated': True, 'aggregate': True, 'divide_by_price': True},
        {'metrica': '% de consumo trazable (cost allocation)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Coste por plan (Core/Teams)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'ACUs promedio por PR mergeado', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'ACUs por línea de código', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'ACUs por outcome (tarea completada)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'ROI por sesión', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% sesiones con outcome', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'ACUs por hora productiva', 'metric_key': '20_efficiency_ratio', 'is_calculated': True, 'aggregate': False},
        {'metrica': '% sesiones reintentadas', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Promedio de PRs por ACU', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Tasa de éxito de PR', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% sesiones sin outcome', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'ACUs desperdiciados (sin ROI)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Sesiones idle (>X min)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% tareas redundantes / duplicadas', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Costo por PR mergeado', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Ahorro FinOps (%)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'ACU Efficiency Index (AEI)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Cost Velocity Ratio (CVR)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Waste-to-Outcome Ratio (WOR)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Ahorro FinOps acumulado', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Lead time con Devin vs humano', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Prompts ineficientes (alto ACU / bajo output)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Prompts eficientes (%)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Prompt Efficiency Index (PEI)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Team Efficiency Spread', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% de usuarios formados en eficiencia de prompts', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'ACUs por tipo de usuario', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Días restantes hasta presupuesto agotado', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Over-scope sessions (>N ACUs)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% ACUs usados fuera de horario laboral', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Coste por entorno (Dev/Test/Prod)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% proyectos con límites activos', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% de proyectos con presupuesto definido', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% de proyectos con alertas activas', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Tiempo medio de reacción a alerta 90%', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Coste incremental PAYG', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'ACUs por sprint / release', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Peak ACU rate', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Costo por entrega (delivery)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Uso presupuestario (%)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Cumplimiento presupuestario', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': '% de proyectos sobre presupuesto', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Desviación forecast vs real', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Tendencia semanal de ACUs', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Estacionalidad de consumo', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Proyección de gasto mensual', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Elasticidad del coste', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Costo incremental por usuario nuevo', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Coste por cliente (externo)', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
        {'metrica': 'Coste recuperable', 'metric_key': None, 'is_calculated': False, 'aggregate': False},
    ]

    placeholder_desc = 'Descripción pendiente de definición'
    placeholder_uso = 'Pendiente de clasificación'
    placeholder_pilar = 'Por definir'
    placeholder_subpilar = 'Por definir'

    for kpi in kpi_definitions:
        row = {
            'Métrica': kpi['metrica'],
            'Descripción': placeholder_desc,
            'Uso FinOps': placeholder_uso,
            'Pilar': placeholder_pilar,
            'Subpilar': placeholder_subpilar
        }

        if kpi['is_calculated'] and kpi['metric_key']:
            value = metrics.get(kpi['metric_key'])

            if kpi.get('aggregate'):
                value = aggregate_dict(value)

            if kpi.get('divide_by_price') and value is not None:
                value = value / config.price_per_acu

            if isinstance(value, float):
                row['Coste'] = round(value, 2)
            else:
                row['Coste'] = value
        else:
            row['Coste'] = 'N/A'

        rows.append(row)
    
    return rows


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
    logger.info(f"  - Columns: endpoint_name, full_url, status_code, timestamp")

def generate_business_summary(consumption_data: Dict[str, Any], config: MetricsConfig) -> None:
    """
    Display final business summary with ASCII formatting using print().
    
    Args:
        consumption_data: Dictionary containing metrics and reporting_period
        config: MetricsConfig object with pricing information
    """
    metrics = consumption_data.get('metrics', {})
    reporting_period = consumption_data.get('reporting_period', {})
    
    total_sessions = metrics.get('06_total_sessions', 0)
    total_acus = metrics.get('02_total_acus', 0)
    total_cost = metrics.get('01_total_monthly_cost', 0)
    unique_users = metrics.get('12_unique_users', 0)
    
    start_date = reporting_period.get('start_date', 'N/A')
    end_date = reporting_period.get('end_date', 'N/A')
    
    num_days = 1
    if start_date != 'N/A' and end_date != 'N/A':
        try:
            from datetime import datetime
            start = datetime.strptime(start_date, '%Y-%m-%d')
            end = datetime.strptime(end_date, '%Y-%m-%d')
            num_days = max(1, (end - start).days + 1)
        except:
            num_days = 1
    
    average_acus_per_day = total_acus / num_days if num_days > 0 else 0
    
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




def main():
    """Main execution function for report generation."""
    data_adapter.setup_logging()

    logger.info("=" * 60)
    logger.info("FinOps Metrics Report Generator - Phase 3")
    logger.info("=" * 60)
    
    logger.info("Fetching data from all Enterprise API endpoints...")
    try:
        all_api_data = data_adapter.fetch_api_data(API_ENDPOINTS)
        data_adapter.save_raw_data(all_api_data)
        create_summary_csv(all_api_data)
        logger.info("Multi-endpoint data fetch completed successfully")
    except Exception as e:
        logger.error(f"Failed to fetch multi-endpoint data: {e}")
        logger.info("Continuing with report generation using existing data if available")
    
    logger.info("Fetching data from Cognition API...")
    try:
        data_adapter.main()
    except Exception as e:
        logger.error(f"Failed to fetch data from API: {e}")
        logger.info("Attempting to use existing raw_usage_data.json if available")

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
    
    generate_business_summary(all_metrics, config)
    
    return output_file


if __name__ == '__main__':
    try:
        output_file = main()
        print(f"\n✓ SUCCESS: Report generated at {output_file}")
    except Exception as e:
        logger.error(f"Error generating report: {e}", exc_info=True)
        print(f"\n✗ FAILED: {e}")
        raise
