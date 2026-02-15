"""
Comprehensive unit tests for the MetricsCalculator module.
Validates all 20 metrics calculations with various data scenarios.
"""

import unittest
import json
import os
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from metrics_calculator import MetricsCalculator
from config import MetricsConfig


class TestMetricsCalculatorBasic(unittest.TestCase):
    """Tests for basic metric calculations with known data."""

    def setUp(self):
        self.test_data = {
            "organization": "Test Org",
            "reporting_period": {
                "start_date": "2024-09-01",
                "end_date": "2024-09-30",
                "month": "September 2024"
            },
            "sessions": [
                {
                    "session_id": "sess_001",
                    "user_email": "alice@test.com",
                    "duration_minutes": 60,
                    "acus_consumed": 300,
                    "task_type": "feature_development",
                    "status": "completed"
                },
                {
                    "session_id": "sess_002",
                    "user_email": "bob@test.com",
                    "duration_minutes": 90,
                    "acus_consumed": 450,
                    "task_type": "bug_fix",
                    "status": "completed"
                },
                {
                    "session_id": "sess_003",
                    "user_email": "alice@test.com",
                    "duration_minutes": 30,
                    "acus_consumed": 150,
                    "task_type": "feature_development",
                    "status": "completed"
                }
            ],
            "user_details": [
                {
                    "user_email": "alice@test.com",
                    "department": "Engineering",
                    "role": "Developer"
                },
                {
                    "user_email": "bob@test.com",
                    "department": "QA",
                    "role": "QA Engineer"
                }
            ]
        }

        self.tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(self.test_data, self.tmp)
        self.tmp.close()

        self.config = MetricsConfig(price_per_acu=0.10, currency='USD')
        self.calculator = MetricsCalculator(self.config)
        self.calculator.load_data(self.tmp.name)

    def tearDown(self):
        os.unlink(self.tmp.name)

    def test_total_monthly_cost(self):
        result = self.calculator.calculate_total_monthly_cost()
        self.assertEqual(result, 90.0)

    def test_total_acus(self):
        result = self.calculator.calculate_total_acus()
        self.assertEqual(result, 900)

    def test_cost_per_user(self):
        result = self.calculator.calculate_cost_per_user()
        self.assertAlmostEqual(result['alice@test.com'], 45.0)
        self.assertAlmostEqual(result['bob@test.com'], 45.0)

    def test_acus_per_session(self):
        result = self.calculator.calculate_acus_per_session()
        self.assertEqual(result['sess_001'], 300)
        self.assertEqual(result['sess_002'], 450)
        self.assertEqual(result['sess_003'], 150)

    def test_average_acus_per_session(self):
        result = self.calculator.calculate_average_acus_per_session()
        self.assertAlmostEqual(result, 300.0)

    def test_total_sessions(self):
        result = self.calculator.calculate_total_sessions()
        self.assertEqual(result, 3)

    def test_sessions_per_user(self):
        result = self.calculator.calculate_sessions_per_user()
        self.assertEqual(result['alice@test.com'], 2)
        self.assertEqual(result['bob@test.com'], 1)

    def test_total_duration_minutes(self):
        result = self.calculator.calculate_total_duration_minutes()
        self.assertEqual(result, 180)

    def test_average_session_duration(self):
        result = self.calculator.calculate_average_session_duration()
        self.assertAlmostEqual(result, 60.0)

    def test_acus_per_minute(self):
        result = self.calculator.calculate_acus_per_minute()
        self.assertAlmostEqual(result, 5.0)

    def test_cost_per_minute(self):
        result = self.calculator.calculate_cost_per_minute()
        self.assertAlmostEqual(result, 0.50)

    def test_unique_users(self):
        result = self.calculator.calculate_unique_users()
        self.assertEqual(result, 2)

    def test_sessions_by_task_type(self):
        result = self.calculator.calculate_sessions_by_task_type()
        self.assertEqual(result['feature_development'], 2)
        self.assertEqual(result['bug_fix'], 1)

    def test_acus_by_task_type(self):
        result = self.calculator.calculate_acus_by_task_type()
        self.assertEqual(result['feature_development'], 450)
        self.assertEqual(result['bug_fix'], 450)

    def test_cost_by_task_type(self):
        result = self.calculator.calculate_cost_by_task_type()
        self.assertAlmostEqual(result['feature_development'], 45.0)
        self.assertAlmostEqual(result['bug_fix'], 45.0)

    def test_sessions_by_department(self):
        result = self.calculator.calculate_sessions_by_department()
        self.assertEqual(result['Engineering'], 2)
        self.assertEqual(result['QA'], 1)

    def test_acus_by_department(self):
        result = self.calculator.calculate_acus_by_department()
        self.assertEqual(result['Engineering'], 450)
        self.assertEqual(result['QA'], 450)

    def test_cost_by_department(self):
        result = self.calculator.calculate_cost_by_department()
        self.assertAlmostEqual(result['Engineering'], 45.0)
        self.assertAlmostEqual(result['QA'], 45.0)

    def test_average_cost_per_user(self):
        result = self.calculator.calculate_average_cost_per_user()
        self.assertAlmostEqual(result, 45.0)

    def test_efficiency_ratio(self):
        result = self.calculator.calculate_efficiency_ratio()
        self.assertAlmostEqual(result, 300.0)

    def test_calculate_all_metrics_keys(self):
        all_metrics = self.calculator.calculate_all_metrics()
        self.assertIn('config', all_metrics)
        self.assertIn('metrics', all_metrics)
        self.assertIn('reporting_period', all_metrics)

        expected_keys = [
            '01_total_monthly_cost',
            '02_total_acus',
            '03_cost_per_user',
            '04_acus_per_session',
            '05_average_acus_per_session',
            '06_total_sessions',
            '07_sessions_per_user',
            '08_total_duration_minutes',
            '09_average_session_duration',
            '10_acus_per_minute',
            '11_cost_per_minute',
            '12_unique_users',
            '13_sessions_by_task_type',
            '14_acus_by_task_type',
            '15_cost_by_task_type',
            '16_sessions_by_department',
            '17_acus_by_department',
            '18_cost_by_department',
            '19_average_cost_per_user',
            '20_efficiency_ratio'
        ]
        for key in expected_keys:
            self.assertIn(key, all_metrics['metrics'], f"Missing metric: {key}")

    def test_calculate_all_metrics_values(self):
        all_metrics = self.calculator.calculate_all_metrics()
        m = all_metrics['metrics']
        self.assertEqual(m['01_total_monthly_cost'], 90.0)
        self.assertEqual(m['02_total_acus'], 900)
        self.assertEqual(m['06_total_sessions'], 3)
        self.assertEqual(m['12_unique_users'], 2)


class TestMetricsCalculatorEmptyData(unittest.TestCase):
    """Tests for edge cases with empty data."""

    def setUp(self):
        self.empty_data = {
            "sessions": [],
            "user_details": []
        }
        self.tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(self.empty_data, self.tmp)
        self.tmp.close()

        self.config = MetricsConfig(price_per_acu=0.10)
        self.calculator = MetricsCalculator(self.config)
        self.calculator.load_data(self.tmp.name)

    def tearDown(self):
        os.unlink(self.tmp.name)

    def test_empty_total_acus(self):
        self.assertEqual(self.calculator.calculate_total_acus(), 0)

    def test_empty_total_monthly_cost(self):
        self.assertEqual(self.calculator.calculate_total_monthly_cost(), 0.0)

    def test_empty_total_sessions(self):
        self.assertEqual(self.calculator.calculate_total_sessions(), 0)

    def test_empty_unique_users(self):
        self.assertEqual(self.calculator.calculate_unique_users(), 0)

    def test_empty_average_acus_per_session(self):
        self.assertEqual(self.calculator.calculate_average_acus_per_session(), 0.0)

    def test_empty_average_session_duration(self):
        self.assertEqual(self.calculator.calculate_average_session_duration(), 0.0)

    def test_empty_acus_per_minute(self):
        self.assertEqual(self.calculator.calculate_acus_per_minute(), 0.0)

    def test_empty_cost_per_minute(self):
        self.assertEqual(self.calculator.calculate_cost_per_minute(), 0.0)

    def test_empty_average_cost_per_user(self):
        self.assertEqual(self.calculator.calculate_average_cost_per_user(), 0.0)

    def test_empty_efficiency_ratio(self):
        self.assertEqual(self.calculator.calculate_efficiency_ratio(), 0.0)

    def test_empty_cost_per_user(self):
        self.assertEqual(self.calculator.calculate_cost_per_user(), {})

    def test_empty_sessions_per_user(self):
        self.assertEqual(self.calculator.calculate_sessions_per_user(), {})

    def test_empty_sessions_by_task_type(self):
        self.assertEqual(self.calculator.calculate_sessions_by_task_type(), {})

    def test_empty_sessions_by_department(self):
        self.assertEqual(self.calculator.calculate_sessions_by_department(), {})

    def test_empty_total_duration(self):
        self.assertEqual(self.calculator.calculate_total_duration_minutes(), 0)


class TestMetricsCalculatorCustomConfig(unittest.TestCase):
    """Tests for custom configuration injection."""

    def setUp(self):
        self.test_data = {
            "sessions": [
                {
                    "session_id": "s1",
                    "user_email": "dev@test.com",
                    "duration_minutes": 120,
                    "acus_consumed": 600,
                    "task_type": "code_review",
                    "status": "completed"
                }
            ],
            "user_details": [
                {
                    "user_email": "dev@test.com",
                    "department": "Platform",
                    "role": "Senior Dev"
                }
            ]
        }
        self.tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(self.test_data, self.tmp)
        self.tmp.close()

    def tearDown(self):
        os.unlink(self.tmp.name)

    def test_default_config(self):
        calc = MetricsCalculator()
        calc.load_data(self.tmp.name)
        self.assertAlmostEqual(calc.calculate_total_monthly_cost(), 600 * 0.05)

    def test_custom_price_per_acu(self):
        config = MetricsConfig(price_per_acu=0.25, currency='EUR')
        calc = MetricsCalculator(config)
        calc.load_data(self.tmp.name)
        self.assertAlmostEqual(calc.calculate_total_monthly_cost(), 150.0)

    def test_config_to_dict(self):
        config = MetricsConfig(
            price_per_acu=0.08,
            currency='GBP',
            working_hours_per_day=7,
            working_days_per_month=20
        )
        d = config.to_dict()
        self.assertEqual(d['price_per_acu'], 0.08)
        self.assertEqual(d['currency'], 'GBP')
        self.assertEqual(d['working_hours_per_day'], 7)
        self.assertEqual(d['working_days_per_month'], 20)

    def test_cost_scales_linearly(self):
        configs = [
            MetricsConfig(price_per_acu=0.01),
            MetricsConfig(price_per_acu=0.10),
            MetricsConfig(price_per_acu=1.00),
        ]
        costs = []
        for cfg in configs:
            calc = MetricsCalculator(cfg)
            calc.load_data(self.tmp.name)
            costs.append(calc.calculate_total_monthly_cost())

        self.assertAlmostEqual(costs[1] / costs[0], 10.0)
        self.assertAlmostEqual(costs[2] / costs[1], 10.0)


class TestMetricsCalculatorSingleUser(unittest.TestCase):
    """Tests with a single user having multiple sessions."""

    def setUp(self):
        self.test_data = {
            "sessions": [
                {
                    "session_id": f"s{i}",
                    "user_email": "solo@test.com",
                    "duration_minutes": 45,
                    "acus_consumed": 100,
                    "task_type": "testing" if i % 2 == 0 else "deployment",
                    "status": "completed"
                }
                for i in range(4)
            ],
            "user_details": [
                {
                    "user_email": "solo@test.com",
                    "department": "DevOps",
                    "role": "Engineer"
                }
            ]
        }
        self.tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(self.test_data, self.tmp)
        self.tmp.close()

        self.config = MetricsConfig(price_per_acu=0.05)
        self.calculator = MetricsCalculator(self.config)
        self.calculator.load_data(self.tmp.name)

    def tearDown(self):
        os.unlink(self.tmp.name)

    def test_single_user_unique_count(self):
        self.assertEqual(self.calculator.calculate_unique_users(), 1)

    def test_single_user_sessions(self):
        sessions = self.calculator.calculate_sessions_per_user()
        self.assertEqual(sessions['solo@test.com'], 4)

    def test_single_user_total_acus(self):
        self.assertEqual(self.calculator.calculate_total_acus(), 400)

    def test_single_user_average_cost_equals_total(self):
        total = self.calculator.calculate_total_monthly_cost()
        avg = self.calculator.calculate_average_cost_per_user()
        self.assertAlmostEqual(total, avg)

    def test_single_user_department_sessions(self):
        dept = self.calculator.calculate_sessions_by_department()
        self.assertEqual(dept['DevOps'], 4)

    def test_task_type_distribution(self):
        tasks = self.calculator.calculate_sessions_by_task_type()
        self.assertEqual(tasks['testing'], 2)
        self.assertEqual(tasks['deployment'], 2)

    def test_efficiency_ratio_single_user(self):
        total_hours = (4 * 45) / 60
        expected = 400 / total_hours
        self.assertAlmostEqual(
            self.calculator.calculate_efficiency_ratio(), expected
        )


class TestMetricsCalculatorMissingFields(unittest.TestCase):
    """Tests for data with missing optional fields."""

    def setUp(self):
        self.test_data = {
            "sessions": [
                {
                    "session_id": "s1",
                    "user_email": "user@test.com"
                },
                {
                    "session_id": "s2",
                    "user_email": "user@test.com",
                    "acus_consumed": 200,
                    "duration_minutes": 40
                }
            ],
            "user_details": []
        }
        self.tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(self.test_data, self.tmp)
        self.tmp.close()

        self.config = MetricsConfig(price_per_acu=0.10)
        self.calculator = MetricsCalculator(self.config)
        self.calculator.load_data(self.tmp.name)

    def tearDown(self):
        os.unlink(self.tmp.name)

    def test_missing_acus_defaults_to_zero(self):
        self.assertEqual(self.calculator.calculate_total_acus(), 200)

    def test_missing_duration_defaults_to_zero(self):
        self.assertEqual(self.calculator.calculate_total_duration_minutes(), 40)

    def test_missing_task_type_defaults_to_unknown(self):
        tasks = self.calculator.calculate_sessions_by_task_type()
        self.assertIn('unknown', tasks)

    def test_missing_department_defaults_to_unknown(self):
        dept = self.calculator.calculate_sessions_by_department()
        self.assertIn('Unknown', dept)


class TestMetricsCalculatorFileErrors(unittest.TestCase):
    """Tests for file loading error handling."""

    def test_file_not_found(self):
        calc = MetricsCalculator()
        with self.assertRaises(FileNotFoundError):
            calc.load_data('/nonexistent/path/data.json')

    def test_invalid_json(self):
        tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        tmp.write("not valid json {{{")
        tmp.close()
        try:
            calc = MetricsCalculator()
            with self.assertRaises(json.JSONDecodeError):
                calc.load_data(tmp.name)
        finally:
            os.unlink(tmp.name)


class TestMetricsCalculatorLargeDataset(unittest.TestCase):
    """Tests with a larger dataset to verify aggregate calculations."""

    def setUp(self):
        sessions = []
        users = set()
        for i in range(100):
            user_email = f"user{i % 10}@test.com"
            users.add(user_email)
            sessions.append({
                "session_id": f"s{i:04d}",
                "user_email": user_email,
                "duration_minutes": 30 + (i % 5) * 15,
                "acus_consumed": 100 + (i % 7) * 50,
                "task_type": ["dev", "test", "review", "deploy"][i % 4],
                "status": "completed"
            })

        user_details = [
            {
                "user_email": email,
                "department": ["Eng", "QA", "Ops"][hash(email) % 3],
                "role": "Engineer"
            }
            for email in users
        ]

        self.test_data = {
            "sessions": sessions,
            "user_details": user_details
        }
        self.tmp = tempfile.NamedTemporaryFile(
            mode='w', suffix='.json', delete=False
        )
        json.dump(self.test_data, self.tmp)
        self.tmp.close()

        self.config = MetricsConfig(price_per_acu=0.05)
        self.calculator = MetricsCalculator(self.config)
        self.calculator.load_data(self.tmp.name)

    def tearDown(self):
        os.unlink(self.tmp.name)

    def test_total_sessions_count(self):
        self.assertEqual(self.calculator.calculate_total_sessions(), 100)

    def test_unique_users_count(self):
        self.assertEqual(self.calculator.calculate_unique_users(), 10)

    def test_cost_per_user_sums_to_total(self):
        cost_per_user = self.calculator.calculate_cost_per_user()
        total_from_users = sum(cost_per_user.values())
        total_cost = self.calculator.calculate_total_monthly_cost()
        self.assertAlmostEqual(total_from_users, total_cost, places=2)

    def test_sessions_per_user_sums_to_total(self):
        sessions_per_user = self.calculator.calculate_sessions_per_user()
        total_from_users = sum(sessions_per_user.values())
        self.assertEqual(total_from_users, 100)

    def test_task_type_sessions_sum_to_total(self):
        by_task = self.calculator.calculate_sessions_by_task_type()
        self.assertEqual(sum(by_task.values()), 100)

    def test_department_sessions_sum_to_total(self):
        by_dept = self.calculator.calculate_sessions_by_department()
        self.assertEqual(sum(by_dept.values()), 100)

    def test_cost_by_task_sums_to_total(self):
        cost_by_task = self.calculator.calculate_cost_by_task_type()
        total_cost = self.calculator.calculate_total_monthly_cost()
        self.assertAlmostEqual(sum(cost_by_task.values()), total_cost, places=2)

    def test_cost_by_dept_sums_to_total(self):
        cost_by_dept = self.calculator.calculate_cost_by_department()
        total_cost = self.calculator.calculate_total_monthly_cost()
        self.assertAlmostEqual(sum(cost_by_dept.values()), total_cost, places=2)

    def test_acus_by_task_sums_to_total(self):
        acus_by_task = self.calculator.calculate_acus_by_task_type()
        self.assertEqual(sum(acus_by_task.values()), self.calculator.calculate_total_acus())

    def test_acus_by_dept_sums_to_total(self):
        acus_by_dept = self.calculator.calculate_acus_by_department()
        self.assertEqual(sum(acus_by_dept.values()), self.calculator.calculate_total_acus())


if __name__ == '__main__':
    unittest.main()
