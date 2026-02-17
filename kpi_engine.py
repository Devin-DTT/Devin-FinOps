"""
KPI Engine - Calculates all KPIs across 6 categories.

Categories:
  1. ADOPCION / UTILIZACION
  2. PRODUCTIVIDAD / FLUJO (GitHub)
  3. CALIDAD / CI / TESTING
  4. SEGURIDAD
  5. JIRA
  6. GOBIERNO / AUDITORIA

Each KPI returns a dict with: value, formula, sources_used, category, implementable.
"""

import logging
import re
from typing import Dict, List, Any, Optional
from datetime import datetime
from collections import defaultdict

from jira_adapter import (
    extract_jira_keys_from_pr,
    compute_cycle_time,
    check_reopen,
)

logger = logging.getLogger(__name__)

TEST_FILE_PATTERNS = [
    r"test[_/]",
    r"tests[_/]",
    r"__tests__",
    r"\.test\.",
    r"\.spec\.",
    r"_test\.",
    r"_spec\.",
    r"test_",
    r"spec_",
]
TEST_REGEX = re.compile("|".join(TEST_FILE_PATTERNS), re.IGNORECASE)


def _metric(value, formula, sources, category, implementable=True):
    return {
        "value": value,
        "formula": formula,
        "sources_used": sources if sources else [],
        "category": category,
        "implementable": implementable,
    }


def _safe_div(numerator, denominator, default=0):
    if denominator and denominator != 0:
        return numerator / denominator
    return default


def _parse_iso(dt_str: Optional[str]) -> Optional[datetime]:
    if not dt_str:
        return None
    try:
        return datetime.fromisoformat(dt_str.replace("Z", "+00:00"))
    except (ValueError, TypeError):
        return None


# =========================================================================
# CATEGORY 1: ADOPCION / UTILIZACION
# =========================================================================

def calculate_adoption_kpis(
    sessions_metrics: Dict[str, Any],
    pr_metrics: Dict[str, Any],
    sessions_list: List[Dict[str, Any]],
    github_pr_data: Dict[str, Dict[str, Any]],
) -> Dict[str, Dict[str, Any]]:
    """Calculate Adoption / Utilization KPIs."""
    cat = "ADOPCION / UTILIZACION"
    kpis: Dict[str, Dict[str, Any]] = {}

    sessions_count = sessions_metrics.get("sessions_count", 0)
    kpis["Devin sessions count"] = _metric(
        sessions_count,
        "count(sessions) in time_window",
        [{"source_path": "GET /v2/enterprise/metrics/sessions", "raw_value": sessions_count}],
        cat,
    )

    status_counts: Dict[str, int] = defaultdict(int)
    for s in sessions_list:
        status = s.get("status", s.get("status_enum", "unknown"))
        status_counts[status] += 1
    kpis["Devin sessions by status"] = _metric(
        dict(status_counts),
        "count_by(status) over sessions in time_window",
        [{"source_path": "GET /v2/enterprise/sessions", "raw_value": dict(status_counts)}],
        cat,
    )

    unique_users = set()
    for s in sessions_list:
        uid = s.get("user_id", s.get("created_by", ""))
        if uid:
            unique_users.add(uid)
    active_users = len(unique_users)
    kpis["Active Devin users"] = _metric(
        active_users,
        "count(distinct user_id) over sessions in time_window",
        [{"source_path": "GET /v2/enterprise/sessions", "raw_value": active_users}],
        cat,
    )

    total_acus = 0.0
    for s in sessions_list:
        total_acus += s.get("acus_consumed", s.get("acu_consumed", 0))
    kpis["ACU consumption (total)"] = _metric(
        round(total_acus, 2),
        "sum(s.acus_consumed) over sessions in time_window",
        [{"source_path": "GET /v2/enterprise/sessions", "raw_value": round(total_acus, 2)}],
        cat,
    )

    session_count_for_avg = len(sessions_list) if sessions_list else sessions_count
    acu_per_session = round(_safe_div(total_acus, session_count_for_avg), 2)
    kpis["ACU per session"] = _metric(
        acu_per_session,
        "sum(acus_consumed)/count(sessions)",
        [
            {"source_path": "GET /v2/enterprise/sessions", "raw_value": round(total_acus, 2)},
            {"source_path": "sessions count", "raw_value": session_count_for_avg},
        ],
        cat,
    )

    prs_opened = pr_metrics.get("prs_opened", 0)
    prs_merged = pr_metrics.get("prs_merged", 0)
    prs_closed = pr_metrics.get("prs_closed", 0)
    kpis["PRs opened/merged/closed (aggregate)"] = _metric(
        {"opened": prs_opened, "merged": prs_merged, "closed": prs_closed},
        "Devin enterprise PR metrics counters in time_window",
        [{"source_path": "GET /v2/enterprise/metrics/prs", "raw_value": {"opened": prs_opened, "merged": prs_merged, "closed": prs_closed}}],
        cat,
    )

    pr_success_raw = round(_safe_div(prs_merged, prs_opened) * 100, 2) if prs_opened else 0
    pr_success_rate = min(pr_success_raw, 100.0)
    kpis["Devin PR success rate"] = _metric(
        pr_success_rate,
        "min(prs_merged / prs_opened * 100, 100) (in same window)",
        [
            {"source_path": "GET /v2/enterprise/metrics/prs", "raw_value": {"opened": prs_opened, "merged": prs_merged, "raw_pct": pr_success_raw}},
        ],
        cat,
    )

    merged_pr_acus = 0.0
    merged_count = 0
    for s in sessions_list:
        pr_list = s.get("pull_requests", [])
        if not pr_list:
            continue
        for pr_ref in pr_list:
            pr_url = pr_ref.get("url", "") if isinstance(pr_ref, dict) else str(pr_ref)
            gh_data = github_pr_data.get(pr_url, {})
            details = gh_data.get("details", {})
            if details.get("merged_at"):
                merged_pr_acus += s.get("acus_consumed", s.get("acu_consumed", 0))
                merged_count += 1
                break

    acu_per_merged_pr = round(_safe_div(merged_pr_acus, merged_count), 2)
    kpis["ACU per merged PR"] = _metric(
        acu_per_merged_pr,
        "sum(acus_consumed for sessions with >=1 merged PR) / count(merged PRs)",
        [
            {"source_path": "GET /v2/enterprise/sessions + GitHub PR merged_at", "raw_value": {"acus": round(merged_pr_acus, 2), "merged_count": merged_count}},
        ],
        cat,
    )

    return kpis


# =========================================================================
# CATEGORY 2: PRODUCTIVIDAD / FLUJO (GitHub)
# =========================================================================

def calculate_productivity_kpis(
    sessions_list: List[Dict[str, Any]],
    github_pr_data: Dict[str, Dict[str, Any]],
) -> Dict[str, Dict[str, Any]]:
    """Calculate Productivity / Flow KPIs from GitHub PR data."""
    cat = "PRODUCTIVIDAD / FLUJO"
    kpis: Dict[str, Dict[str, Any]] = {}

    lead_times: List[float] = []
    first_review_times: List[float] = []
    change_request_counts: List[int] = []
    files_changed_list: List[int] = []
    additions_list: List[int] = []
    deletions_list: List[int] = []
    commits_per_pr: List[int] = []
    devin_pr_urls = set()

    for pr_url, pr_data in github_pr_data.items():
        details = pr_data.get("details", {})
        if not details:
            continue

        devin_pr_urls.add(pr_url)

        created_at = _parse_iso(details.get("created_at"))
        merged_at = _parse_iso(details.get("merged_at"))
        if created_at and merged_at:
            delta_hours = (merged_at - created_at).total_seconds() / 3600.0
            lead_times.append(delta_hours)

        reviews = pr_data.get("reviews", [])
        if reviews and created_at:
            review_times = []
            for r in reviews:
                submitted = _parse_iso(r.get("submitted_at"))
                if submitted:
                    review_times.append(submitted)
            if review_times:
                first_review = min(review_times)
                delta_hours = (first_review - created_at).total_seconds() / 3600.0
                first_review_times.append(delta_hours)

        cr_count = sum(1 for r in reviews if r.get("state") == "CHANGES_REQUESTED")
        change_request_counts.append(cr_count)

        files_changed_list.append(details.get("changed_files", 0))
        additions_list.append(details.get("additions", 0))
        deletions_list.append(details.get("deletions", 0))

        commits = pr_data.get("commits", [])
        commits_per_pr.append(len(commits))

    avg_lead_time = round(_safe_div(sum(lead_times), len(lead_times)), 2) if lead_times else 0
    kpis["Lead time (PR created -> merged)"] = _metric(
        avg_lead_time,
        "avg(pr.merged_at - pr.created_at) in hours",
        [{"source_path": "GitHub PR API (created_at, merged_at)", "raw_value": {"count": len(lead_times), "avg_hours": avg_lead_time}}],
        cat,
    )

    avg_first_review = round(_safe_div(sum(first_review_times), len(first_review_times)), 2) if first_review_times else 0
    kpis["Time to first review"] = _metric(
        avg_first_review,
        "avg(first_review.submitted_at - pr.created_at) in hours",
        [{"source_path": "GitHub PR Reviews API", "raw_value": {"count": len(first_review_times), "avg_hours": avg_first_review}}],
        cat,
    )

    avg_cr = round(_safe_div(sum(change_request_counts), len(change_request_counts)), 2) if change_request_counts else 0
    sorted_cr = sorted(change_request_counts)
    p95_idx = int(len(sorted_cr) * 0.95) if sorted_cr else 0
    p95_cr = sorted_cr[min(p95_idx, len(sorted_cr) - 1)] if sorted_cr else 0
    kpis["Review iterations (changes requested)"] = _metric(
        {"avg": avg_cr, "p95": p95_cr},
        "count(review where state == CHANGES_REQUESTED) per PR; avg/p95",
        [{"source_path": "GitHub PR Reviews API", "raw_value": {"avg": avg_cr, "p95": p95_cr}}],
        cat,
    )

    avg_files = round(_safe_div(sum(files_changed_list), len(files_changed_list)), 2) if files_changed_list else 0
    avg_add = round(_safe_div(sum(additions_list), len(additions_list)), 2) if additions_list else 0
    avg_del = round(_safe_div(sum(deletions_list), len(deletions_list)), 2) if deletions_list else 0
    kpis["PR size (files, additions, deletions)"] = _metric(
        {"avg_files_changed": avg_files, "avg_additions": avg_add, "avg_deletions": avg_del},
        "avg(changed_files), avg(additions), avg(deletions)",
        [{"source_path": "GitHub PR API", "raw_value": {"files": avg_files, "additions": avg_add, "deletions": avg_del}}],
        cat,
    )

    avg_commits = round(_safe_div(sum(commits_per_pr), len(commits_per_pr)), 2) if commits_per_pr else 0
    kpis["Commits per Devin PR"] = _metric(
        avg_commits,
        "avg(count(commits_on_pr))",
        [{"source_path": "GitHub PR Commits API", "raw_value": {"avg": avg_commits, "count": len(commits_per_pr)}}],
        cat,
    )

    devin_pr_count = len(devin_pr_urls)
    kpis["Share of Devin PRs"] = _metric(
        {"devin_prs": devin_pr_count, "note": "Requires total repo PRs for denominator"},
        "count(Devin PRs)/count(all PRs) in window",
        [{"source_path": "Devin sessions + GitHub PR list", "raw_value": devin_pr_count}],
        cat,
        implementable=True,
    )

    return kpis


# =========================================================================
# CATEGORY 3: CALIDAD / CI / TESTING
# =========================================================================

def calculate_quality_kpis(
    github_pr_data: Dict[str, Dict[str, Any]],
) -> Dict[str, Dict[str, Any]]:
    """Calculate Quality / CI / Testing KPIs."""
    cat = "CALIDAD / CI / TESTING"
    kpis: Dict[str, Dict[str, Any]] = {}

    total_prs = 0
    prs_all_pass = 0
    ci_durations: List[float] = []
    prs_with_tests = 0

    for pr_url, pr_data in github_pr_data.items():
        details = pr_data.get("details", {})
        if not details:
            continue
        total_prs += 1

        check_runs = pr_data.get("check_runs", [])
        if check_runs:
            all_success = all(
                cr.get("conclusion") in ("success", "skipped", "neutral")
                for cr in check_runs
                if cr.get("conclusion") is not None
            )
            if all_success:
                prs_all_pass += 1

            started_times = []
            completed_times = []
            for cr in check_runs:
                started = _parse_iso(cr.get("started_at"))
                completed = _parse_iso(cr.get("completed_at"))
                if started:
                    started_times.append(started)
                if completed:
                    completed_times.append(completed)

            if started_times and completed_times:
                earliest = min(started_times)
                latest = max(completed_times)
                duration_min = (latest - earliest).total_seconds() / 60.0
                ci_durations.append(duration_min)

        files = pr_data.get("files", [])
        has_test_files = any(
            TEST_REGEX.search(f.get("filename", ""))
            for f in files
        )
        if has_test_files:
            prs_with_tests += 1

    ci_pass_rate = round(_safe_div(prs_all_pass, total_prs) * 100, 2)
    kpis["CI pass rate for Devin PRs"] = _metric(
        ci_pass_rate,
        "count(PRs where all checks succeed) / count(Devin PRs) * 100",
        [{"source_path": "GitHub Checks API", "raw_value": {"passed": prs_all_pass, "total": total_prs}}],
        cat,
    )

    avg_ci_duration = round(_safe_div(sum(ci_durations), len(ci_durations)), 2) if ci_durations else 0
    kpis["Avg CI duration for Devin PRs"] = _metric(
        avg_ci_duration,
        "avg(max(check.completed_at) - min(check.started_at)) per PR in minutes",
        [{"source_path": "GitHub Checks API", "raw_value": {"avg_minutes": avg_ci_duration, "count": len(ci_durations)}}],
        cat,
    )

    test_pct = round(_safe_div(prs_with_tests, total_prs) * 100, 2)
    kpis["% Devin PRs that modify/add tests"] = _metric(
        test_pct,
        "count(PRs where any file matches test patterns) / count(Devin PRs) * 100",
        [{"source_path": "GitHub PR Files API", "raw_value": {"with_tests": prs_with_tests, "total": total_prs}}],
        cat,
    )

    kpis["Coverage delta for Devin PRs"] = _metric(
        "N/A",
        "Requires coverage provider API (Codecov/SonarQube)",
        [],
        cat,
        implementable=False,
    )

    kpis["Flaky test rate impacting Devin PRs"] = _metric(
        "N/A",
        "Requires flaky-test detection telemetry",
        [],
        cat,
        implementable=False,
    )

    return kpis


# =========================================================================
# CATEGORY 4: SEGURIDAD
# =========================================================================

def calculate_security_kpis(
    sessions_list: List[Dict[str, Any]],
    github_pr_data: Dict[str, Dict[str, Any]],
    code_scanning_alerts: Dict[str, List[Dict[str, Any]]],
    secret_scanning_alerts: List[Dict[str, Any]],
    dependabot_alerts: Dict[str, List[Dict[str, Any]]],
    dependency_reviews: Dict[str, Any],
) -> Dict[str, Dict[str, Any]]:
    """Calculate Security KPIs."""
    cat = "SEGURIDAD"
    kpis: Dict[str, Dict[str, Any]] = {}

    total_open_alerts = 0
    total_fixed_alerts = 0
    for repo_key, alerts in code_scanning_alerts.items():
        if not isinstance(alerts, list):
            continue
        for alert in alerts:
            state = alert.get("state", "")
            if state == "open":
                total_open_alerts += 1
            elif state in ("fixed", "dismissed"):
                total_fixed_alerts += 1

    net_new = total_open_alerts - total_fixed_alerts
    kpis["Net new code scanning alerts"] = _metric(
        {"open": total_open_alerts, "fixed": total_fixed_alerts, "net_new": net_new},
        "alerts_opened - alerts_closed in repos touched by Devin",
        [{"source_path": "GitHub Code Scanning API", "raw_value": {"open": total_open_alerts, "fixed": total_fixed_alerts}}],
        cat,
        implementable=True,
    )

    secret_count = len(secret_scanning_alerts) if isinstance(secret_scanning_alerts, list) else 0
    secret_open = sum(1 for a in (secret_scanning_alerts if isinstance(secret_scanning_alerts, list) else []) if a.get("state") == "open")
    kpis["Secret scanning alerts"] = _metric(
        {"total": secret_count, "open": secret_open},
        "count(secret_alerts where created_at in window)",
        [{"source_path": "GitHub Secret Scanning API", "raw_value": {"total": secret_count, "open": secret_open}}],
        cat,
    )

    dep_opened = 0
    dep_fixed = 0
    for repo_key, alerts in dependabot_alerts.items():
        if not isinstance(alerts, list):
            continue
        for alert in alerts:
            state = alert.get("state", "")
            if state == "open":
                dep_opened += 1
            elif state in ("fixed", "dismissed"):
                dep_fixed += 1

    kpis["Dependabot alerts trend"] = _metric(
        {"opened": dep_opened, "resolved": dep_fixed},
        "count(alerts_opened) and count(alerts_resolved) in repos touched by Devin",
        [{"source_path": "GitHub Dependabot API", "raw_value": {"opened": dep_opened, "resolved": dep_fixed}}],
        cat,
    )

    total_vuln_findings = 0
    for key, review_data in dependency_reviews.items():
        if isinstance(review_data, list):
            for item in review_data:
                vulns = item.get("vulnerabilities", [])
                total_vuln_findings += len(vulns) if isinstance(vulns, list) else 0
        elif isinstance(review_data, dict):
            vulns = review_data.get("vulnerabilities", [])
            total_vuln_findings += len(vulns) if isinstance(vulns, list) else 0

    kpis["Dependency-review vulnerability findings"] = _metric(
        total_vuln_findings,
        "sum(vuln_findings) from dependency diff between base and head",
        [{"source_path": "GitHub Dependency Review API", "raw_value": total_vuln_findings}],
        cat,
    )

    return kpis


# =========================================================================
# CATEGORY 5: JIRA
# =========================================================================

def calculate_jira_kpis(
    github_pr_data: Dict[str, Dict[str, Any]],
    jira_data: Dict[str, Dict[str, Any]],
) -> Dict[str, Dict[str, Any]]:
    """Calculate Jira-related KPIs."""
    cat = "JIRA"
    kpis: Dict[str, Dict[str, Any]] = {}

    total_prs = 0
    prs_with_jira = 0
    all_jira_keys: List[str] = []

    for pr_url, pr_data in github_pr_data.items():
        details = pr_data.get("details", {})
        if not details:
            continue
        total_prs += 1

        keys = extract_jira_keys_from_pr(details)
        if keys:
            prs_with_jira += 1
            all_jira_keys.extend(keys)

    jira_pct = round(_safe_div(prs_with_jira, total_prs) * 100, 2)
    kpis["% Devin PRs with valid Jira issue key"] = _metric(
        jira_pct,
        "count(PRs with jira_key regex) / count(Devin PRs) * 100",
        [{"source_path": "GitHub PR title/body/branch + regex", "raw_value": {"with_jira": prs_with_jira, "total": total_prs}}],
        cat,
        implementable=True,
    )

    cycle_times: List[float] = []
    reopen_count = 0
    total_delivered = 0

    for key, data in jira_data.items():
        changelog = data.get("changelog", [])
        if not changelog:
            continue

        total_delivered += 1

        ct = compute_cycle_time(changelog)
        if ct is not None:
            cycle_times.append(ct)

        if check_reopen(changelog):
            reopen_count += 1

    avg_cycle_time = round(_safe_div(sum(cycle_times), len(cycle_times)), 2) if cycle_times else 0
    kpis["Issue cycle time (In Progress -> Done)"] = _metric(
        avg_cycle_time,
        "avg(done_timestamp - in_progress_timestamp) in hours",
        [{"source_path": "Jira changelog API", "raw_value": {"avg_hours": avg_cycle_time, "count": len(cycle_times)}}],
        cat,
        implementable=True,
    )

    reopen_rate = round(_safe_div(reopen_count, total_delivered) * 100, 2)
    kpis["Reopen rate of Jira issues"] = _metric(
        reopen_rate,
        "count(issues Done->Reopened) / count(issues delivered) * 100",
        [{"source_path": "Jira changelog API", "raw_value": {"reopened": reopen_count, "delivered": total_delivered}}],
        cat,
        implementable=True,
    )

    return kpis


# =========================================================================
# CATEGORY 6: GOBIERNO / AUDITORIA
# =========================================================================

def calculate_governance_kpis(
    audit_logs: List[Dict[str, Any]],
) -> Dict[str, Dict[str, Any]]:
    """Calculate Governance / Audit KPIs."""
    cat = "GOBIERNO / AUDITORIA"
    kpis: Dict[str, Dict[str, Any]] = {}

    event_counts: Dict[str, int] = defaultdict(int)
    for entry in audit_logs:
        event_type = entry.get("event_type", entry.get("type", entry.get("action", "unknown")))
        event_counts[event_type] += 1

    kpis["Devin audit events volume (by type)"] = _metric(
        dict(event_counts) if event_counts else {"note": "No audit log data available"},
        "count_by(event_type) over Devin audit logs in window",
        [{"source_path": "GET /v2/enterprise/audit-logs", "raw_value": dict(event_counts)}],
        cat,
        implementable=True,
    )

    return kpis


# =========================================================================
# MAIN AGGREGATOR
# =========================================================================

def calculate_all_kpis(
    sessions_metrics: Dict[str, Any],
    pr_metrics: Dict[str, Any],
    sessions_list: List[Dict[str, Any]],
    github_pr_data: Dict[str, Dict[str, Any]],
    code_scanning_alerts: Dict[str, List[Dict[str, Any]]],
    secret_scanning_alerts: List[Dict[str, Any]],
    dependabot_alerts: Dict[str, List[Dict[str, Any]]],
    dependency_reviews: Dict[str, Any],
    jira_data: Dict[str, Dict[str, Any]],
    audit_logs: List[Dict[str, Any]],
) -> Dict[str, Dict[str, Any]]:
    """Calculate all KPIs across all categories and return a unified dict."""
    all_kpis: Dict[str, Dict[str, Any]] = {}

    logger.info("Calculating Adoption / Utilization KPIs...")
    adoption = calculate_adoption_kpis(sessions_metrics, pr_metrics, sessions_list, github_pr_data)
    all_kpis.update(adoption)
    logger.info("  + %d Adoption KPIs calculated", len(adoption))

    logger.info("Calculating Productivity / Flow KPIs...")
    productivity = calculate_productivity_kpis(sessions_list, github_pr_data)
    all_kpis.update(productivity)
    logger.info("  + %d Productivity KPIs calculated", len(productivity))

    logger.info("Calculating Quality / CI / Testing KPIs...")
    quality = calculate_quality_kpis(github_pr_data)
    all_kpis.update(quality)
    logger.info("  + %d Quality KPIs calculated", len(quality))

    logger.info("Calculating Security KPIs...")
    security = calculate_security_kpis(
        sessions_list, github_pr_data,
        code_scanning_alerts, secret_scanning_alerts,
        dependabot_alerts, dependency_reviews,
    )
    all_kpis.update(security)
    logger.info("  + %d Security KPIs calculated", len(security))

    logger.info("Calculating Jira KPIs...")
    jira_kpis = calculate_jira_kpis(github_pr_data, jira_data)
    all_kpis.update(jira_kpis)
    logger.info("  + %d Jira KPIs calculated", len(jira_kpis))

    logger.info("Calculating Governance / Audit KPIs...")
    governance = calculate_governance_kpis(audit_logs)
    all_kpis.update(governance)
    logger.info("  + %d Governance KPIs calculated", len(governance))

    logger.info("Total KPIs calculated: %d", len(all_kpis))
    return all_kpis
