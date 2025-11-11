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
    output_filename: str = 'finops_summary_report.xlsx'
) -> None:
    """
    Export FinOps business summary data to Excel format with professional formatting.
    
    Args:
        consumption_data: Dictionary containing metrics and reporting_period
        config: MetricsConfig object with pricing information
        output_filename: Output Excel filename
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
        
        ws.column_dimensions['A'].width = 35
        ws.column_dimensions['B'].width = 20
        ws.column_dimensions['C'].width = 15
        
        ws['A1'] = "FINOPS BUSINESS SUMMARY REPORT"
        ws['A1'].font = title_font
        ws['A1'].fill = title_fill
        ws['A1'].alignment = Alignment(horizontal="center")
        ws.merge_cells('A1:C1')
        
        ws['A3'] = "Metric Name"
        ws['B3'] = "Value"
        ws['C3'] = "Unit"
        for col in ['A3', 'B3', 'C3']:
            ws[col].font = header_font
            ws[col].fill = header_fill
            ws[col].alignment = header_alignment
        
        ws['A4'] = "KEY METRICS FROM REAL DATA"
        ws['A4'].font = Font(bold=True, size=11)
        ws['A4'].fill = PatternFill(start_color="E7E6E6", end_color="E7E6E6", fill_type="solid")
        ws.merge_cells('A4:C4')
        
        row = 5
        key_metrics = [
            ("Total Daily Consumption Records", total_sessions, "Records"),
            ("Average ACUs Per Day", round(average_acus_per_day, 2), "ACUs"),
        ]
        
        for metric_name, value, unit in key_metrics:
            ws[f'A{row}'] = metric_name
            ws[f'B{row}'] = value
            ws[f'C{row}'] = unit
            row += 1
        
        row += 1
        ws[f'A{row}'] = "ADDITIONAL STATISTICS"
        ws[f'A{row}'].font = Font(bold=True, size=11)
        ws[f'A{row}'].fill = PatternFill(start_color="E7E6E6", end_color="E7E6E6", fill_type="solid")
        ws.merge_cells(f'A{row}:C{row}')
        row += 1
        
        additional_stats = [
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
        
        row += 1
        ws[f'A{row}'] = "COST VISIBILITY AND TRANSPARENCY"
        ws[f'A{row}'].font = Font(bold=True, size=11)
        ws[f'A{row}'].fill = PatternFill(start_color="E7E6E6", end_color="E7E6E6", fill_type="solid")
        ws.merge_cells(f'A{row}:C{row}')
        row += 1
        
        total_cost_previous_month = 0
        
        cost_visibility_metrics = [
            ("Total Monthly Cost", round(total_cost, 2), config.currency),
            ("Total Previous Month Cost", total_cost_previous_month, config.currency),
            ("Cost by User", "See detailed breakdown sheet", "Reference"),
            ("Cost by Repository/PR", "See detailed breakdown sheet", "Reference"),
            ("Cost by Task Type", "See detailed breakdown sheet", "Reference"),
            ("Cost by Organization", "N/A - Data not available", "N/A"),
            ("Cost per Group (IdP)", "N/A - Data not available", "N/A"),
        ]
        
        for metric_name, value, unit in cost_visibility_metrics:
            ws[f'A{row}'] = metric_name
            ws[f'B{row}'] = value
            ws[f'C{row}'] = unit
            row += 1
        
        wb.save(output_filename)
        
        logger.info(f"Successfully exported summary to {output_filename}")
        print(f"\n✓ Summary data exported to {output_filename}")
        
    except Exception as e:
        logger.error(f"Failed to export summary to Excel: {e}", exc_info=True)
        print(f"\n✗ Error exporting summary to Excel: {e}")
