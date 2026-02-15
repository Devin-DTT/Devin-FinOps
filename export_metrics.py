"""
Export module for FinOps metrics data.
Provides functionality to export daily consumption data to CSV format.
"""

import csv
import json
import logging
from typing import List, Dict, Any
from datetime import datetime
from error_handling import handle_pipeline_phase, ExportError, DataValidationError

logger = logging.getLogger(__name__)


@handle_pipeline_phase(phase_name="EXPORT_CSV", error_cls=ExportError)
def export_daily_acus_to_csv(
    raw_data_file: str = 'raw_usage_data.json',
    output_filename: str = 'daily_consumption_data.csv'
) -> None:
    """
    Export daily ACU consumption data to CSV format.
    
    Args:
        raw_data_file: Path to raw usage data JSON file
        output_filename: Output CSV filename
    
    Raises:
        DataValidationError: If the raw data file cannot be read or parsed.
        ExportError: If CSV export fails.
    """
    logger.info(
        "[EXPORT_CSV] Exporting daily consumption data to %s",
        output_filename,
    )

    try:
        with open(raw_data_file, 'r') as f:
            raw_data = json.load(f)
    except FileNotFoundError as exc:
        raise DataValidationError(
            f"Raw data file not found: {raw_data_file}",
            details={"file_path": raw_data_file},
        ) from exc
    except json.JSONDecodeError as exc:
        raise DataValidationError(
            f"Invalid JSON in raw data file: {raw_data_file}",
            details={"file_path": raw_data_file, "error": str(exc)},
        ) from exc

    if not raw_data:
        logger.warning("[EXPORT_CSV] No consumption data found in %s", raw_data_file)
        return

    if not isinstance(raw_data, list):
        raise DataValidationError(
            f"Expected list in {raw_data_file}, got {type(raw_data).__name__}",
            details={"file_path": raw_data_file, "received_type": type(raw_data).__name__},
        )

    rows = []
    skipped = 0
    for idx, record in enumerate(raw_data):
        try:
            timestamp = record.get('timestamp', '')
            acu_consumed = record.get('acu_consumed', 0)

            if not isinstance(acu_consumed, (int, float)):
                logger.warning(
                    "[EXPORT_CSV] Non-numeric acu_consumed at index %d, defaulting to 0",
                    idx,
                )
                acu_consumed = 0

            date = timestamp.split('T')[0] if 'T' in timestamp else timestamp

            rows.append({
                'Date': date,
                'ACUs_Consumed': acu_consumed
            })
        except Exception as exc:
            skipped += 1
            logger.warning(
                "[EXPORT_CSV] Failed to process record at index %d: %s",
                idx,
                exc,
            )

    if skipped:
        logger.warning("[EXPORT_CSV] Skipped %d records during export", skipped)

    with open(output_filename, 'w', newline='') as csvfile:
        fieldnames = ['Date', 'ACUs_Consumed']
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)

        writer.writeheader()
        writer.writerows(rows)

    logger.info(
        "[EXPORT_CSV] Successfully exported %d records to %s",
        len(rows),
        output_filename,
    )
    print(f"\n+ Daily consumption data exported to {output_filename} ({len(rows)} records)")
