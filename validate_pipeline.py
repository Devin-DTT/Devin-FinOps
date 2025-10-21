"""
Pipeline Validation Script - Phase 4
End-to-End Validation and Data Integrity Checks

This script validates the final FinOps metrics report (finops_metrics_report.csv)
by performing structural and financial integrity checks.
"""

import pandas as pd
import logging
from typing import Tuple

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def validate_csv_structure(df: pd.DataFrame) -> bool:
    """
    Validate that the CSV has the correct structure.

    Args:
        df: DataFrame loaded from finops_metrics_report.csv

    Returns:
        True if structure is valid

    Raises:
        ValueError: If structure validation fails
    """
    expected_columns = ['metric_name', 'value', 'unit']

    if list(df.columns) != expected_columns:
        raise ValueError(
            f"CSV columns mismatch. Expected {expected_columns}, "
            f"got {list(df.columns)}"
        )

    logger.info("✓ CSV structure validation passed: 3 columns (metric_name, value, unit)")
    return True


def validate_metric_types(df: pd.DataFrame) -> bool:
    """
    Validate that exactly 20 unique metric types exist.

    Args:
        df: DataFrame loaded from finops_metrics_report.csv

    Returns:
        True if metric types validation passes

    Raises:
        ValueError: If metric type count is incorrect
    """
    base_metric_names = df['metric_name'].str.split(' - ').str[0].unique()

    expected_count = 20
    actual_count = len(base_metric_names)

    if actual_count != expected_count:
        raise ValueError(
            f"Metric type count mismatch. Expected {expected_count} unique metric types, "
            f"found {actual_count}"
        )

    logger.info(f"✓ Metric types validation passed: {actual_count} unique metric types confirmed")
    return True


def validate_financial_integrity(df: pd.DataFrame) -> bool:
    """
    Validate financial integrity of the report.

    Checks:
    1. Total Monthly Cost = Sum of all Cost Per User entries
    2. Total Monthly Cost = Sum of all Cost By Task Type entries
    3. Total Monthly Cost = Sum of all Cost By Department entries
    4. All cost values are non-negative

    Args:
        df: DataFrame loaded from finops_metrics_report.csv

    Returns:
        True if all financial checks pass

    Raises:
        ValueError: If any financial integrity check fails
    """
    tolerance = 0.01

    total_cost_row = df[df['metric_name'] == 'Total Monthly Cost']
    if total_cost_row.empty:
        raise ValueError("Total Monthly Cost metric not found")

    total_monthly_cost = float(total_cost_row['value'].iloc[0])
    logger.info(f"Total Monthly Cost: ${total_monthly_cost:.2f}")

    cost_per_user_rows = df[df['metric_name'].str.startswith('Cost Per User - ')]
    sum_cost_per_user = cost_per_user_rows['value'].astype(float).sum()

    if abs(sum_cost_per_user - total_monthly_cost) > tolerance:
        raise ValueError(
            f"Cost Per User sum mismatch. "
            f"Sum: ${sum_cost_per_user:.2f}, Expected: ${total_monthly_cost:.2f}"
        )
    logger.info(f"✓ Cost Per User sum validation passed: ${sum_cost_per_user:.2f}")

    cost_by_task_rows = df[df['metric_name'].str.startswith('Cost By Task Type - ')]
    sum_cost_by_task = cost_by_task_rows['value'].astype(float).sum()

    if abs(sum_cost_by_task - total_monthly_cost) > tolerance:
        raise ValueError(
            f"Cost By Task Type sum mismatch. "
            f"Sum: ${sum_cost_by_task:.2f}, Expected: ${total_monthly_cost:.2f}"
        )
    logger.info(f"✓ Cost By Task Type sum validation passed: ${sum_cost_by_task:.2f}")

    cost_by_dept_rows = df[df['metric_name'].str.startswith('Cost By Department - ')]
    sum_cost_by_dept = cost_by_dept_rows['value'].astype(float).sum()

    if abs(sum_cost_by_dept - total_monthly_cost) > tolerance:
        raise ValueError(
            f"Cost By Department sum mismatch. "
            f"Sum: ${sum_cost_by_dept:.2f}, Expected: ${total_monthly_cost:.2f}"
        )
    logger.info(f"✓ Cost By Department sum validation passed: ${sum_cost_by_dept:.2f}")

    usd_rows = df[df['unit'] == 'USD']
    negative_costs = usd_rows[usd_rows['value'].astype(float) < 0]

    if not negative_costs.empty:
        raise ValueError(
            f"Found {len(negative_costs)} negative cost values. "
            f"All costs must be >= $0"
        )
    logger.info(f"✓ Non-negative cost validation passed: All {len(usd_rows)} USD values >= $0")

    return True


def main():
    """Main validation function."""
    logger.info("=" * 60)
    logger.info("FinOps Pipeline Validation - Phase 4")
    logger.info("=" * 60)

    report_file = 'finops_metrics_report.csv'

    try:
        logger.info(f"Loading report from {report_file}...")
        df = pd.read_csv(report_file)
        logger.info(f"Loaded {len(df)} rows from report")

        validate_csv_structure(df)
        validate_metric_types(df)
        validate_financial_integrity(df)

        logger.info("\n" + "=" * 60)
        print("Pipeline validation SUCCESSFUL. Financial integrity passed.")
        logger.info("=" * 60)

        return 0

    except Exception as e:
        logger.error(f"Validation FAILED: {e}")
        return 1


if __name__ == '__main__':
    exit(main())
