"""
Metrics Calculator Module for Devin Usage Data Analysis.

Backward-compatibility wrapper. All logic has been consolidated into
metrics_engine.py (Layer 2: Derived Metrics).
"""

from metrics_engine import MetricsCalculator  # noqa: F401


def main():
    """Example usage of the MetricsCalculator."""
    import json
    from config import MetricsConfig

    config = MetricsConfig(price_per_acu=0.05, currency='USD')

    calculator = MetricsCalculator(config)
    calculator.load_data('raw_usage_data.json')

    all_metrics = calculator.calculate_all_metrics()

    print(json.dumps(all_metrics, indent=2, default=str))


if __name__ == '__main__':
    main()
