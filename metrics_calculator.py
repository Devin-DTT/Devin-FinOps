"""
Metrics Calculator Module for Devin Usage Data Analysis.
This module processes raw usage data and calculates 20 foundational metrics.
"""

import json
import logging
from typing import Dict, List, Any
from collections import defaultdict

from config import MetricsConfig
from validators import UsageData
from error_handling import (
    handle_pipeline_phase,
    MetricsCalculationError,
    DataValidationError,
)

logger = logging.getLogger(__name__)


class MetricsCalculator:
    """
    Main class for calculating usage metrics from Devin session data.
    Supports configurable cost rates and parameters.
    """

    def __init__(self, config: MetricsConfig = None):
        """
        Initialize the metrics calculator.

        Args:
            config: MetricsConfig object with cost rates and parameters.
                   If None, uses default configuration.
        """
        self.config = config or MetricsConfig()
        self.data = None
        self.sessions = []
        self.users = []

    def load_data(self, json_file_path: str) -> None:
        """
        Load and validate usage data from JSON file.

        Args:
            json_file_path: Path to the raw_usage_data.json file

        Raises:
            DataValidationError: If the file cannot be read or data fails validation.
        """
        logger.info("[CALCULATE] Loading data from %s", json_file_path)
        try:
            with open(json_file_path, 'r') as f:
                raw = json.load(f)
        except FileNotFoundError as exc:
            raise DataValidationError(
                f"Data file not found: {json_file_path}",
                details={"file_path": json_file_path},
            ) from exc
        except json.JSONDecodeError as exc:
            raise DataValidationError(
                f"Invalid JSON in data file: {json_file_path}",
                details={"file_path": json_file_path, "error": str(exc)},
            ) from exc

        try:
            validated = UsageData.model_validate(raw)
        except Exception as exc:
            raise DataValidationError(
                f"Data validation failed for {json_file_path}: {exc}",
                details={"file_path": json_file_path, "error": str(exc)},
            ) from exc

        self.data = raw
        self.sessions = [s.model_dump() for s in validated.sessions]
        self.users = [u.model_dump() for u in validated.user_details]
        logger.info(
            "[CALCULATE] Data loaded and validated: %d sessions, %d users",
            len(self.sessions),
            len(self.users),
        )

    def calculate_total_monthly_cost(self) -> float:
        """
        Calculate total monthly cost.
        Metric 1: Coste total mensual

        Returns:
            Total cost for the month in configured currency
        """
        total_acus = sum(session.get('acus_consumed', 0) for session in self.sessions)
        return total_acus * self.config.price_per_acu

    def calculate_total_acus(self) -> int:
        """
        Calculate total ACUs consumed.
        Metric 2: ACUs totales

        Returns:
            Total ACUs consumed across all sessions
        """
        return sum(session.get('acus_consumed', 0) for session in self.sessions)

    def calculate_cost_per_user(self) -> Dict[str, float]:
        """
        Calculate cost per user.
        Metric 3: Coste por usuario

        Returns:
            Dictionary mapping user email to their total cost
        """
        user_acus = defaultdict(int)

        for session in self.sessions:
            user_email = session.get('user_email')
            acus = session.get('acus_consumed', 0)
            user_acus[user_email] += acus

        return {
            user: acus * self.config.price_per_acu
            for user, acus in user_acus.items()
        }

    def calculate_acus_per_session(self) -> Dict[str, int]:
        """
        Calculate ACUs consumed per session.
        Metric 4: ACUs por sesión

        Returns:
            Dictionary mapping session ID to ACUs consumed
        """
        return {
            session.get('session_id'): session.get('acus_consumed', 0)
            for session in self.sessions
        }

    def calculate_average_acus_per_session(self) -> float:
        """
        Calculate average ACUs per session.
        Metric 5: Promedio de ACUs por sesión

        Returns:
            Average ACUs consumed per session
        """
        if not self.sessions:
            return 0.0

        total_acus = self.calculate_total_acus()
        return total_acus / len(self.sessions)

    def calculate_total_sessions(self) -> int:
        """
        Calculate total number of sessions.
        Metric 6: Total de sesiones

        Returns:
            Total number of sessions
        """
        return len(self.sessions)

    def calculate_sessions_per_user(self) -> Dict[str, int]:
        """
        Calculate number of sessions per user.
        Metric 7: Sesiones por usuario

        Returns:
            Dictionary mapping user email to session count
        """
        user_sessions = defaultdict(int)

        for session in self.sessions:
            user_email = session.get('user_email')
            user_sessions[user_email] += 1

        return dict(user_sessions)

    def calculate_total_duration_minutes(self) -> int:
        """
        Calculate total duration in minutes.
        Metric 8: Duración total (minutos)

        Returns:
            Total duration of all sessions in minutes
        """
        return sum(session.get('duration_minutes', 0) for session in self.sessions)

    def calculate_average_session_duration(self) -> float:
        """
        Calculate average session duration.
        Metric 9: Duración promedio por sesión (minutos)

        Returns:
            Average duration per session in minutes
        """
        if not self.sessions:
            return 0.0

        total_duration = self.calculate_total_duration_minutes()
        return total_duration / len(self.sessions)

    def calculate_acus_per_minute(self) -> float:
        """
        Calculate ACUs consumed per minute.
        Metric 10: ACUs por minuto

        Returns:
            Average ACUs consumed per minute
        """
        total_duration = self.calculate_total_duration_minutes()
        if total_duration == 0:
            return 0.0

        total_acus = self.calculate_total_acus()
        return total_acus / total_duration

    def calculate_cost_per_minute(self) -> float:
        """
        Calculate cost per minute.
        Metric 11: Coste por minuto

        Returns:
            Average cost per minute
        """
        acus_per_minute = self.calculate_acus_per_minute()
        return acus_per_minute * self.config.price_per_acu

    def calculate_unique_users(self) -> int:
        """
        Calculate number of unique users.
        Metric 12: Usuarios únicos

        Returns:
            Number of unique users
        """
        unique_emails = set(session.get('user_email') for session in self.sessions)
        return len(unique_emails)

    def calculate_sessions_by_task_type(self) -> Dict[str, int]:
        """
        Calculate sessions grouped by task type.
        Metric 13: Sesiones por tipo de tarea

        Returns:
            Dictionary mapping task type to session count
        """
        task_sessions = defaultdict(int)

        for session in self.sessions:
            task_type = session.get('task_type', 'unknown')
            task_sessions[task_type] += 1

        return dict(task_sessions)

    def calculate_acus_by_task_type(self) -> Dict[str, int]:
        """
        Calculate ACUs grouped by task type.
        Metric 14: ACUs por tipo de tarea

        Returns:
            Dictionary mapping task type to total ACUs consumed
        """
        task_acus = defaultdict(int)

        for session in self.sessions:
            task_type = session.get('task_type', 'unknown')
            acus = session.get('acus_consumed', 0)
            task_acus[task_type] += acus

        return dict(task_acus)

    def calculate_cost_by_task_type(self) -> Dict[str, float]:
        """
        Calculate cost grouped by task type.
        Metric 15: Coste por tipo de tarea

        Returns:
            Dictionary mapping task type to total cost
        """
        acus_by_task = self.calculate_acus_by_task_type()
        return {
            task_type: acus * self.config.price_per_acu
            for task_type, acus in acus_by_task.items()
        }

    def calculate_sessions_by_department(self) -> Dict[str, int]:
        """
        Calculate sessions grouped by department.
        Metric 16: Sesiones por departamento

        Returns:
            Dictionary mapping department to session count
        """
        user_dept_map = {
            user.get('user_email'): user.get('department', 'Unknown')
            for user in self.users
        }

        dept_sessions = defaultdict(int)
        for session in self.sessions:
            user_email = session.get('user_email')
            dept = user_dept_map.get(user_email, 'Unknown')
            dept_sessions[dept] += 1

        return dict(dept_sessions)

    def calculate_acus_by_department(self) -> Dict[str, int]:
        """
        Calculate ACUs grouped by department.
        Metric 17: ACUs por departamento

        Returns:
            Dictionary mapping department to total ACUs consumed
        """
        user_dept_map = {
            user.get('user_email'): user.get('department', 'Unknown')
            for user in self.users
        }

        dept_acus = defaultdict(int)
        for session in self.sessions:
            user_email = session.get('user_email')
            dept = user_dept_map.get(user_email, 'Unknown')
            acus = session.get('acus_consumed', 0)
            dept_acus[dept] += acus

        return dict(dept_acus)

    def calculate_cost_by_department(self) -> Dict[str, float]:
        """
        Calculate cost grouped by department.
        Metric 18: Coste por departamento

        Returns:
            Dictionary mapping department to total cost
        """
        acus_by_dept = self.calculate_acus_by_department()
        return {
            dept: acus * self.config.price_per_acu
            for dept, acus in acus_by_dept.items()
        }

    def calculate_average_cost_per_user(self) -> float:
        """
        Calculate average cost per user.
        Metric 19: Coste promedio por usuario

        Returns:
            Average cost per user
        """
        unique_users = self.calculate_unique_users()
        if unique_users == 0:
            return 0.0

        total_cost = self.calculate_total_monthly_cost()
        return total_cost / unique_users

    def calculate_efficiency_ratio(self) -> float:
        """
        Calculate efficiency ratio (ACUs per hour).
        Metric 20: Ratio de eficiencia (ACUs por hora)

        Returns:
            Average ACUs consumed per hour
        """
        total_duration_hours = self.calculate_total_duration_minutes() / 60
        if total_duration_hours == 0:
            return 0.0

        total_acus = self.calculate_total_acus()
        return total_acus / total_duration_hours

    def calculate_all_metrics(self) -> Dict[str, Any]:
        """
        Calculate all 20 foundational metrics.

        Returns:
            Dictionary containing all calculated metrics

        Raises:
            MetricsCalculationError: If any metric calculation fails.
        """
        logger.info("[CALCULATE] Starting calculation of all 20 metrics")

        if self.data is None:
            raise MetricsCalculationError(
                "No data loaded. Call load_data() before calculate_all_metrics().",
                details={"sessions": 0, "users": 0},
            )

        metric_methods = [
            ('01_total_monthly_cost', self.calculate_total_monthly_cost),
            ('02_total_acus', self.calculate_total_acus),
            ('03_cost_per_user', self.calculate_cost_per_user),
            ('04_acus_per_session', self.calculate_acus_per_session),
            ('05_average_acus_per_session', self.calculate_average_acus_per_session),
            ('06_total_sessions', self.calculate_total_sessions),
            ('07_sessions_per_user', self.calculate_sessions_per_user),
            ('08_total_duration_minutes', self.calculate_total_duration_minutes),
            ('09_average_session_duration', self.calculate_average_session_duration),
            ('10_acus_per_minute', self.calculate_acus_per_minute),
            ('11_cost_per_minute', self.calculate_cost_per_minute),
            ('12_unique_users', self.calculate_unique_users),
            ('13_sessions_by_task_type', self.calculate_sessions_by_task_type),
            ('14_acus_by_task_type', self.calculate_acus_by_task_type),
            ('15_cost_by_task_type', self.calculate_cost_by_task_type),
            ('16_sessions_by_department', self.calculate_sessions_by_department),
            ('17_acus_by_department', self.calculate_acus_by_department),
            ('18_cost_by_department', self.calculate_cost_by_department),
            ('19_average_cost_per_user', self.calculate_average_cost_per_user),
            ('20_efficiency_ratio', self.calculate_efficiency_ratio),
        ]

        metrics = {}
        failed_metrics = []

        for metric_key, method in metric_methods:
            try:
                metrics[metric_key] = method()
            except Exception as exc:
                logger.error(
                    "[CALCULATE] Failed to calculate metric '%s': %s",
                    metric_key,
                    exc,
                )
                failed_metrics.append(metric_key)
                metrics[metric_key] = None

        if failed_metrics:
            logger.warning(
                "[CALCULATE] %d of %d metrics failed: %s",
                len(failed_metrics),
                len(metric_methods),
                ", ".join(failed_metrics),
            )

        metrics_result = {
            'config': self.config.to_dict(),
            'reporting_period': self.data.get('reporting_period', {}),
            'metrics': metrics,
        }

        logger.info(
            "[CALCULATE] Metrics calculation complete: %d succeeded, %d failed",
            len(metric_methods) - len(failed_metrics),
            len(failed_metrics),
        )
        return metrics_result


def main():
    """Example usage of the MetricsCalculator."""
    config = MetricsConfig(price_per_acu=0.05, currency='USD')

    calculator = MetricsCalculator(config)
    calculator.load_data('raw_usage_data.json')

    all_metrics = calculator.calculate_all_metrics()

    print(json.dumps(all_metrics, indent=2, default=str))


if __name__ == '__main__':
    main()
