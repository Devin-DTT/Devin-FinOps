"""
Export module for FinOps metrics data.
Provides functionality to export daily consumption data to CSV format.
"""

import csv
import json
import logging
from typing import List, Dict, Any
from datetime import datetime

logger = logging.getLogger(__name__)


def export_daily_acus_to_csv(
    raw_data: List[Dict[str, Any]] = None,
    raw_data_file: str = 'raw_usage_data.json',
    output_filename: str = 'daily_consumption_data.csv'
) -> None:
    """
    Export daily ACU consumption data to CSV format.
    
    Args:
        raw_data: In-memory list of usage records (preferred over file)
        raw_data_file: Fallback path to raw usage data JSON file
        output_filename: Output CSV filename
    """
    logger.info(f"Exporting daily consumption data to {output_filename}...")
    
    try:
        if raw_data is None:
            with open(raw_data_file, 'r') as f:
                raw_data = json.load(f)
        
        if not raw_data:
            logger.warning("No consumption data available for CSV export")
            return
        
        rows = []
        for record in raw_data:
            timestamp = record.get('timestamp', '')
            acu_consumed = record.get('acu_consumed', 0)
            
            date = timestamp.split('T')[0] if 'T' in timestamp else timestamp
            
            rows.append({
                'Date': date,
                'ACUs_Consumed': acu_consumed
            })
        
        with open(output_filename, 'w', newline='') as csvfile:
            fieldnames = ['Date', 'ACUs_Consumed']
            writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
            
            writer.writeheader()
            writer.writerows(rows)
        
        logger.info(f"Successfully exported {len(rows)} records to {output_filename}")
        print(f"\n+ Daily consumption data exported to {output_filename} ({len(rows)} records)")
        
    except FileNotFoundError:
        logger.error(f"Raw data file not found: {raw_data_file}")
        print(f"\n- Error: {raw_data_file} not found")
    except Exception as e:
        logger.error(f"Failed to export consumption data: {e}", exc_info=True)
        print(f"\n- Error exporting consumption data: {e}")
