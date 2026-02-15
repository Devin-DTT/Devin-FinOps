"""
Metrics Service Module for FinOps Report Generation.

Backward-compatibility wrapper. All logic has been consolidated into
metrics_engine.py with a three-layer hierarchy:
  Layer 1 (Base Metrics)    -> extract_base_metrics
  Layer 2 (Derived Metrics) -> MetricsCalculator
  Layer 3 (Business Metrics) -> calculate_finops_metrics
"""

from metrics_engine import (  # noqa: F401
    calculate_monthly_acus_from_daily,
    calculate_cost_from_acus,
    extract_base_metrics,
    calculate_finops_metrics,
)

calculate_monthly_acus = calculate_monthly_acus_from_daily
calculate_base_metrics = extract_base_metrics
