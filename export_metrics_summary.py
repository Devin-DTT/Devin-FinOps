"""
Export module for FinOps summary metrics to Excel format.
Provides functionality to export business summary data to Excel with professional formatting.
"""

import logging
import json
from typing import Dict, Any
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, PatternFill
from datetime import datetime

logger = logging.getLogger(__name__)


def export_summary_to_excel(
    consumption_data: Dict[str, Any],
    config: Any,
    all_api_data: Dict[str, Dict[str, Any]] = None,
    output_filename: str = 'finops_summary_report.xlsx',
    summary_data: Dict[str, Any] = None,
    user_breakdown_list: list = None,
    org_breakdown_summary: dict = None,
    finops_metrics: Dict[str, Dict[str, Any]] = None
) -> None:
    """
    Export FinOps business summary data to Excel format with professional formatting.
    
    Args:
        consumption_data: Dictionary containing metrics and reporting_period
        config: MetricsConfig object with pricing information
        output_filename: Output Excel filename
        user_breakdown_list: List of user consumption breakdown dictionaries
        org_breakdown_summary: Dictionary of organization aggregation data
        finops_metrics: Dictionary containing FinOps metrics with traceability information
    """
    logger.info(f"Exporting summary data to {output_filename}...")
    
    try:
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
                        
                        logger.info(f"Extracted metrics from consumption_daily for Excel: {total_acus} ACUs, {total_sessions} records, {unique_users} users")
                except (json.JSONDecodeError, AttributeError, TypeError) as e:
                    logger.warning(f"Failed to extract consumption_daily metrics for Excel: {e}")
        
        if total_acus == 0:
            total_sessions = metrics.get('06_total_sessions', 0)
            total_acus = metrics.get('02_total_acus', 0)
            total_cost = metrics.get('01_total_monthly_cost', 0)
            unique_users = metrics.get('12_unique_users', 0)
        
        start_date = reporting_period.get('start_date', 'N/A')
        end_date = reporting_period.get('end_date', 'N/A')
        
        num_days = 1
        if start_date != 'N/A' and end_date != 'N/A':
            try:
                start = datetime.strptime(start_date, '%Y-%m-%d')
                end = datetime.strptime(end_date, '%Y-%m-%d')
                num_days = max(1, (end - start).days + 1)
            except:
                num_days = 1
        
        average_acus_per_day = total_acus / num_days if num_days > 0 else 0
        
        wb = Workbook()
        ws = wb.active
        ws.title = "FinOps Summary"
        
        header_font = Font(bold=True, size=12)
        header_fill = PatternFill(start_color="366092", end_color="366092", fill_type="solid")
        header_alignment = Alignment(horizontal="center", vertical="center")
        
        title_font = Font(bold=True, size=14)
        title_fill = PatternFill(start_color="D9E1F2", end_color="D9E1F2", fill_type="solid")
        
        ws.column_dimensions['A'].width = 40
        ws.column_dimensions['B'].width = 20
        ws.column_dimensions['C'].width = 30
        ws.column_dimensions['D'].width = 50
        ws.column_dimensions['E'].width = 25
        ws.column_dimensions['F'].width = 50
        
        ws['A1'] = "FINOPS BUSINESS SUMMARY REPORT"
        ws['A1'].font = title_font
        ws['A1'].fill = title_fill
        ws['A1'].alignment = Alignment(horizontal="center")
        ws.merge_cells('A1:F1')
        
        row = 3
        
        if finops_metrics:
            ws[f'A{row}'] = "KEY FINOPS METRICS WITH TRACEABILITY"
            ws[f'A{row}'].font = Font(bold=True, size=12)
            ws[f'A{row}'].fill = PatternFill(start_color="4472C4", end_color="4472C4", fill_type="solid")
            ws[f'A{row}'].alignment = Alignment(horizontal="center")
            ws.merge_cells(f'A{row}:F{row}')
            row += 1
            
            ws[f'A{row}'] = "METRICA FINOPS"
            ws[f'B{row}'] = "RESULTADO"
            ws[f'C{row}'] = "FORMULA / LOGICA"
            ws[f'D{row}'] = "FUENTE JSON"
            ws[f'E{row}'] = "DATO EXTRAIDO"
            ws[f'F{row}'] = "DATO EXTERNO PENDIENTE"
            
            for col in [f'A{row}', f'B{row}', f'C{row}', f'D{row}', f'E{row}', f'F{row}']:
                ws[col].font = header_font
                ws[col].fill = header_fill
                ws[col].alignment = header_alignment
            row += 1
            
            categories = [
                'MONTHLY TREND',
                'COST VISIBILITY AND TRANSPARENCY',
                'COST OPTIMIZATION',
                'FINOPS ENABLEMENT',
                'CLOUD GOVERNANCE',
                'FORECAST'
            ]
            
            for category in categories:
                ws[f'A{row}'] = category
                ws[f'A{row}'].font = Font(bold=True, size=11)
                ws[f'A{row}'].fill = PatternFill(start_color="D9E1F2", end_color="D9E1F2", fill_type="solid")
                ws.merge_cells(f'A{row}:F{row}')
                row += 1
                
                category_metrics = {k: v for k, v in finops_metrics.items() if v.get('category') == category}
                
                for metric_name, metric_data in category_metrics.items():
                    ws[f'A{row}'] = metric_name
                    
                    value = metric_data.get('value', 'N/A')
                    if isinstance(value, (int, float)) and value != 'N/A':
                        ws[f'B{row}'] = round(value, 2)
                        ws[f'B{row}'].number_format = '0.00'
                    else:
                        ws[f'B{row}'] = str(value)
                    
                    if 'formula' in metric_data:
                        ws[f'C{row}'] = metric_data.get('formula', '')
                        
                        sources_used = metric_data.get('sources_used', [])
                        if sources_used:
                            source_paths = []
                            raw_values = []
                            for source in sources_used:
                                source_paths.append(source.get('source_path', ''))
                                raw_values.append(str(source.get('raw_value', '')))
                            
                            ws[f'D{row}'] = '\n'.join(source_paths)
                            ws[f'E{row}'] = '\n'.join(raw_values)
                        
                        ws[f'F{row}'] = ''
                    
                    elif 'external_data_required' in metric_data:
                        ws[f'C{row}'] = metric_data.get('reason', '')
                        ws[f'D{row}'] = ''
                        ws[f'E{row}'] = ''
                        ws[f'F{row}'] = metric_data.get('external_data_required', '')
                    
                    ws[f'A{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                    ws[f'C{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                    ws[f'D{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                    ws[f'E{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                    ws[f'F{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                    
                    row += 1
                
                row += 1
            
            logger.info(f"Added Key FinOps Metrics section with {len(finops_metrics)} metrics organized by category")
        
        row += 1
        ws[f'A{row}'] = "ADDITIONAL STATISTICS"
        ws[f'A{row}'].font = Font(bold=True, size=11)
        ws[f'A{row}'].fill = PatternFill(start_color="E7E6E6", end_color="E7E6E6", fill_type="solid")
        ws.merge_cells(f'A{row}:C{row}')
        row += 1
        
        additional_stats = [
            ("Total Daily Consumption Records", total_sessions, "Records"),
            ("Average ACUs Per Day", round(average_acus_per_day, 2), "ACUs"),
            ("Unique Users", unique_users, "Users"),
            ("Reporting Period Start", start_date, "Date"),
            ("Reporting Period End", end_date, "Date"),
            ("Number of Days", num_days, "Days"),
        ]
        
        for metric_name, value, unit in additional_stats:
            ws[f'A{row}'] = metric_name
            ws[f'B{row}'] = value
            ws[f'C{row}'] = unit
            row += 1
        
        if user_breakdown_list:
            logger.info(f"Creating 'Cost by User' sheet with {len(user_breakdown_list)} users...")
            
            ws_user = wb.create_sheet(title="Cost by User")
            
            ws_user.column_dimensions['A'].width = 30
            ws_user.column_dimensions['B'].width = 20
            ws_user.column_dimensions['C'].width = 20
            
            ws_user['A1'] = "User ID"
            ws_user['B1'] = "ACUs Consumed"
            ws_user['C1'] = "Cost (USD)"
            
            for col in ['A1', 'B1', 'C1']:
                ws_user[col].font = header_font
                ws_user[col].fill = header_fill
                ws_user[col].alignment = header_alignment
            
            row = 2
            for user_data in user_breakdown_list:
                ws_user[f'A{row}'] = user_data.get('User ID', '')
                ws_user[f'B{row}'] = user_data.get('ACUs Consumed', 0)
                ws_user[f'C{row}'] = user_data.get('Cost (USD)', 0)
                
                ws_user[f'B{row}'].number_format = '0.00'
                ws_user[f'C{row}'].number_format = '0.00'
                
                row += 1
            
            logger.info(f"'Cost by User' sheet created with {len(user_breakdown_list)} user records")
        else:
            logger.warning("No user breakdown data provided, skipping 'Cost by User' sheet creation")
        
        if org_breakdown_summary:
            logger.info(f"Creating 'Cost by Organization' sheet with {len(org_breakdown_summary)} organizations...")
            
            ws_org = wb.create_sheet(title="Cost by Organization")
            
            ws_org.column_dimensions['A'].width = 30
            ws_org.column_dimensions['B'].width = 25
            ws_org.column_dimensions['C'].width = 20
            
            ws_org['A1'] = "Organization ID"
            ws_org['B1'] = "Total ACUs Consumed"
            ws_org['C1'] = "Total Cost (USD)"
            
            for col in ['A1', 'B1', 'C1']:
                ws_org[col].font = header_font
                ws_org[col].fill = header_fill
                ws_org[col].alignment = header_alignment
            
            row = 2
            for org_id, org_data in org_breakdown_summary.items():
                ws_org[f'A{row}'] = org_data.get('Organization ID', org_id)
                ws_org[f'B{row}'] = org_data.get('Total ACUs Consumed', 0)
                ws_org[f'C{row}'] = org_data.get('Total Cost (USD)', 0)
                
                ws_org[f'B{row}'].number_format = '0.00'
                ws_org[f'C{row}'].number_format = '0.00'
                
                row += 1
            
            logger.info(f"'Cost by Organization' sheet created with {len(org_breakdown_summary)} organization records")
        else:
            logger.warning("No organization breakdown data provided, skipping 'Cost by Organization' sheet creation")
        
        wb.save(output_filename)
        
        logger.info(f"Successfully exported summary to {output_filename}")
        print(f"\n+ Summary data exported to {output_filename}")
        
    except Exception as e:
        logger.error(f"Failed to export summary to Excel: {e}", exc_info=True)
        print(f"\n- Error exporting summary to Excel: {e}")
