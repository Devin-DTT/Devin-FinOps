"""
Generate FinOps Metrics Report
Phase 3: Reporting and Output Generation

This script orchestrates the FinOps report generation pipeline:
1. Fetches data from the Cognition Enterprise API
2. Transforms raw data using data_transformer
3. Calculates metrics using metrics_service and metrics_calculator
4. Exports results to CSV, Excel, and HTML dashboard
"""

import json
import logging
import argparse
from datetime import datetime, timedelta
from typing import Dict, Any, List, Optional, Tuple
from metrics_calculator import MetricsCalculator
from config import MetricsConfig
import data_adapter
from export_metrics import export_daily_acus_to_csv
from export_metrics_summary import export_summary_to_excel
from html_dashboard import generate_html_dashboard
from metrics_service import (
    calculate_monthly_acus,
    calculate_base_metrics,
    calculate_finops_metrics,
)
from data_transformer import (
    transform_raw_data,
    create_summary_csv,
    generate_business_summary,
    generate_consumption_summary,
)

logger = logging.getLogger(__name__)


TARGET_USER_ID = "email|68edea2414c1686557f8d5a6"

API_ENDPOINTS = {
    'consumption_cycles': '/consumption/cycles',
    'consumption_daily': '/consumption/daily',
    'metrics_prs': '/metrics/prs',
    'metrics_sessions': '/metrics/sessions',
    'metrics_searches': '/metrics/searches',
    'metrics_usage': '/metrics/usage',
    'roles': '/roles',
    'members': '/members',
    'members_user': f'/members/{TARGET_USER_ID}',
    'members_user_orgs': f'/members/{TARGET_USER_ID}/organizations',
    'groups': '/groups',
    'api_keys': '/api-keys',
    'playbooks': '/playbooks'
}

FALLBACK_LOOKBACK_DAYS = 1825
RAW_DATA_FILE = 'raw_usage_data.json'
TRANSFORMED_DATA_FILE = 'transformed_usage_data.json'


def _fallback_date_range(end_date_str: str) -> Tuple[str, str]:
    end_dt = datetime.strptime(end_date_str, '%Y-%m-%d')
    start_for_trend = (end_dt - timedelta(days=FALLBACK_LOOKBACK_DAYS)).strftime('%Y-%m-%d')
    return start_for_trend, end_date_str


def _parse_consumption_response(consumption_endpoint: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    if consumption_endpoint.get('status_code') != 200:
        return None
    response_data = consumption_endpoint.get('response', {})
    if isinstance(response_data, str):
        response_data = json.loads(response_data)
    if isinstance(response_data, dict):
        return response_data
    return None


class ReportOrchestrator:
    """Coordinates all phases of the FinOps report generation pipeline."""

    def __init__(self, start_date: str, end_date: str):
        self.start_date = start_date
        self.end_date = end_date
        self.config = MetricsConfig(price_per_acu=0.05, currency='USD')

        self.all_api_data: Optional[Dict[str, Any]] = None
        self.daily_chart_data: List[Dict[str, Any]] = []
        self.user_chart_data: List[Dict[str, Any]] = []
        self.transformed_data: Dict[str, Any] = {}
        self.all_metrics: Dict[str, Any] = {}
        self.user_breakdown_list: List[Dict[str, Any]] = []
        self.org_breakdown_summary: Dict[str, Any] = {}
        self.base_data: Dict[str, Any] = {}
        self.finops_metrics: Dict[str, Any] = {}
        self.summary_data: Dict[str, Any] = {}
        self.monthly_consumption_history: List[Dict[str, Any]] = []

    def run(self) -> None:
        """Execute the full report generation pipeline."""
        logger.info("=" * 60)
        logger.info("FinOps Metrics Report Generator - Phase 3")
        logger.info("=" * 60)
        logger.info("Date range: %s to %s", self.start_date, self.end_date)

        start_for_trend, end_for_trend = self.determine_date_range()
        self.fetch_api_data(start_for_trend, end_for_trend)
        self.fetch_and_transform_sessions()
        self.calculate_session_metrics()
        self.prepare_user_breakdown()
        self.aggregate_org_breakdown()
        self.calculate_finops_data()
        self.generate_summaries()
        self.export_all_reports()

    def determine_date_range(self) -> Tuple[str, str]:
        """Phase 1: Determine dynamic date range from consumption_cycles."""
        logger.info("Step 1: Fetching consumption_cycles to determine dynamic date range...")
        cycles_endpoint = {'consumption_cycles': API_ENDPOINTS['consumption_cycles']}

        try:
            cycles_data = data_adapter.fetch_api_data(cycles_endpoint)

            if not (cycles_data and 'consumption_cycles' in cycles_data):
                logger.warning("Failed to fetch consumption_cycles, using fallback date range")
                return _fallback_date_range(self.end_date)

            cycles_response = cycles_data['consumption_cycles']

            if cycles_response.get('status_code') != 200:
                logger.warning(
                    "consumption_cycles returned status %s, using fallback",
                    cycles_response.get('status_code'),
                )
                return _fallback_date_range(self.end_date)

            cycles_list = cycles_response.get('response', [])
            if isinstance(cycles_list, str):
                cycles_list = json.loads(cycles_list)

            if not (isinstance(cycles_list, list) and len(cycles_list) > 0):
                logger.warning("consumption_cycles returned empty list, using fallback date range")
                return _fallback_date_range(self.end_date)

            logger.info("Found %d consumption cycles", len(cycles_list))

            all_starts: List[datetime] = []
            all_ends: List[datetime] = []

            for cycle in cycles_list:
                if 'start' in cycle:
                    start_dt = datetime.fromisoformat(cycle['start'].replace('Z', '+00:00'))
                    all_starts.append(start_dt)
                if 'end' in cycle:
                    end_dt = datetime.fromisoformat(cycle['end'].replace('Z', '+00:00'))
                    all_ends.append(end_dt)

            if not (all_starts and all_ends):
                logger.warning("No valid dates found in consumption_cycles, using fallback")
                return _fallback_date_range(self.end_date)

            earliest_start = min(all_starts)
            latest_end = max(all_ends)
            start_for_trend = earliest_start.strftime('%Y-%m-%d')
            end_for_trend = latest_end.strftime('%Y-%m-%d')

            logger.info("Dynamic date range determined from consumption_cycles:")
            logger.info("  Earliest start: %s", start_for_trend)
            logger.info("  Latest end: %s", end_for_trend)
            return start_for_trend, end_for_trend

        except Exception as e:
            logger.error("Error fetching consumption_cycles: %s", e)
            logger.info("Using fallback date range")
            return _fallback_date_range(self.end_date)

    def fetch_api_data(self, start_for_trend: str, end_for_trend: str) -> None:
        """Phase 2: Fetch data from all Enterprise API endpoints."""
        params_by_endpoint = {
            'consumption_daily': {
                'start_date': start_for_trend,
                'end_date': end_for_trend,
            }
        }
        logger.info(
            "Step 2: Fetching consumption_daily with dynamic date range: %s to %s",
            start_for_trend,
            end_for_trend,
        )

        try:
            self.all_api_data = data_adapter.fetch_api_data(
                API_ENDPOINTS, params_by_endpoint=params_by_endpoint,
            )
            data_adapter.save_raw_data(self.all_api_data)
            create_summary_csv(self.all_api_data)
            logger.info("Multi-endpoint data fetch completed successfully")

            self._extract_chart_data()

        except Exception as e:
            logger.error("Failed to fetch multi-endpoint data: %s", e)
            logger.info("Continuing with report generation using existing data if available")

    def _extract_chart_data(self) -> None:
        """Extract daily and user chart data from consumption_daily response."""
        if not self.all_api_data:
            return

        consumption_endpoint = self.all_api_data.get('consumption_daily', {})
        response_data = _parse_consumption_response(consumption_endpoint)
        if response_data is None:
            return

        try:
            consumption_by_date = response_data.get('consumption_by_date', {})
            for date, acus in consumption_by_date.items():
                self.daily_chart_data.append({'Date': date, 'ACUs': acus})

            consumption_by_user = response_data.get('consumption_by_user', {})
            for user_id, acus in consumption_by_user.items():
                self.user_chart_data.append({'User ID': user_id, 'ACUs': acus})

            logger.info(
                "Prepared chart data: %d daily records, %d users",
                len(self.daily_chart_data),
                len(self.user_chart_data),
            )
        except (json.JSONDecodeError, AttributeError, TypeError) as e:
            logger.warning("Failed to prepare chart data: %s", e)

    def fetch_and_transform_sessions(self) -> None:
        """Phase 3: Fetch raw session data and transform it."""
        logger.info("Fetching data from Cognition API...")
        try:
            data_adapter.main(start_date=self.start_date, end_date=self.end_date)
        except Exception as e:
            logger.error("Failed to fetch data from API: %s", e)
            logger.info("Attempting to use existing raw_usage_data.json if available")

        logger.info("Loading raw data from %s...", RAW_DATA_FILE)
        with open(RAW_DATA_FILE, 'r') as f:
            raw_sessions = json.load(f)

        logger.info("Loaded %d raw sessions", len(raw_sessions))

        self.transformed_data = transform_raw_data(
            raw_sessions, start_date=self.start_date, end_date=self.end_date,
        )

        with open(TRANSFORMED_DATA_FILE, 'w') as f:
            json.dump(self.transformed_data, f, indent=2)
        logger.info("Transformed data saved to %s", TRANSFORMED_DATA_FILE)

    def calculate_session_metrics(self) -> None:
        """Phase 4: Calculate all 20 session-based metrics."""
        logger.info(
            "Configuration: price_per_acu=%s, currency=%s",
            self.config.price_per_acu,
            self.config.currency,
        )

        calculator = MetricsCalculator(self.config)
        calculator.load_data(TRANSFORMED_DATA_FILE)
        logger.info("Data loaded into MetricsCalculator")

        logger.info("Calculating all 20 metrics...")
        self.all_metrics = calculator.calculate_all_metrics()

    def prepare_user_breakdown(self) -> None:
        """Phase 5: Prepare per-user consumption breakdown with org mappings."""
        user_org_mappings: Dict[str, Any] = {}

        if self.all_api_data:
            consumption_endpoint = self.all_api_data.get('consumption_daily', {})
            response_data = _parse_consumption_response(consumption_endpoint)
            if response_data is not None:
                try:
                    self.user_breakdown_list = self._build_user_breakdown_from_api(
                        response_data, user_org_mappings,
                    )
                except (json.JSONDecodeError, AttributeError, TypeError) as e:
                    logger.warning("Failed to prepare user breakdown list from API: %s", e)

        if not self.user_breakdown_list:
            self.user_breakdown_list = self._build_user_breakdown_from_sessions()

    def _build_user_breakdown_from_api(
        self,
        response_data: Dict[str, Any],
        user_org_mappings: Dict[str, Any],
    ) -> List[Dict[str, Any]]:
        """Build user breakdown list from API consumption data."""
        consumption_by_user = response_data.get('consumption_by_user', {})

        unique_user_ids = list(consumption_by_user.keys())
        logger.info("Extracted %d unique user IDs from consumption data", len(unique_user_ids))

        if unique_user_ids:
            logger.info("Fetching organization mappings for all users...")
            user_org_mappings.update(
                data_adapter.fetch_user_organization_mappings(unique_user_ids),
            )
            logger.info("Organization mappings fetched for %d users", len(user_org_mappings))

        breakdown: List[Dict[str, Any]] = []
        for user_id, acus in consumption_by_user.items():
            cost_usd = round(acus * self.config.price_per_acu, 2)
            org_info = user_org_mappings.get(user_id, {})
            breakdown.append({
                'User ID': user_id,
                'ACUs Consumed': round(acus, 2),
                'Cost (USD)': cost_usd,
                'Organization ID': org_info.get('organization_id', 'Unmapped'),
                'Organization Name': org_info.get('organization_name', 'Unmapped'),
            })

        logger.info("Prepared user breakdown list with %d users from API data", len(breakdown))
        return breakdown

    def _build_user_breakdown_from_sessions(self) -> List[Dict[str, Any]]:
        """Fallback: build user breakdown from transformed session data."""
        logger.info("API data not available, aggregating user consumption from transformed sessions")
        user_acus_map: Dict[str, float] = {}
        for session in self.transformed_data.get('sessions', []):
            user_email = session.get('user_email', 'unknown')
            acus = session.get('acus_consumed', 0)
            if user_email not in user_acus_map:
                user_acus_map[user_email] = 0
            user_acus_map[user_email] += acus

        breakdown: List[Dict[str, Any]] = []
        for user_email, total_acus in user_acus_map.items():
            cost_usd = round(total_acus * self.config.price_per_acu, 2)
            breakdown.append({
                'User ID': user_email,
                'ACUs Consumed': round(total_acus, 2),
                'Cost (USD)': cost_usd,
                'Organization ID': 'Unmapped',
                'Organization Name': 'Unmapped',
            })

        logger.info(
            "Prepared user breakdown list with %d users from transformed data", len(breakdown),
        )
        return breakdown

    def aggregate_org_breakdown(self) -> None:
        """Phase 6: Aggregate user data into organization-level breakdown."""
        logger.info("Aggregating data by organization...")
        org_summary: Dict[str, Dict[str, Any]] = {}

        for user_data in self.user_breakdown_list:
            org_id = user_data.get('Organization ID', 'Unmapped')
            org_name = user_data.get('Organization Name', 'Unmapped')
            acus = user_data.get('ACUs Consumed', 0)
            cost = user_data.get('Cost (USD)', 0)

            if org_id not in org_summary:
                org_summary[org_id] = {
                    'Organization ID': org_id,
                    'Organization Name': org_name,
                    'Total ACUs Consumed': 0.0,
                    'Total Cost (USD)': 0.0,
                }

            org_summary[org_id]['Total ACUs Consumed'] += acus
            org_summary[org_id]['Total Cost (USD)'] += cost

        for org_id in org_summary:
            org_summary[org_id]['Total ACUs Consumed'] = round(
                org_summary[org_id]['Total ACUs Consumed'], 2,
            )
            org_summary[org_id]['Total Cost (USD)'] = round(
                org_summary[org_id]['Total Cost (USD)'], 2,
            )

        self.org_breakdown_summary = org_summary
        logger.info("Organization aggregation complete: %d organizations", len(org_summary))

    def calculate_finops_data(self) -> None:
        """Phase 7: Calculate base metrics and FinOps metrics with traceability."""
        logger.info("Calculating base metrics with traceability...")
        self.base_data = calculate_base_metrics(self.all_api_data, self.config)
        logger.info("Base metrics calculated: %d metrics", len(self.base_data))

        logger.info("Calculating FinOps metrics with traceability...")
        self.finops_metrics = calculate_finops_metrics(self.base_data, end_date=self.end_date)
        logger.info("FinOps metrics calculated: %d metrics", len(self.finops_metrics))

    def generate_summaries(self) -> None:
        """Phase 8: Generate consumption and business summaries."""
        generate_consumption_summary(self.all_api_data, self.all_metrics)
        self.summary_data = generate_business_summary(
            self.all_metrics, self.config, self.all_api_data,
        )

    def export_all_reports(self) -> None:
        """Phase 9: Export reports in all formats (CSV, Excel, HTML)."""
        self._export_csv()
        self._prepare_monthly_history()
        self._export_excel()
        self._export_html()

    def _export_csv(self) -> None:
        """Export daily ACU consumption to CSV."""
        export_daily_acus_to_csv()

    def _prepare_monthly_history(self) -> None:
        """Prepare monthly consumption history from daily data."""
        consumption_by_date = self.base_data.get('consumption_by_date', {}).get('value', {})
        if not consumption_by_date:
            logger.warning("No consumption_by_date data available for monthly history")
            return

        logger.info("Preparing monthly consumption history...")
        unique_months: set = set()
        for date_str in consumption_by_date.keys():
            if len(date_str) >= 7:
                unique_months.add(date_str[:7])

        for month_prefix in sorted(unique_months):
            monthly_acus = calculate_monthly_acus(consumption_by_date, month_prefix)
            self.monthly_consumption_history.append({
                'Mes': month_prefix,
                'Consumo Total (ACUs)': round(monthly_acus, 2),
            })

        logger.info(
            "Prepared monthly consumption history with %d months",
            len(self.monthly_consumption_history),
        )

    def _export_excel(self) -> None:
        """Export full summary to Excel workbook."""
        export_summary_to_excel(
            self.all_metrics,
            self.config,
            self.all_api_data,
            summary_data=self.summary_data,
            user_breakdown_list=self.user_breakdown_list,
            org_breakdown_summary=self.org_breakdown_summary,
            finops_metrics=self.finops_metrics,
            monthly_consumption_history=self.monthly_consumption_history,
        )

    def _export_html(self) -> None:
        """Export interactive HTML dashboard."""
        generate_html_dashboard(
            self.summary_data, self.daily_chart_data, self.user_chart_data,
        )


def main():
    """Main execution function for report generation."""
    parser = argparse.ArgumentParser(description='Generate FinOps metrics report')
    parser.add_argument(
        '--start',
        type=str,
        default='2025-10-01',
        help='Start date for reporting period (YYYY-MM-DD)'
    )
    parser.add_argument(
        '--end',
        type=str,
        default='2025-10-31',
        help='End date for reporting period (YYYY-MM-DD)'
    )
    args = parser.parse_args()

    data_adapter.setup_logging()

    orchestrator = ReportOrchestrator(start_date=args.start, end_date=args.end)
    orchestrator.run()


if __name__ == '__main__':
    try:
        main()
        print("\n+ SUCCESS: All API data saved and FinOps Summary computed.")
    except Exception as e:
        logger.error(f"Error generating report: {e}", exc_info=True)
        print(f"\n- FAILED: {e}")
        raise
