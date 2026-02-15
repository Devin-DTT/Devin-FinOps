"""
Integration tests for the FinOps pipeline.
Verifies end-to-end functionality from data loading through metrics
calculation, report generation, and pipeline validation.
"""

import unittest
import json
import csv
import os
import sys
import tempfile
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from metrics_calculator import MetricsCalculator
from config import MetricsConfig
from generate_report import (
    calculate_monthly_acus,
    calculate_cost_from_acus,
    transform_raw_data,
)
from export_metrics import export_daily_acus_to_csv
from validate_pipeline import (
    validate_csv_structure,
    validate_metric_types,
    validate_financial_integrity,
)


SAMPLE_SESSIONS = [
    {
        "session_id": "int_001",
        "user_email": "alice@company.com",
        "duration_minutes": 60,
        "acus_consumed": 200,
        "task_type": "feature_development",
        "status": "completed"
    },
    {
        "session_id": "int_002",
        "user_email": "bob@company.com",
        "duration_minutes": 120,
        "acus_consumed": 500,
        "task_type": "bug_fix",
        "status": "completed"
    },
    {
        "session_id": "int_003",
        "user_email": "alice@company.com",
        "duration_minutes": 45,
        "acus_consumed": 100,
        "task_type": "code_review",
        "status": "completed"
    },
    {
        "session_id": "int_004",
        "user_email": "carol@company.com",
        "duration_minutes": 90,
        "acus_consumed": 300,
        "task_type": "feature_development",
        "status": "completed"
    },
    {
        "session_id": "int_005",
        "user_email": "bob@company.com",
        "duration_minutes": 30,
        "acus_consumed": 150,
        "task_type": "bug_fix",
        "status": "completed"
    }
]

SAMPLE_USERS = [
    {"user_email": "alice@company.com", "department": "Engineering", "role": "Dev"},
    {"user_email": "bob@company.com", "department": "Engineering", "role": "Dev"},
    {"user_email": "carol@company.com", "department": "Product", "role": "PM"},
]

SAMPLE_DATA = {
    "organization": "IntegrationTestOrg",
    "reporting_period": {
        "start_date": "2024-09-01",
        "end_date": "2024-09-30",
        "month": "September 2024"
    },
    "sessions": SAMPLE_SESSIONS,
    "user_details": SAMPLE_USERS,
}


class TestEndToEndMetricsCalculation(unittest.TestCase):
    """Integration: load data -> calculate all metrics -> validate consistency."""

    def setUp(self):
        self.tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(SAMPLE_DATA, self.tmp)
        self.tmp.close()

        self.config = MetricsConfig(price_per_acu=0.05, currency='USD')
        self.calculator = MetricsCalculator(self.config)
        self.calculator.load_data(self.tmp.name)
        self.all_metrics = self.calculator.calculate_all_metrics()

    def tearDown(self):
        os.unlink(self.tmp.name)

    def test_all_20_metrics_present(self):
        metrics = self.all_metrics['metrics']
        self.assertEqual(len(metrics), 20)

    def test_config_in_output(self):
        config = self.all_metrics['config']
        self.assertEqual(config['price_per_acu'], 0.05)
        self.assertEqual(config['currency'], 'USD')

    def test_reporting_period_preserved(self):
        rp = self.all_metrics['reporting_period']
        self.assertEqual(rp['month'], 'September 2024')

    def test_total_acus_consistency(self):
        m = self.all_metrics['metrics']
        expected_total = sum(s['acus_consumed'] for s in SAMPLE_SESSIONS)
        self.assertEqual(m['02_total_acus'], expected_total)

    def test_total_cost_equals_acus_times_price(self):
        m = self.all_metrics['metrics']
        self.assertAlmostEqual(
            m['01_total_monthly_cost'],
            m['02_total_acus'] * self.config.price_per_acu
        )

    def test_cost_per_user_sums_to_total(self):
        m = self.all_metrics['metrics']
        total_from_users = sum(m['03_cost_per_user'].values())
        self.assertAlmostEqual(total_from_users, m['01_total_monthly_cost'], places=2)

    def test_sessions_per_user_sums_to_total(self):
        m = self.all_metrics['metrics']
        total_from_users = sum(m['07_sessions_per_user'].values())
        self.assertEqual(total_from_users, m['06_total_sessions'])

    def test_acus_by_task_sums_to_total(self):
        m = self.all_metrics['metrics']
        total_from_tasks = sum(m['14_acus_by_task_type'].values())
        self.assertEqual(total_from_tasks, m['02_total_acus'])

    def test_acus_by_department_sums_to_total(self):
        m = self.all_metrics['metrics']
        total_from_depts = sum(m['17_acus_by_department'].values())
        self.assertEqual(total_from_depts, m['02_total_acus'])

    def test_cost_by_task_sums_to_total(self):
        m = self.all_metrics['metrics']
        total_from_tasks = sum(m['15_cost_by_task_type'].values())
        self.assertAlmostEqual(total_from_tasks, m['01_total_monthly_cost'], places=2)

    def test_cost_by_department_sums_to_total(self):
        m = self.all_metrics['metrics']
        total_from_depts = sum(m['18_cost_by_department'].values())
        self.assertAlmostEqual(total_from_depts, m['01_total_monthly_cost'], places=2)

    def test_average_cost_per_user_consistent(self):
        m = self.all_metrics['metrics']
        expected_avg = m['01_total_monthly_cost'] / m['12_unique_users']
        self.assertAlmostEqual(m['19_average_cost_per_user'], expected_avg)

    def test_efficiency_ratio_consistent(self):
        m = self.all_metrics['metrics']
        total_hours = m['08_total_duration_minutes'] / 60
        expected = m['02_total_acus'] / total_hours
        self.assertAlmostEqual(m['20_efficiency_ratio'], expected)

    def test_acus_per_minute_consistent(self):
        m = self.all_metrics['metrics']
        expected = m['02_total_acus'] / m['08_total_duration_minutes']
        self.assertAlmostEqual(m['10_acus_per_minute'], expected)

    def test_cost_per_minute_consistent(self):
        m = self.all_metrics['metrics']
        expected = m['10_acus_per_minute'] * self.config.price_per_acu
        self.assertAlmostEqual(m['11_cost_per_minute'], expected)


class TestMetricsToCSVPipeline(unittest.TestCase):
    """Integration: metrics calculation -> CSV export -> validation."""

    def setUp(self):
        self.tmp_data = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(SAMPLE_DATA, self.tmp_data)
        self.tmp_data.close()

        self.config = MetricsConfig(price_per_acu=0.05, currency='USD')
        self.calculator = MetricsCalculator(self.config)
        self.calculator.load_data(self.tmp_data.name)
        self.all_metrics = self.calculator.calculate_all_metrics()

        self.csv_file = tempfile.NamedTemporaryFile(
            mode='w', suffix='.csv', delete=False
        )
        self.csv_file.close()
        self._generate_csv()

    def _generate_csv(self):
        m = self.all_metrics['metrics']
        rows = []

        rows.append({'metric_name': 'Total Monthly Cost', 'value': m['01_total_monthly_cost'], 'unit': 'USD'})
        rows.append({'metric_name': 'Total ACUs', 'value': m['02_total_acus'], 'unit': 'ACUs'})

        for user, cost in m['03_cost_per_user'].items():
            rows.append({'metric_name': f'Cost Per User - {user}', 'value': cost, 'unit': 'USD'})

        for sid, acus in m['04_acus_per_session'].items():
            rows.append({'metric_name': f'ACUs Per Session - {sid}', 'value': acus, 'unit': 'ACUs'})

        rows.append({'metric_name': 'Average ACUs Per Session', 'value': m['05_average_acus_per_session'], 'unit': 'ACUs'})
        rows.append({'metric_name': 'Total Sessions', 'value': m['06_total_sessions'], 'unit': 'count'})

        for user, cnt in m['07_sessions_per_user'].items():
            rows.append({'metric_name': f'Sessions Per User - {user}', 'value': cnt, 'unit': 'count'})

        rows.append({'metric_name': 'Total Duration Minutes', 'value': m['08_total_duration_minutes'], 'unit': 'minutes'})
        rows.append({'metric_name': 'Average Session Duration', 'value': m['09_average_session_duration'], 'unit': 'minutes'})
        rows.append({'metric_name': 'ACUs Per Minute', 'value': m['10_acus_per_minute'], 'unit': 'ACUs/min'})
        rows.append({'metric_name': 'Cost Per Minute', 'value': m['11_cost_per_minute'], 'unit': 'USD'})
        rows.append({'metric_name': 'Unique Users', 'value': m['12_unique_users'], 'unit': 'count'})

        for task, cnt in m['13_sessions_by_task_type'].items():
            rows.append({'metric_name': f'Sessions By Task Type - {task}', 'value': cnt, 'unit': 'count'})

        for task, acus in m['14_acus_by_task_type'].items():
            rows.append({'metric_name': f'ACUs By Task Type - {task}', 'value': acus, 'unit': 'ACUs'})

        for task, cost in m['15_cost_by_task_type'].items():
            rows.append({'metric_name': f'Cost By Task Type - {task}', 'value': cost, 'unit': 'USD'})

        for dept, cnt in m['16_sessions_by_department'].items():
            rows.append({'metric_name': f'Sessions By Department - {dept}', 'value': cnt, 'unit': 'count'})

        for dept, acus in m['17_acus_by_department'].items():
            rows.append({'metric_name': f'ACUs By Department - {dept}', 'value': acus, 'unit': 'ACUs'})

        for dept, cost in m['18_cost_by_department'].items():
            rows.append({'metric_name': f'Cost By Department - {dept}', 'value': cost, 'unit': 'USD'})

        rows.append({'metric_name': 'Average Cost Per User', 'value': m['19_average_cost_per_user'], 'unit': 'USD'})
        rows.append({'metric_name': 'Efficiency Ratio', 'value': m['20_efficiency_ratio'], 'unit': 'ACUs/hour'})

        with open(self.csv_file.name, 'w', newline='') as f:
            writer = csv.DictWriter(f, fieldnames=['metric_name', 'value', 'unit'])
            writer.writeheader()
            writer.writerows(rows)

    def tearDown(self):
        os.unlink(self.tmp_data.name)
        os.unlink(self.csv_file.name)

    def test_csv_structure_valid(self):
        import pandas as pd
        df = pd.read_csv(self.csv_file.name)
        self.assertTrue(validate_csv_structure(df))

    def test_csv_has_20_metric_types(self):
        import pandas as pd
        df = pd.read_csv(self.csv_file.name)
        self.assertTrue(validate_metric_types(df))

    def test_csv_financial_integrity(self):
        import pandas as pd
        df = pd.read_csv(self.csv_file.name)
        self.assertTrue(validate_financial_integrity(df))

    def test_csv_no_negative_costs(self):
        import pandas as pd
        df = pd.read_csv(self.csv_file.name)
        usd_rows = df[df['unit'] == 'USD']
        negative = usd_rows[usd_rows['value'].astype(float) < 0]
        self.assertTrue(negative.empty)


class TestExportDailyACUsCSV(unittest.TestCase):
    """Integration: raw data JSON -> daily consumption CSV."""

    def setUp(self):
        self.raw_data = [
            {"timestamp": "2024-09-01T10:00:00", "acu_consumed": 100},
            {"timestamp": "2024-09-01T14:00:00", "acu_consumed": 200},
            {"timestamp": "2024-09-02T09:00:00", "acu_consumed": 150},
        ]
        self.tmp_json = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(self.raw_data, self.tmp_json)
        self.tmp_json.close()

        self.tmp_csv = tempfile.NamedTemporaryFile(
            mode='w', suffix='.csv', delete=False
        )
        self.tmp_csv.close()

    def tearDown(self):
        os.unlink(self.tmp_json.name)
        os.unlink(self.tmp_csv.name)

    def test_export_creates_csv(self):
        export_daily_acus_to_csv(self.tmp_json.name, self.tmp_csv.name)
        self.assertTrue(os.path.exists(self.tmp_csv.name))

        with open(self.tmp_csv.name, 'r') as f:
            reader = csv.DictReader(f)
            rows = list(reader)

        self.assertEqual(len(rows), 3)
        self.assertEqual(rows[0]['Date'], '2024-09-01')
        self.assertEqual(float(rows[0]['ACUs_Consumed']), 100.0)

    def test_export_handles_missing_file(self):
        export_daily_acus_to_csv('/nonexistent/file.json', self.tmp_csv.name)


class TestHelperFunctions(unittest.TestCase):
    """Tests for helper functions in generate_report module."""

    def test_calculate_monthly_acus(self):
        consumption = {
            "2024-09-01": 100.0,
            "2024-09-15": 200.0,
            "2024-10-01": 50.0,
        }
        result = calculate_monthly_acus(consumption, "2024-09")
        self.assertAlmostEqual(result, 300.0)

    def test_calculate_monthly_acus_no_match(self):
        consumption = {
            "2024-09-01": 100.0,
        }
        result = calculate_monthly_acus(consumption, "2024-10")
        self.assertAlmostEqual(result, 0.0)

    def test_calculate_cost_from_acus(self):
        result = calculate_cost_from_acus(1000.0, 0.05)
        self.assertAlmostEqual(result, 50.0)

    def test_calculate_cost_from_acus_zero(self):
        result = calculate_cost_from_acus(0, 0.05)
        self.assertAlmostEqual(result, 0.0)

    def test_calculate_cost_rounding(self):
        result = calculate_cost_from_acus(333.333, 0.03)
        self.assertEqual(result, round(333.333 * 0.03, 2))


class TestTransformRawData(unittest.TestCase):
    """Tests for the transform_raw_data function."""

    def setUp(self):
        self.raw_records = [
            {
                "session_id": "s1",
                "user_id": "u1",
                "organization_id": "org1",
                "timestamp": "2024-09-01T10:00:00",
                "acu_consumed": 100,
                "business_unit": "Engineering",
                "task_type": "Feature",
                "session_outcome": "Success"
            },
            {
                "session_id": "s2",
                "user_id": "u2",
                "organization_id": "org1",
                "timestamp": "2024-09-02T11:00:00",
                "acu_consumed": 200,
                "business_unit": "QA",
                "task_type": "BugFix",
                "session_outcome": "Failure"
            }
        ]

    def test_transform_produces_expected_structure(self):
        result = transform_raw_data(self.raw_records)
        self.assertIn('sessions', result)
        self.assertIn('user_details', result)
        self.assertIn('reporting_period', result)

    def test_transform_session_count(self):
        result = transform_raw_data(self.raw_records)
        self.assertEqual(len(result['sessions']), 2)

    def test_transform_user_details_populated(self):
        result = transform_raw_data(self.raw_records)
        self.assertGreater(len(result['user_details']), 0)


class TestValidationPipelineEdgeCases(unittest.TestCase):
    """Tests for validation functions with invalid data."""

    def test_validate_csv_wrong_columns(self):
        import pandas as pd
        df = pd.DataFrame({'wrong_col': [1], 'value': [2], 'unit': ['USD']})
        with self.assertRaises(ValueError):
            validate_csv_structure(df)

    def test_validate_metric_types_wrong_count(self):
        import pandas as pd
        df = pd.DataFrame({
            'metric_name': ['Metric A', 'Metric B'],
            'value': [1, 2],
            'unit': ['USD', 'ACUs']
        })
        with self.assertRaises(ValueError):
            validate_metric_types(df)

    def test_validate_financial_missing_total(self):
        import pandas as pd
        df = pd.DataFrame({
            'metric_name': ['Some Other Metric'],
            'value': [100],
            'unit': ['USD']
        })
        with self.assertRaises(ValueError):
            validate_financial_integrity(df)


class TestPipelineWithDifferentConfigs(unittest.TestCase):
    """Integration: verify the pipeline produces correct results with different configs."""

    def setUp(self):
        self.tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(SAMPLE_DATA, self.tmp)
        self.tmp.close()

    def tearDown(self):
        os.unlink(self.tmp.name)

    def test_different_prices_produce_proportional_costs(self):
        config_low = MetricsConfig(price_per_acu=0.01)
        config_high = MetricsConfig(price_per_acu=0.10)

        calc_low = MetricsCalculator(config_low)
        calc_low.load_data(self.tmp.name)
        cost_low = calc_low.calculate_total_monthly_cost()

        calc_high = MetricsCalculator(config_high)
        calc_high.load_data(self.tmp.name)
        cost_high = calc_high.calculate_total_monthly_cost()

        self.assertAlmostEqual(cost_high / cost_low, 10.0)

    def test_same_data_same_session_count(self):
        calc1 = MetricsCalculator(MetricsConfig(price_per_acu=0.01))
        calc1.load_data(self.tmp.name)

        calc2 = MetricsCalculator(MetricsConfig(price_per_acu=1.00))
        calc2.load_data(self.tmp.name)

        self.assertEqual(
            calc1.calculate_total_sessions(),
            calc2.calculate_total_sessions()
        )

    def test_same_data_same_acus(self):
        calc1 = MetricsCalculator(MetricsConfig(price_per_acu=0.01))
        calc1.load_data(self.tmp.name)

        calc2 = MetricsCalculator(MetricsConfig(price_per_acu=1.00))
        calc2.load_data(self.tmp.name)

        self.assertEqual(
            calc1.calculate_total_acus(),
            calc2.calculate_total_acus()
        )


class TestDataAdapterIntegration(unittest.TestCase):
    """Integration: simulated API fetch -> write -> load -> calculate."""

    @patch('data_adapter.requests.get')
    def test_fetch_write_load_calculate(self, mock_get):
        from data_adapter import fetch_cognition_data, write_raw_data

        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            'data': [
                {
                    "session_id": "api_001",
                    "user_id": "u1",
                    "timestamp": "2024-09-01T10:00:00",
                    "acu_consumed": 500
                }
            ],
            'has_more': False
        }
        mock_response.raise_for_status = MagicMock()
        mock_get.return_value = mock_response

        fetched = fetch_cognition_data(api_key="test_key")
        self.assertEqual(len(fetched), 1)

        tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        tmp.close()

        try:
            write_raw_data(fetched, tmp.name)

            with open(tmp.name, 'r') as f:
                loaded = json.load(f)
            self.assertEqual(loaded[0]['acu_consumed'], 500)
        finally:
            os.unlink(tmp.name)


if __name__ == '__main__':
    unittest.main()
