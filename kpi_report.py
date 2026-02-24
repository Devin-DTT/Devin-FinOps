"""
KPI Report Orchestrator.

Fetches all data sources (Devin API, GitHub API, Jira API, audit logs),
computes all KPIs via kpi_engine, and exports results to JSON and HTML dashboard.

Usage:
    python kpi_report.py --start 2025-01-01 --end 2025-12-31
"""

import argparse
import json
import logging
from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional

import data_adapter
from github_adapter import (
    fetch_all_pr_data,
    parse_pr_url,
    fetch_code_scanning_alerts,
    fetch_secret_scanning_alerts_org,
    fetch_dependabot_alerts,
    fetch_dependency_review,
)
from jira_adapter import (
    extract_jira_keys_from_pr,
    fetch_jira_data_for_keys,
)
from kpi_engine import calculate_all_kpis
from kpi_dashboard import generate_kpi_dashboard

logger = logging.getLogger(__name__)


def _extract_sessions_list(all_api_data: Dict[str, Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Extract sessions list from the enterprise sessions endpoint response."""
    sessions_endpoint = all_api_data.get("sessions_list", {})
    if sessions_endpoint.get("status_code") != 200:
        logger.warning("sessions_list endpoint returned status %s", sessions_endpoint.get("status_code"))
        return []

    response = sessions_endpoint.get("response", {})
    if isinstance(response, str):
        try:
            response = json.loads(response)
        except json.JSONDecodeError:
            return []

    if isinstance(response, list):
        return response

    if isinstance(response, dict):
        return response.get("sessions", response.get("data", response.get("items", [])))

    return []


def _extract_metrics(all_api_data: Dict[str, Dict[str, Any]], key: str) -> Dict[str, Any]:
    """Extract metrics dict from an API response."""
    endpoint = all_api_data.get(key, {})
    if endpoint.get("status_code") != 200:
        return {}

    response = endpoint.get("response", {})
    if isinstance(response, str):
        try:
            response = json.loads(response)
        except json.JSONDecodeError:
            return {}

    return response if isinstance(response, dict) else {}


def _extract_pr_urls(sessions_list: List[Dict[str, Any]]) -> List[str]:
    """Extract unique PR URLs from sessions list."""
    urls = set()
    for session in sessions_list:
        pr_list = session.get("pull_requests", [])
        for pr_ref in pr_list:
            if isinstance(pr_ref, dict):
                url = pr_ref.get("url", "")
            else:
                url = str(pr_ref)
            if url and "github.com" in url:
                urls.add(url)
    logger.info("Extracted %d unique PR URLs from sessions", len(urls))
    return list(urls)


def _extract_repos_from_pr_data(github_pr_data: Dict[str, Dict[str, Any]]) -> List[str]:
    """Extract unique owner/repo pairs from GitHub PR data."""
    repos = set()
    for pr_url, pr_data in github_pr_data.items():
        owner = pr_data.get("owner", "")
        repo = pr_data.get("repo", "")
        if owner and repo:
            repos.add(f"{owner}/{repo}")
    return list(repos)


def _extract_audit_logs(all_api_data: Dict[str, Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Extract audit logs from the audit-logs endpoint response."""
    audit_endpoint = all_api_data.get("audit_logs", {})
    if audit_endpoint.get("status_code") != 200:
        logger.warning("audit_logs endpoint returned status %s", audit_endpoint.get("status_code"))
        return []

    response = audit_endpoint.get("response", {})
    if isinstance(response, str):
        try:
            response = json.loads(response)
        except json.JSONDecodeError:
            return []

    if isinstance(response, list):
        return response

    if isinstance(response, dict):
        return response.get("events", response.get("data", response.get("items", [])))

    return []


def _fetch_security_data(
    repos: List[str],
    github_pr_data: Dict[str, Dict[str, Any]],
    github_org: str,
) -> tuple:
    """Fetch security-related data from GitHub APIs."""
    code_scanning: Dict[str, List[Dict[str, Any]]] = {}
    dependabot: Dict[str, List[Dict[str, Any]]] = {}
    dependency_reviews: Dict[str, Any] = {}

    for repo_full in repos:
        parts = repo_full.split("/")
        if len(parts) != 2:
            continue
        owner, repo = parts

        logger.info("Fetching security data for %s/%s", owner, repo)
        code_scanning[repo_full] = fetch_code_scanning_alerts(owner, repo)
        dependabot[repo_full] = fetch_dependabot_alerts(owner, repo)

    for pr_url, pr_data in github_pr_data.items():
        details = pr_data.get("details", {})
        if not details:
            continue
        owner = pr_data.get("owner", "")
        repo = pr_data.get("repo", "")
        base_sha = details.get("base", {}).get("sha", "")
        head_sha = details.get("head", {}).get("sha", "")
        if owner and repo and base_sha and head_sha:
            dep_review = fetch_dependency_review(owner, repo, base_sha, head_sha)
            if dep_review:
                dependency_reviews[pr_url] = dep_review

    secret_scanning = []
    if github_org:
        secret_scanning = fetch_secret_scanning_alerts_org(github_org)

    return code_scanning, secret_scanning, dependabot, dependency_reviews


def run_kpi_report(
    start_date: str,
    end_date: str,
    github_org: str = "",
    output_json: str = "kpi_results.json",
    output_html: str = "kpi_dashboard.html",
) -> Dict[str, Dict[str, Any]]:
    """Main KPI report generation pipeline."""
    data_adapter.setup_logging()

    logger.info("=" * 60)
    logger.info("KPI Report Generator")
    logger.info("=" * 60)
    logger.info("Date range: %s to %s", start_date, end_date)

    kpi_endpoints = {
        "metrics_sessions": "/metrics/sessions",
        "metrics_prs": "/metrics/prs",
        "sessions_list": "/sessions",
        "audit_logs": "/audit-logs",
    }

    params_by_endpoint = {
        "metrics_sessions": {"start_date": start_date, "end_date": end_date},
        "metrics_prs": {"start_date": start_date, "end_date": end_date},
        "sessions_list": {"created_date_from": start_date, "created_date_to": end_date},
    }

    logger.info("Step 1: Fetching data from Devin Enterprise API...")
    try:
        all_api_data = data_adapter.fetch_api_data(kpi_endpoints, params_by_endpoint=params_by_endpoint)
    except Exception as e:
        logger.error("Failed to fetch Devin API data: %s", e)
        all_api_data = {}

    sessions_metrics = _extract_metrics(all_api_data, "metrics_sessions")
    pr_metrics = _extract_metrics(all_api_data, "metrics_prs")
    sessions_list = _extract_sessions_list(all_api_data)
    audit_logs = _extract_audit_logs(all_api_data)

    logger.info("Sessions metrics: %s", sessions_metrics)
    logger.info("PR metrics: %s", pr_metrics)
    logger.info("Sessions list: %d sessions", len(sessions_list))
    logger.info("Audit logs: %d entries", len(audit_logs))

    logger.info("Step 2: Extracting PR URLs and fetching GitHub data...")
    pr_urls = _extract_pr_urls(sessions_list)
    github_pr_data = fetch_all_pr_data(pr_urls)

    logger.info("Step 3: Fetching security data from GitHub...")
    repos = _extract_repos_from_pr_data(github_pr_data)
    code_scanning, secret_scanning, dependabot, dependency_reviews = _fetch_security_data(
        repos, github_pr_data, github_org
    )

    logger.info("Step 4: Extracting Jira keys and fetching Jira data...")
    all_jira_keys = set()
    for pr_url, pr_data in github_pr_data.items():
        details = pr_data.get("details", {})
        if details:
            keys = extract_jira_keys_from_pr(details)
            all_jira_keys.update(keys)

    jira_data = fetch_jira_data_for_keys(list(all_jira_keys))

    logger.info("Step 5: Calculating all KPIs...")
    all_kpis = calculate_all_kpis(
        sessions_metrics=sessions_metrics,
        pr_metrics=pr_metrics,
        sessions_list=sessions_list,
        github_pr_data=github_pr_data,
        code_scanning_alerts=code_scanning,
        secret_scanning_alerts=secret_scanning,
        dependabot_alerts=dependabot,
        dependency_reviews=dependency_reviews,
        jira_data=jira_data,
        audit_logs=audit_logs,
    )

    logger.info("Step 6: Exporting results...")
    serializable_kpis = {}
    for k, v in all_kpis.items():
        serializable_kpis[k] = {
            "value": v.get("value"),
            "formula": v.get("formula", ""),
            "category": v.get("category", ""),
            "implementable": v.get("implementable", True),
            "sources_used": v.get("sources_used", []),
        }

    try:
        with open(output_json, "w", encoding="utf-8") as f:
            json.dump(
                {
                    "generated_at": datetime.now().isoformat(),
                    "date_range": {"start": start_date, "end": end_date},
                    "total_kpis": len(serializable_kpis),
                    "kpis": serializable_kpis,
                },
                f,
                indent=2,
                default=str,
            )
        logger.info("KPI results exported to %s", output_json)
    except Exception as e:
        logger.error("Failed to export KPI JSON: %s", e)

    try:
        generate_kpi_dashboard(all_kpis, start_date, end_date, output_html)
        logger.info("KPI dashboard exported to %s", output_html)
    except Exception as e:
        logger.error("Failed to generate KPI dashboard: %s", e)

    logger.info("=" * 60)
    logger.info("KPI Report complete: %d KPIs calculated", len(all_kpis))
    logger.info("=" * 60)

    return all_kpis


def main():
    parser = argparse.ArgumentParser(description="Generate KPI metrics report")
    parser.add_argument(
        "--start",
        type=str,
        default=None,
        help="Start date (YYYY-MM-DD). Defaults to 12 months ago.",
    )
    parser.add_argument(
        "--end",
        type=str,
        default=None,
        help="End date (YYYY-MM-DD). Defaults to today.",
    )
    parser.add_argument(
        "--org",
        type=str,
        default="",
        help="GitHub organization name for org-level security alerts.",
    )
    parser.add_argument(
        "--output-json",
        type=str,
        default="kpi_results.json",
        help="Output JSON file path.",
    )
    parser.add_argument(
        "--output-html",
        type=str,
        default="kpi_dashboard.html",
        help="Output HTML dashboard file path.",
    )
    args = parser.parse_args()

    if not args.end:
        args.end = datetime.now().strftime("%Y-%m-%d")
    if not args.start:
        end_dt = datetime.strptime(args.end, "%Y-%m-%d")
        start_dt = end_dt - timedelta(days=365)
        args.start = start_dt.strftime("%Y-%m-%d")

    run_kpi_report(
        start_date=args.start,
        end_date=args.end,
        github_org=args.org,
        output_json=args.output_json,
        output_html=args.output_html,
    )


if __name__ == "__main__":
    try:
        main()
        print("\n+ SUCCESS: KPI Report generated.")
    except Exception as e:
        logger.error("KPI Report failed: %s", e, exc_info=True)
        print(f"\n- FAILED: {e}")
        raise
