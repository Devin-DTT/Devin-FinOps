# Devin Metrics Calculator - Phase 2

A modular Python module for calculating foundational metrics from Devin usage data.

## Overview

This module processes raw usage data and calculates 20 foundational metrics for Devin usage analysis, including cost analysis, resource utilization, and efficiency metrics.

## Features

- **Modular Design**: Clean separation of concerns with dedicated modules
- **Configurable Cost Rates**: Inject custom cost rates via configuration
- **Comprehensive Metrics**: Calculates 20 foundational metrics
- **Well-Tested**: Includes comprehensive unit tests
- **Type-Safe**: Uses type hints for better code quality

## Project Structure

```
devin-metrics-calculator/
├── config.py                  # Configuration module for cost rates
├── metrics_calculator.py      # Main calculator module
├── test_metrics_calculator.py # Comprehensive unit tests
├── raw_usage_data.json       # Sample usage data
├── README.md                 # This file
└── requirements.txt          # Python dependencies
```

## Installation

No external dependencies required - uses only Python standard library.

```bash
# Clone or download this repository
cd devin-metrics-calculator

# Run tests
python -m unittest test_metrics_calculator.py
```

## Usage

### Basic Usage

```python
from metrics_calculator import MetricsCalculator
from config import MetricsConfig

# Create configuration with custom cost rates
config = MetricsConfig(price_per_acu=0.05, currency='USD')

# Initialize calculator
calculator = MetricsCalculator(config)

# Load data
calculator.load_data('raw_usage_data.json')

# Calculate all metrics
all_metrics = calculator.calculate_all_metrics()

# Or calculate individual metrics
total_cost = calculator.calculate_total_monthly_cost()
total_acus = calculator.calculate_total_acus()
```

### Custom Configuration

```python
# Custom configuration with different cost rates
custom_config = MetricsConfig(
    price_per_acu=0.08,
    currency='EUR',
    working_hours_per_day=8,
    working_days_per_month=22
)

calculator = MetricsCalculator(custom_config)
```

## 20 Foundational Metrics

1. **Total Monthly Cost** - Total cost for the reporting period
2. **Total ACUs** - Total ACUs consumed across all sessions
3. **Cost Per User** - Cost breakdown by user
4. **ACUs Per Session** - ACUs consumed in each session
5. **Average ACUs Per Session** - Mean ACUs per session
6. **Total Sessions** - Total number of sessions
7. **Sessions Per User** - Session count by user
8. **Total Duration Minutes** - Total duration of all sessions
9. **Average Session Duration** - Mean session duration
10. **ACUs Per Minute** - Resource consumption rate
11. **Cost Per Minute** - Cost rate per minute
12. **Unique Users** - Number of unique users
13. **Sessions By Task Type** - Session distribution by task type
14. **ACUs By Task Type** - Resource consumption by task type
15. **Cost By Task Type** - Cost breakdown by task type
16. **Sessions By Department** - Session distribution by department
17. **ACUs By Department** - Resource consumption by department
18. **Cost By Department** - Cost breakdown by department
19. **Average Cost Per User** - Mean cost per user
20. **Efficiency Ratio** - ACUs consumed per hour

## Running Tests

```bash
# Run all tests
python -m unittest test_metrics_calculator.py

# Run with verbose output
python -m unittest test_metrics_calculator.py -v

# Run specific test
python -m unittest test_metrics_calculator.TestMetricsCalculator.test_calculate_total_monthly_cost
```

## Configuration Options

The `MetricsConfig` class accepts the following parameters:

- `price_per_acu` (float): Cost per ACU (default: 0.05)
- `currency` (str): Currency code (default: 'USD')
- `working_hours_per_day` (int): Standard working hours (default: 8)
- `working_days_per_month` (int): Standard working days (default: 22)

## Data Format

The module expects a JSON file with the following structure:

```json
{
  "organization": "Organization Name",
  "reporting_period": {
    "start_date": "YYYY-MM-DD",
    "end_date": "YYYY-MM-DD",
    "month": "Month Year"
  },
  "sessions": [
    {
      "session_id": "string",
      "user_email": "string",
      "duration_minutes": number,
      "acus_consumed": number,
      "task_type": "string",
      "status": "string"
    }
  ],
  "user_details": [
    {
      "user_email": "string",
      "department": "string",
      "role": "string"
    }
  ]
}
```

## License

This is a Phase 2 deliverable for the Devin usage metrics project.
