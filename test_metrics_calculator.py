"""
Comprehensive unit tests for the MetricsCalculator module.
Tests the main calculation functions to verify accuracy.
"""

import unittest
import json
import os
from metrics_calculator import MetricsCalculator
from config import MetricsConfig


class TestMetricsCalculator(unittest.TestCase):
    """Test suite for MetricsCalculator class."""

    def setUp(self):
        """Set up test fixtures before each test method."""
        self.test_data = {
            "organization": "Test Org",
            "reporting_period": {
                "start_date": "2024-09-01",
                "end_date": "2024-09-30",
                "month": "September 2024"
            },
            "sessions": [
                {
                    "session_id": "test_001",
                    "user_email": "user1@test.com",
                    "duration_minutes": 60,
                    "acus_consumed": 300,
                    "task_type": "feature_development",
                    "status": "completed"
                },
                {
                    "session_id": "test_002",
                    "user_email": "user2@test.com",
                    "duration_minutes": 90,
                    "acus_consumed": 450,
                    "task_type": "bug_fix",
                    "status": "completed"
                },
                {
                    "session_id": "test_003",
                    "user_email": "user1@test.com",
                    "duration_minutes": 30,
                    "acus_consumed": 150,
                    "task_type": "feature_development",
                    "status": "completed"
                }
            ],
            "user_details": [
                {
                    "user_email": "user1@test.com",
                    "department": "Engineering",
                    "role": "Developer"
                },
                {
                    "user_email": "user2@test.com",
                    "department": "QA",
                    "role": "QA Engineer"
                }
            ]
        }

        self.test_file = 'test_data.json'
        with open(self.test_file, 'w') as f:
            json.dump(self.test_data, f)

        self.config = MetricsConfig(price_per_acu=0.10, currency='USD')
        self.calculator = MetricsCalculator(self.config)
        self.calculator.load_data(self.test_file)

    def tearDown(self):
        """Clean up test fixtures after each test method."""
        if os.path.exists(self.test_file):
            os.remove(self.test_file)

    def test_calculate_total_monthly_cost(self):
        """Test calculation of total monthly cost."""
        expected_cost = (300 + 450 + 150) * 0.10
        actual_cost = self.calculator.calculate_total_monthly_cost()

        self.assertEqual(actual_cost, expected_cost)
        self.assertEqual(actual_cost, 90.0)

    def test_calculate_total_acus(self):
        """Test calculation of total ACUs consumed."""
        expected_acus = 300 + 450 + 150
        actual_acus = self.calculator.calculate_total_acus()

        self.assertEqual(actual_acus, expected_acus)
        self.assertEqual(actual_acus, 900)

    def test_calculate_cost_per_user(self):
        """Test calculation of cost per user."""
        cost_per_user = self.calculator.calculate_cost_per_user()

        self.assertIn('user1@test.com', cost_per_user)
        self.assertIn('user2@test.com', cost_per_user)

        self.assertEqual(cost_per_user['user1@test.com'], 45.0)
        self.assertEqual(cost_per_user['user2@test.com'], 45.0)

    def test_calculate_average_acus_per_session(self):
        """Test calculation of average ACUs per session."""
        expected_avg = 900 / 3
        actual_avg = self.calculator.calculate_average_acus_per_session()

        self.assertEqual(actual_avg, expected_avg)
        self.assertEqual(actual_avg, 300.0)

    def test_calculate_sessions_per_user(self):
        """Test calculation of sessions per user."""
        sessions_per_user = self.calculator.calculate_sessions_per_user()

        self.assertEqual(sessions_per_user['user1@test.com'], 2)
        self.assertEqual(sessions_per_user['user2@test.com'], 1)

    def test_calculate_total_duration_minutes(self):
        """Test calculation of total duration."""
        expected_duration = 60 + 90 + 30
        actual_duration = self.calculator.calculate_total_duration_minutes()

        self.assertEqual(actual_duration, expected_duration)
        self.assertEqual(actual_duration, 180)

    def test_calculate_average_session_duration(self):
        """Test calculation of average session duration."""
        expected_avg = 180 / 3
        actual_avg = self.calculator.calculate_average_session_duration()

        self.assertEqual(actual_avg, expected_avg)
        self.assertEqual(actual_avg, 60.0)

    def test_calculate_acus_per_minute(self):
        """Test calculation of ACUs per minute."""
        expected_ratio = 900 / 180
        actual_ratio = self.calculator.calculate_acus_per_minute()

        self.assertEqual(actual_ratio, expected_ratio)
        self.assertEqual(actual_ratio, 5.0)

    def test_calculate_unique_users(self):
        """Test calculation of unique users."""
        unique_users = self.calculator.calculate_unique_users()

        self.assertEqual(unique_users, 2)

    def test_calculate_sessions_by_task_type(self):
        """Test calculation of sessions by task type."""
        sessions_by_task = self.calculator.calculate_sessions_by_task_type()

        self.assertEqual(sessions_by_task['feature_development'], 2)
        self.assertEqual(sessions_by_task['bug_fix'], 1)

    def test_calculate_acus_by_task_type(self):
        """Test calculation of ACUs by task type."""
        acus_by_task = self.calculator.calculate_acus_by_task_type()

        self.assertEqual(acus_by_task['feature_development'], 450)
        self.assertEqual(acus_by_task['bug_fix'], 450)

    def test_calculate_sessions_by_department(self):
        """Test calculation of sessions by department."""
        sessions_by_dept = self.calculator.calculate_sessions_by_department()

        self.assertEqual(sessions_by_dept['Engineering'], 2)
        self.assertEqual(sessions_by_dept['QA'], 1)

    def test_calculate_acus_by_department(self):
        """Test calculation of ACUs by department."""
        acus_by_dept = self.calculator.calculate_acus_by_department()

        self.assertEqual(acus_by_dept['Engineering'], 450)
        self.assertEqual(acus_by_dept['QA'], 450)

    def test_calculate_efficiency_ratio(self):
        """Test calculation of efficiency ratio."""
        expected_ratio = 900 / (180 / 60)
        actual_ratio = self.calculator.calculate_efficiency_ratio()

        self.assertEqual(actual_ratio, expected_ratio)
        self.assertEqual(actual_ratio, 300.0)

    def test_custom_config_injection(self):
        """Test that custom configuration is properly injected."""
        custom_config = MetricsConfig(price_per_acu=0.25, currency='EUR')
        custom_calculator = MetricsCalculator(custom_config)
        custom_calculator.load_data(self.test_file)

        total_cost = custom_calculator.calculate_total_monthly_cost()
        expected_cost = 900 * 0.25

        self.assertEqual(total_cost, expected_cost)
        self.assertEqual(total_cost, 225.0)

    def test_empty_sessions(self):
        """Test behavior with empty sessions list."""
        empty_data = {
            "sessions": [],
            "user_details": []
        }

        empty_file = 'empty_test.json'
        with open(empty_file, 'w') as f:
            json.dump(empty_data, f)

        try:
            empty_calculator = MetricsCalculator(self.config)
            empty_calculator.load_data(empty_file)

            self.assertEqual(empty_calculator.calculate_total_acus(), 0)
            self.assertEqual(empty_calculator.calculate_total_sessions(), 0)
            self.assertEqual(empty_calculator.calculate_unique_users(), 0)
            self.assertEqual(empty_calculator.calculate_average_acus_per_session(), 0.0)
        finally:
            if os.path.exists(empty_file):
                os.remove(empty_file)

    def test_calculate_all_metrics(self):
        """Test that calculate_all_metrics returns all expected metrics."""
        all_metrics = self.calculator.calculate_all_metrics()

        self.assertIn('config', all_metrics)
        self.assertIn('metrics', all_metrics)
        self.assertIn('reporting_period', all_metrics)

        metrics = all_metrics['metrics']

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
            self.assertIn(key, metrics, f"Missing metric: {key}")


if __name__ == '__main__':
    unittest.main()
