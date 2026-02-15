"""
Metrics Service Module for FinOps Report Generation.
Handles calculation of base metrics and FinOps metrics with full traceability.
Extracted from generate_report.py for separation of responsibilities.
"""

import json
import logging
from typing import Dict, Any
from config import MetricsConfig

logger = logging.getLogger(__name__)


def calculate_monthly_acus(consumption_by_date: Dict[str, float], month_prefix_str: str) -> float:
    """
    Calculate total ACUs for a specific month by filtering consumption_by_date.
    
    Args:
        consumption_by_date: Dictionary mapping ISO date strings to ACU consumption
        month_prefix_str: Target month prefix in YYYY-MM format (e.g., "2024-11")
    
    Returns:
        Total ACUs consumed in the specified month
    """
    total_acus = 0.0
    
    for date_str, acus in consumption_by_date.items():
        if date_str.startswith(month_prefix_str):
            total_acus += acus
    
    return total_acus


def calculate_cost_from_acus(acus: float, cost_per_acu: float) -> float:
    """
    Calculate cost from ACUs by applying the cost multiplier.
    
    Args:
        acus: ACU value to convert to cost
        cost_per_acu: Cost per ACU constant (e.g., 0.05)
    
    Returns:
        Calculated cost formatted to two decimal places
    """
    return round(acus * cost_per_acu, 2)


def calculate_base_metrics(all_api_data: Dict[str, Dict[str, Any]], config: MetricsConfig) -> Dict[str, Dict[str, Any]]:
    """
    Calculate base metrics with full traceability to JSON source paths.
    
    Args:
        all_api_data: Dictionary of API results from fetch_api_data()
        config: MetricsConfig object with pricing information
    
    Returns:
        Dictionary containing base metrics with 'value' and 'source' keys
    """
    base_data = {}
    
    consumption_endpoint = all_api_data.get('consumption_daily', {})
    if consumption_endpoint.get('status_code') == 200:
        try:
            response_data = consumption_endpoint.get('response', {})
            if isinstance(response_data, str):
                response_data = json.loads(response_data)
            
            if isinstance(response_data, dict):
                total_acus = response_data.get('total_acus', 0)
                consumption_by_date = response_data.get('consumption_by_date', {})
                consumption_by_user = response_data.get('consumption_by_user', {})
                consumption_by_org_id = response_data.get('consumption_by_org_id', {})
                
                base_data['total_acus'] = {
                    'value': total_acus,
                    'source': 'consumption_daily.response.total_acus'
                }
                base_data['consumption_by_date'] = {
                    'value': consumption_by_date,
                    'source': 'consumption_daily.response.consumption_by_date'
                }
                base_data['consumption_by_user'] = {
                    'value': consumption_by_user,
                    'source': 'consumption_daily.response.consumption_by_user'
                }
                base_data['consumption_by_org_id'] = {
                    'value': consumption_by_org_id,
                    'source': 'consumption_daily.response.consumption_by_org_id'
                }
                base_data['unique_users'] = {
                    'value': len(consumption_by_user),
                    'source': 'consumption_daily.response.consumption_by_user (count)'
                }
                base_data['num_organizations'] = {
                    'value': len(consumption_by_org_id),
                    'source': 'consumption_daily.response.consumption_by_org_id (count keys)'
                }
        except (json.JSONDecodeError, AttributeError, TypeError) as e:
            logger.warning(f"Failed to extract consumption_daily metrics: {e}")
    
    metrics_prs_endpoint = all_api_data.get('metrics_prs', {})
    if metrics_prs_endpoint.get('status_code') == 200:
        try:
            prs_response = metrics_prs_endpoint.get('response', {})
            if isinstance(prs_response, str):
                prs_response = json.loads(prs_response)
            if isinstance(prs_response, dict):
                prs_opened = prs_response.get('prs_opened', 0)
                prs_closed = prs_response.get('prs_closed', 0)
                prs_merged = prs_response.get('prs_merged', 0)
                
                base_data['prs_opened'] = {
                    'value': prs_opened,
                    'source': 'metrics_prs.response.prs_opened'
                }
                base_data['prs_closed'] = {
                    'value': prs_closed,
                    'source': 'metrics_prs.response.prs_closed'
                }
                base_data['prs_merged'] = {
                    'value': prs_merged,
                    'source': 'metrics_prs.response.prs_merged'
                }
        except (json.JSONDecodeError, AttributeError, TypeError) as e:
            logger.warning(f"Failed to extract metrics_prs data: {e}")
    
    metrics_sessions_endpoint = all_api_data.get('metrics_sessions', {})
    if metrics_sessions_endpoint.get('status_code') == 200:
        try:
            sessions_response = metrics_sessions_endpoint.get('response', {})
            if isinstance(sessions_response, str):
                sessions_response = json.loads(sessions_response)
            if isinstance(sessions_response, dict):
                sessions_count = sessions_response.get('sessions_count', 0)
                base_data['sessions_count'] = {
                    'value': sessions_count,
                    'source': 'metrics_sessions.response.sessions_count'
                }
        except (json.JSONDecodeError, AttributeError, TypeError) as e:
            logger.warning(f"Failed to extract metrics_sessions data: {e}")
    
    members_endpoint = all_api_data.get('members', {})
    if members_endpoint.get('status_code') == 200:
        try:
            members_response = members_endpoint.get('response', {})
            if isinstance(members_response, str):
                members_response = json.loads(members_response)
            if isinstance(members_response, dict):
                user_count = members_response.get('total', 0)
                base_data['user_count'] = {
                    'value': user_count,
                    'source': 'members.response.total'
                }
        except (json.JSONDecodeError, AttributeError, TypeError) as e:
            logger.warning(f"Failed to extract members data: {e}")
    
    base_data['price_per_acu'] = {
        'value': config.price_per_acu,
        'source': 'config.price_per_acu'
    }
    
    return base_data


def calculate_finops_metrics(base_data: Dict[str, Dict[str, Any]], end_date: str = None) -> Dict[str, Dict[str, Any]]:
    """
    Calculate FinOps metrics with full traceability.
    Metrics are organized by category: COST VISIBILITY, COST OPTIMIZATION, FORECAST.
    
    Note: Inviable metrics that require external data not available via the Cognition API
    have been moved to FUTURE_METRICS_ROADMAP.md for documentation purposes.
    
    Args:
        base_data: Dictionary containing base metrics with 'value' and 'source' keys
        end_date: End date of reporting period (YYYY-MM-DD) for calculating monthly trends
    
    Returns:
        Dictionary containing calculable metrics with traceability information, organized by category
    """
    from datetime import datetime
    from dateutil.relativedelta import relativedelta
    
    all_metrics = {}
    
    def get_value(key, default=0):
        return base_data.get(key, {}).get('value', default)
    
    def get_source(key):
        return base_data.get(key, {}).get('source', 'N/A')
    
    total_acus = get_value('total_acus', 0)
    price_per_acu = get_value('price_per_acu', 0)
    total_cost = total_acus * price_per_acu
    prs_merged = get_value('prs_merged', 0)
    sessions_count = get_value('sessions_count', 0)
    unique_users = get_value('unique_users', 0)
    user_count = get_value('user_count', 0)
    consumption_by_user = get_value('consumption_by_user', {})
    consumption_by_date = get_value('consumption_by_date', {})
    
    cost_per_pr = total_cost / prs_merged if prs_merged > 0 else 0
    acus_per_pr = total_acus / prs_merged if prs_merged > 0 else 0
    acus_per_session = total_acus / sessions_count if sessions_count > 0 else 0
    prs_per_acu = prs_merged / total_acus if total_acus > 0 else 0
    
    
    if consumption_by_date:
        try:
            today = datetime.now()
            logger.info(f"Using system date for monthly calculation: {today.strftime('%Y-%m-%d')}")
            
            current_month_prefix = today.strftime("%Y-%m")
            
            previous_month = today - relativedelta(months=1)
            previous_month_prefix = previous_month.strftime("%Y-%m")
            
            logger.info(f"Current month prefix: {current_month_prefix}")
            logger.info(f"Previous month prefix: {previous_month_prefix}")
            
            current_month_acus = calculate_monthly_acus(consumption_by_date, current_month_prefix)
            prev_month_acus = calculate_monthly_acus(consumption_by_date, previous_month_prefix)
            
            logger.info(f"Current month ACUs: {current_month_acus}")
            logger.info(f"Previous month ACUs: {prev_month_acus}")
            
            current_month_cost = calculate_cost_from_acus(current_month_acus, price_per_acu)
            prev_month_cost = calculate_cost_from_acus(prev_month_acus, price_per_acu)
            
            absolute_difference = current_month_cost - prev_month_cost
            
            if prev_month_cost > 0:
                percentage_difference = (absolute_difference / prev_month_cost) * 100
                percentage_value = round(percentage_difference, 2)
                percentage_formula = '(Current Cost - Previous Cost) / Previous Cost * 100'
            else:
                percentage_value = 'N/A (Costo Base Cero)'
                percentage_formula = 'Cannot calculate (Previous Cost is zero)'
            
            all_metrics['ACUs Mes'] = {
                'value': round(current_month_acus, 2),
                'raw_data_value': round(current_month_acus, 2),
                'formula': f'Python Function (calculate_monthly_acus) filtering data for {current_month_prefix}',
                'sources_used': [
                    {'source_path': 'consumption_daily.consumption_by_date', 'raw_value': f'ACUs filtered for {current_month_prefix}'}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['ACUs Mes Anterior'] = {
                'value': round(prev_month_acus, 2),
                'raw_data_value': round(prev_month_acus, 2),
                'formula': f'Python Function (calculate_monthly_acus) filtering data for {previous_month_prefix}',
                'sources_used': [
                    {'source_path': 'consumption_daily.consumption_by_date', 'raw_value': f'ACUs filtered for {previous_month_prefix}'}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['Coste Mes'] = {
                'value': current_month_cost,
                'raw_data_value': round(current_month_acus, 2),
                'formula': f'Python Function (calculate_cost_from_acus): ACUs Mes × {price_per_acu} (COST_PER_ACU)',
                'sources_used': [
                    {'source_path': 'Python Function (calculate_monthly_acus)', 'raw_value': round(current_month_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['Coste Mes Anterior'] = {
                'value': prev_month_cost,
                'raw_data_value': round(prev_month_acus, 2),
                'formula': f'Python Function (calculate_cost_from_acus): ACUs Mes Anterior × {price_per_acu} (COST_PER_ACU)',
                'sources_used': [
                    {'source_path': 'Python Function (calculate_monthly_acus)', 'raw_value': round(prev_month_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['Diferencia (Mes Actual - Mes Anterior)'] = {
                'value': round(absolute_difference, 2),
                'raw_data_value': 'N/A',
                'formula': f'Coste Mes - Coste Mes Anterior',
                'sources_used': [
                    {'source_path': 'Calculated from Coste Mes', 'raw_value': current_month_cost},
                    {'source_path': 'Calculated from Coste Mes Anterior', 'raw_value': prev_month_cost}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['Variacion Porcentual'] = {
                'value': percentage_value,
                'raw_data_value': 'N/A',
                'formula': percentage_formula,
                'sources_used': [
                    {'source_path': 'Calculated from Coste Mes', 'raw_value': current_month_cost},
                    {'source_path': 'Calculated from Coste Mes Anterior', 'raw_value': prev_month_cost}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['ACUs consumidos totales'] = {
                'value': round(total_acus, 2),
                'raw_data_value': round(total_acus, 2),
                'formula': 'Direct extraction from JSON',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            total_cost_calculated = calculate_cost_from_acus(total_acus, price_per_acu)
            all_metrics['Coste consumo total'] = {
                'value': round(total_cost_calculated, 2),
                'raw_data_value': round(total_acus, 2),
                'formula': f'Python Function (calculate_cost_from_acus): Total ACUs × {price_per_acu} (COST_PER_ACU)',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['Numero de usuarios'] = {
                'value': user_count,
                'raw_data_value': user_count,
                'formula': 'Direct extraction from JSON',
                'sources_used': [
                    {'source_path': get_source('user_count'), 'raw_value': user_count}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['Numero de sesiones'] = {
                'value': sessions_count,
                'raw_data_value': sessions_count,
                'formula': 'Direct extraction from JSON',
                'sources_used': [
                    {'source_path': get_source('sessions_count'), 'raw_value': sessions_count}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if user_count > 0:
                media_total_acus_usuario = round(total_acus / user_count, 2)
            else:
                media_total_acus_usuario = 0.00
            
            all_metrics['Media total ACUs por usuario'] = {
                'value': media_total_acus_usuario,
                'raw_data_value': 'N/A',
                'formula': f'Total ACUs / Numero de Usuarios',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('user_count'), 'raw_value': user_count}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if user_count > 0:
                media_total_coste_usuario = round(total_cost_calculated / user_count, 2)
            else:
                media_total_coste_usuario = 0.00
            
            all_metrics['Media total Coste por usuario'] = {
                'value': media_total_coste_usuario,
                'raw_data_value': 'N/A',
                'formula': f'Total Cost / Numero de Usuarios',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu},
                    {'source_path': get_source('user_count'), 'raw_value': user_count}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if user_count > 0:
                media_mes_acus_usuario = round(current_month_acus / user_count, 2)
            else:
                media_mes_acus_usuario = 0.00
            
            all_metrics['Media mes ACUs por usuario'] = {
                'value': media_mes_acus_usuario,
                'raw_data_value': 'N/A',
                'formula': f'ACUs Mes / Numero de Usuarios',
                'sources_used': [
                    {'source_path': 'Python Function (calculate_monthly_acus)', 'raw_value': round(current_month_acus, 2)},
                    {'source_path': get_source('user_count'), 'raw_value': user_count}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if user_count > 0:
                media_mes_coste_usuario = round(current_month_cost / user_count, 2)
            else:
                media_mes_coste_usuario = 0.00
            
            all_metrics['Media mes Coste por usuario'] = {
                'value': media_mes_coste_usuario,
                'raw_data_value': 'N/A',
                'formula': f'Coste Mes / Numero de Usuarios',
                'sources_used': [
                    {'source_path': 'Python Function (calculate_monthly_acus)', 'raw_value': round(current_month_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu},
                    {'source_path': get_source('user_count'), 'raw_value': user_count}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if sessions_count > 0:
                media_total_acus_sesion = round(total_acus / sessions_count, 2)
            else:
                media_total_acus_sesion = 0.00
            
            all_metrics['Media Total ACUs por sesion'] = {
                'value': media_total_acus_sesion,
                'raw_data_value': 'N/A',
                'formula': f'Total ACUs / Numero de Sesiones',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('sessions_count'), 'raw_value': sessions_count}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if sessions_count > 0:
                media_total_coste_sesion = round(total_cost_calculated / sessions_count, 2)
            else:
                media_total_coste_sesion = 0.00
            
            all_metrics['Media Total Coste por sesion'] = {
                'value': media_total_coste_sesion,
                'raw_data_value': 'N/A',
                'formula': f'Total Cost / Numero de Sesiones',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu},
                    {'source_path': get_source('sessions_count'), 'raw_value': sessions_count}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            num_organizations = get_value('num_organizations', 0)
            all_metrics['Numero de organizaciones'] = {
                'value': num_organizations,
                'raw_data_value': num_organizations,
                'formula': 'Count keys in consumption_by_org_id dictionary',
                'sources_used': [
                    {'source_path': get_source('num_organizations'), 'raw_value': num_organizations}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if num_organizations > 0:
                coste_por_organizacion = round(total_cost_calculated / num_organizations, 2)
            else:
                coste_por_organizacion = 0.00
            
            all_metrics['Coste por organizacion'] = {
                'value': coste_por_organizacion,
                'raw_data_value': 'N/A',
                'formula': f'Coste Total / Numero de Organizaciones',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu},
                    {'source_path': get_source('num_organizations'), 'raw_value': num_organizations}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if num_organizations > 0:
                coste_por_grupo = round(total_cost_calculated / num_organizations, 2)
            else:
                coste_por_grupo = 0.00
            
            all_metrics['Coste por grupo (IdP)'] = {
                'value': coste_por_grupo,
                'raw_data_value': 'N/A',
                'formula': f'Coste Total / Numero de Organizaciones',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu},
                    {'source_path': get_source('num_organizations'), 'raw_value': num_organizations}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            prs_opened = get_value('prs_opened', 0)
            prs_closed = get_value('prs_closed', 0)
            prs_merged = get_value('prs_merged', 0)
            prs_totales = prs_opened + prs_closed + prs_merged
            
            all_metrics['Numero de PRs abiertos'] = {
                'value': prs_opened,
                'raw_data_value': prs_opened,
                'formula': 'Direct extraction from JSON',
                'sources_used': [
                    {'source_path': get_source('prs_opened'), 'raw_value': prs_opened}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['Numero de PRs cerradas'] = {
                'value': prs_closed,
                'raw_data_value': prs_closed,
                'formula': 'Direct extraction from JSON',
                'sources_used': [
                    {'source_path': get_source('prs_closed'), 'raw_value': prs_closed}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['Numero de PRs mergeadas'] = {
                'value': prs_merged,
                'raw_data_value': prs_merged,
                'formula': 'Direct extraction from JSON',
                'sources_used': [
                    {'source_path': get_source('prs_merged'), 'raw_value': prs_merged}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            all_metrics['Numero de PRs totales'] = {
                'value': prs_totales,
                'raw_data_value': 'N/A',
                'formula': 'Sum of PRs abiertos + PRs cerradas + PRs mergeadas',
                'sources_used': [
                    {'source_path': get_source('prs_opened'), 'raw_value': prs_opened},
                    {'source_path': get_source('prs_closed'), 'raw_value': prs_closed},
                    {'source_path': get_source('prs_merged'), 'raw_value': prs_merged}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if prs_totales > 0:
                acus_por_pr_totales = round(total_acus / prs_totales, 2)
            else:
                acus_por_pr_totales = 0.00
            
            all_metrics['ACUs por PR totales'] = {
                'value': acus_por_pr_totales,
                'raw_data_value': 'N/A',
                'formula': f'Total ACUs / PRs totales',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': 'Calculated (prs_opened + prs_closed + prs_merged)', 'raw_value': prs_totales}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if prs_totales > 0:
                coste_por_pr_totales = round(total_cost_calculated / prs_totales, 2)
            else:
                coste_por_pr_totales = 0.00
            
            all_metrics['Coste por PR totales'] = {
                'value': coste_por_pr_totales,
                'raw_data_value': 'N/A',
                'formula': f'Coste Total / PRs totales',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu},
                    {'source_path': 'Calculated (prs_opened + prs_closed + prs_merged)', 'raw_value': prs_totales}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if prs_merged > 0:
                acus_medio_por_pr_mergeado = round(total_acus / prs_merged, 2)
            else:
                acus_medio_por_pr_mergeado = 0.00
            
            all_metrics['ACUs medio por PR mergeado'] = {
                'value': acus_medio_por_pr_mergeado,
                'raw_data_value': 'N/A',
                'formula': f'Total ACUs / PRs mergeadas',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('prs_merged'), 'raw_value': prs_merged}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if prs_merged > 0:
                coste_medio_por_pr_mergeado = round(total_cost_calculated / prs_merged, 2)
            else:
                coste_medio_por_pr_mergeado = 0.00
            
            all_metrics['Coste medio por PR mergeado'] = {
                'value': coste_medio_por_pr_mergeado,
                'raw_data_value': 'N/A',
                'formula': f'Coste Total / PRs mergeadas',
                'sources_used': [
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)},
                    {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu},
                    {'source_path': get_source('prs_merged'), 'raw_value': prs_merged}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if total_acus > 0:
                promedio_prs_por_acu = round(prs_totales / total_acus, 2)
            else:
                promedio_prs_por_acu = 0.00
            
            all_metrics['Promedio de PRs por ACU'] = {
                'value': promedio_prs_por_acu,
                'raw_data_value': 'N/A',
                'formula': f'PRs totales / Total ACUs',
                'sources_used': [
                    {'source_path': 'Calculated (prs_opened + prs_closed + prs_merged)', 'raw_value': prs_totales},
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if total_acus > 0:
                promedio_prs_mergeados_por_acu = round(prs_merged / total_acus, 2)
            else:
                promedio_prs_mergeados_por_acu = 0.00
            
            all_metrics['Promedio de PRs mergeados por ACU'] = {
                'value': promedio_prs_mergeados_por_acu,
                'raw_data_value': 'N/A',
                'formula': f'PRs mergeadas / Total ACUs',
                'sources_used': [
                    {'source_path': get_source('prs_merged'), 'raw_value': prs_merged},
                    {'source_path': 'consumption_daily.response.total_acus', 'raw_value': round(total_acus, 2)}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
            if prs_totales > 0:
                tasa_exito_pr = round((prs_merged / prs_totales) * 100, 2)
            else:
                tasa_exito_pr = 0.00
            
            all_metrics['Tasa de exito de PR'] = {
                'value': tasa_exito_pr,
                'raw_data_value': 'N/A',
                'formula': f'(PRs mergeadas / PRs totales) * 100',
                'sources_used': [
                    {'source_path': get_source('prs_merged'), 'raw_value': prs_merged},
                    {'source_path': 'Calculated (prs_opened + prs_closed + prs_merged)', 'raw_value': prs_totales}
                ],
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            
        except Exception as e:
            logger.warning(f"Failed to calculate monthly cost comparison: {e}")
            all_metrics['ACUs Mes'] = {
                'value': 'N/A',
                'reason': f'Failed to calculate: {str(e)}',
                'external_data_required': 'Valid consumption_by_date',
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            all_metrics['ACUs Mes Anterior'] = {
                'value': 'N/A',
                'reason': f'Failed to calculate: {str(e)}',
                'external_data_required': 'Valid consumption_by_date',
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            all_metrics['Coste Mes'] = {
                'value': 'N/A',
                'reason': f'Failed to calculate: {str(e)}',
                'external_data_required': 'Valid consumption_by_date',
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
            all_metrics['Coste Mes Anterior'] = {
                'value': 'N/A',
                'reason': f'Failed to calculate: {str(e)}',
                'external_data_required': 'Valid consumption_by_date',
                'category': 'COST VISIBILITY AND TRANSPARENCY'
            }
    
    
    all_metrics['Costo Total Mensual'] = {
        'value': total_cost,
        'formula': 'Total ACUs * Price per ACU',
        'sources_used': [
            {'source_path': get_source('total_acus'), 'raw_value': total_acus},
            {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu}
        ],
        'category': 'COST VISIBILITY AND TRANSPARENCY'
    }
    
    all_metrics['ACUs Totales Consumidos'] = {
        'value': total_acus,
        'formula': 'Direct value from API',
        'sources_used': [
            {'source_path': get_source('total_acus'), 'raw_value': total_acus}
        ],
        'category': 'COST VISIBILITY AND TRANSPARENCY'
    }
    
    all_metrics['ACUs por Sesion'] = {
        'value': acus_per_session,
        'formula': 'Total ACUs / Total Sessions',
        'sources_used': [
            {'source_path': get_source('total_acus'), 'raw_value': total_acus},
            {'source_path': get_source('sessions_count'), 'raw_value': sessions_count}
        ],
        'category': 'COST VISIBILITY AND TRANSPARENCY'
    }
    
    all_metrics['ACUs por Usuario'] = {
        'value': 'See detailed breakdown',
        'formula': 'ACUs per User from consumption_by_user',
        'sources_used': [
            {'source_path': get_source('consumption_by_user'), 'raw_value': f'{len(consumption_by_user)} users'}
        ],
        'category': 'COST VISIBILITY AND TRANSPARENCY'
    }
    
    all_metrics['Costo por Usuario'] = {
        'value': 'See detailed breakdown',
        'formula': 'ACUs per User * Price per ACU',
        'sources_used': [
            {'source_path': get_source('consumption_by_user'), 'raw_value': f'{len(consumption_by_user)} users'},
            {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu}
        ],
        'category': 'COST VISIBILITY AND TRANSPARENCY'
    }
    
    all_metrics['ACUs promedio por PR mergeado'] = {
        'value': acus_per_pr,
        'formula': 'Total ACUs / Total Merged PRs',
        'sources_used': [
            {'source_path': get_source('total_acus'), 'raw_value': total_acus},
            {'source_path': get_source('prs_merged'), 'raw_value': prs_merged}
        ],
        'category': 'COST OPTIMIZATION'
    }
    
    all_metrics['Promedio de PRs por ACU'] = {
        'value': prs_per_acu,
        'formula': 'Total Merged PRs / Total ACUs',
        'sources_used': [
            {'source_path': get_source('prs_merged'), 'raw_value': prs_merged},
            {'source_path': get_source('total_acus'), 'raw_value': total_acus}
        ],
        'category': 'COST OPTIMIZATION'
    }
    
    all_metrics['Costo por PR mergeado'] = {
        'value': cost_per_pr,
        'formula': 'Total Cost / Total Merged PRs',
        'sources_used': [
            {'source_path': get_source('total_acus'), 'raw_value': total_acus},
            {'source_path': get_source('price_per_acu'), 'raw_value': price_per_acu},
            {'source_path': get_source('prs_merged'), 'raw_value': prs_merged}
        ],
        'category': 'COST OPTIMIZATION'
    }
    
    all_metrics['ACUs por mes'] = {
        'value': total_acus,
        'formula': 'Total ACUs for the reporting period',
        'sources_used': [
            {'source_path': get_source('total_acus'), 'raw_value': total_acus}
        ],
        'category': 'FORECAST'
    }
    
    return all_metrics
