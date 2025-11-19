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
    Export FinOps business summary data to Excel format with modular structure.
    Focus on "Visibilidad y transparencia de costes" section with four key metrics.
    
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
        wb = Workbook()
        ws = wb.active
        ws.title = "FinOps Report"
        
        header_font = Font(bold=True, size=12, color="FFFFFF")
        header_fill = PatternFill(start_color="366092", end_color="366092", fill_type="solid")
        header_alignment = Alignment(horizontal="center", vertical="center")
        
        section_font = Font(bold=True, size=13)
        section_fill = PatternFill(start_color="D9E1F2", end_color="D9E1F2", fill_type="solid")
        
        ws.column_dimensions['A'].width = 35
        ws.column_dimensions['B'].width = 20
        ws.column_dimensions['C'].width = 50
        ws.column_dimensions['D'].width = 50
        ws.column_dimensions['E'].width = 25
        
        row = 1
        
        ws[f'A{row}'] = "Visibilidad y transparencia de costes"
        ws[f'A{row}'].font = section_font
        ws[f'A{row}'].fill = section_fill
        ws.merge_cells(f'A{row}:E{row}')
        row += 1
        
        ws[f'A{row}'] = "METRICA FINOPS"
        ws[f'B{row}'] = "RESULTADO"
        ws[f'C{row}'] = "FORMULA / LOGICA"
        ws[f'D{row}'] = "FUENTE JSON"
        ws[f'E{row}'] = "DATO EXTRAÍDO"
        
        for col in [f'A{row}', f'B{row}', f'C{row}', f'D{row}', f'E{row}']:
            ws[col].font = header_font
            ws[col].fill = header_fill
            ws[col].alignment = header_alignment
        row += 1
        
        if finops_metrics:
            target_metrics = [
                'ACUs Mes', 
                'ACUs Mes Anterior', 
                'Coste Mes', 
                'Coste Mes Anterior', 
                'ACUs consumidos totales', 
                'Coste consumo total',
                'Numero de usuarios',
                'Numero de sesiones',
                'Media total ACUs por usuario',
                'Media total Coste por usuario',
                'Media mes ACUs por usuario',
                'Media mes Coste por usuario',
                'Media Total ACUs por sesion',
                'Media Total Coste por sesion'
            ]
            
            for metric_name in target_metrics:
                if metric_name in finops_metrics:
                    metric_data = finops_metrics[metric_name]
                    
                    ws[f'A{row}'] = metric_name
                    ws[f'A{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                    
                    value = metric_data.get('value', 'N/A')
                    if isinstance(value, (int, float)) and value != 'N/A':
                        ws[f'B{row}'] = round(value, 2)
                        ws[f'B{row}'].number_format = '0.00'
                    else:
                        ws[f'B{row}'] = str(value)
                    ws[f'B{row}'].alignment = Alignment(horizontal="right", vertical="top")
                    
                    if 'formula' in metric_data:
                        ws[f'C{row}'] = metric_data.get('formula', '')
                        ws[f'C{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                        
                        sources_used = metric_data.get('sources_used', [])
                        if sources_used:
                            source_paths = []
                            for source in sources_used:
                                source_path = source.get('source_path', '')
                                source_paths.append(source_path)
                            
                            ws[f'D{row}'] = '\n'.join(source_paths)
                        else:
                            ws[f'D{row}'] = ''
                        ws[f'D{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                        
                        raw_data = metric_data.get('raw_data_value', 'N/A')
                        if isinstance(raw_data, (int, float)) and raw_data != 'N/A':
                            ws[f'E{row}'] = round(raw_data, 2)
                            ws[f'E{row}'].number_format = '0.00'
                        else:
                            ws[f'E{row}'] = str(raw_data)
                        ws[f'E{row}'].alignment = Alignment(horizontal="right", vertical="top")
                    
                    elif 'external_data_required' in metric_data:
                        ws[f'C{row}'] = metric_data.get('reason', '')
                        ws[f'C{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                        ws[f'D{row}'] = metric_data.get('external_data_required', '')
                        ws[f'D{row}'].alignment = Alignment(wrap_text=True, vertical="top")
                        ws[f'E{row}'] = 'N/A'
                        ws[f'E{row}'].alignment = Alignment(horizontal="right", vertical="top")
                    
                    row += 1
            
            logger.info(f"Added 'Visibilidad y transparencia de costes' section with 4 metrics")
        else:
            logger.warning("No finops_metrics provided, cannot populate Excel report")
        
        wb.save(output_filename)
        
        logger.info(f"Successfully exported summary to {output_filename}")
        print(f"\n+ Summary data exported to {output_filename}")
        
    except Exception as e:
        logger.error(f"Failed to export summary to Excel: {e}", exc_info=True)
        print(f"\n- Error exporting summary to Excel: {e}")
