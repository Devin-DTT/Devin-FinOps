"""
Jira REST API Adapter for KPI Metrics.

Provides functions to fetch Jira issues and changelogs for traceability
and cycle-time calculations. Uses environment variables for configuration:
  JIRA_BASE_URL  - e.g. https://yourorg.atlassian.net
  JIRA_EMAIL     - Jira user email
  JIRA_API_TOKEN - Jira API token
"""

import os
import re
import logging
from typing import Dict, List, Any, Optional, Tuple
from datetime import datetime

import requests

from error_handling import handle_api_errors

logger = logging.getLogger(__name__)

JIRA_KEY_PATTERN = re.compile(r"[A-Z][A-Z0-9]+-\d+")


def _resolve_jira_config() -> Tuple[str, str, str]:
    base_url = os.getenv("JIRA_BASE_URL", "")
    email = os.getenv("JIRA_EMAIL", "")
    api_token = os.getenv("JIRA_API_TOKEN", "")
    if not base_url or not email or not api_token:
        logger.warning(
            "Jira configuration incomplete. Set JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN"
        )
    return base_url, email, api_token


def _jira_headers() -> Dict[str, str]:
    return {
        "Accept": "application/json",
        "Content-Type": "application/json",
    }


@handle_api_errors(max_retries=3, base_delay=1.0)
def _jira_get(url: str, auth: Tuple[str, str], headers: Dict[str, str]) -> requests.Response:
    response = requests.get(url, auth=auth, headers=headers, timeout=30)
    response.raise_for_status()
    return response


def extract_jira_keys(text: str) -> List[str]:
    """Extract all Jira issue keys (e.g. PROJ-123) from a text string."""
    if not text:
        return []
    return JIRA_KEY_PATTERN.findall(text)


def extract_jira_keys_from_pr(pr_details: Dict[str, Any]) -> List[str]:
    """Extract Jira keys from PR title, body, and branch name."""
    keys = set()
    title = pr_details.get("title", "")
    body = pr_details.get("body", "") or ""
    branch = pr_details.get("head", {}).get("ref", "")

    for text in [title, body, branch]:
        keys.update(extract_jira_keys(text))

    return list(keys)


def fetch_jira_issue(issue_key: str) -> Optional[Dict[str, Any]]:
    """GET /rest/api/3/issue/{issueIdOrKey}"""
    base_url, email, api_token = _resolve_jira_config()
    if not base_url:
        return None

    url = f"{base_url.rstrip('/')}/rest/api/3/issue/{issue_key}"
    auth = (email, api_token)
    headers = _jira_headers()

    try:
        resp = _jira_get(url, auth, headers)
        return resp.json()
    except Exception as e:
        logger.warning("Failed to fetch Jira issue %s: %s", issue_key, e)
        return None


def fetch_jira_issue_changelog(issue_key: str) -> List[Dict[str, Any]]:
    """GET /rest/api/3/issue/{issueIdOrKey}/changelog"""
    base_url, email, api_token = _resolve_jira_config()
    if not base_url:
        return []

    url = f"{base_url.rstrip('/')}/rest/api/3/issue/{issue_key}/changelog"
    auth = (email, api_token)
    headers = _jira_headers()

    all_changelogs: List[Dict[str, Any]] = []
    start_at = 0
    max_results = 100

    while True:
        try:
            resp = _jira_get(
                f"{url}?startAt={start_at}&maxResults={max_results}",
                auth,
                headers,
            )
            data = resp.json()
            values = data.get("values", [])
            all_changelogs.extend(values)

            if start_at + len(values) >= data.get("total", 0):
                break
            start_at += max_results
        except Exception as e:
            logger.warning("Failed to fetch changelog for %s: %s", issue_key, e)
            break

    return all_changelogs


def compute_cycle_time(
    changelog: List[Dict[str, Any]],
    start_status: str = "In Progress",
    end_status: str = "Done",
) -> Optional[float]:
    """Compute cycle time in hours from status transitions in changelog.

    Finds the first transition TO start_status and the first transition TO end_status,
    returning the difference in hours.
    """
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None

    for entry in changelog:
        created = entry.get("created", "")
        items = entry.get("items", [])

        for item in items:
            if item.get("field") != "status":
                continue

            to_string = item.get("toString", "")

            if to_string == start_status and start_time is None:
                try:
                    start_time = datetime.fromisoformat(created.replace("Z", "+00:00"))
                except (ValueError, TypeError):
                    pass

            if to_string == end_status and end_time is None:
                try:
                    end_time = datetime.fromisoformat(created.replace("Z", "+00:00"))
                except (ValueError, TypeError):
                    pass

    if start_time and end_time and end_time > start_time:
        delta = end_time - start_time
        return delta.total_seconds() / 3600.0

    return None


def check_reopen(
    changelog: List[Dict[str, Any]],
    done_status: str = "Done",
    reopen_status: str = "Reopened",
    days_threshold: int = 30,
) -> bool:
    """Check if an issue was reopened after being marked Done within threshold days."""
    done_time: Optional[datetime] = None

    for entry in changelog:
        created = entry.get("created", "")
        items = entry.get("items", [])

        for item in items:
            if item.get("field") != "status":
                continue

            to_string = item.get("toString", "")

            if to_string == done_status:
                try:
                    done_time = datetime.fromisoformat(created.replace("Z", "+00:00"))
                except (ValueError, TypeError):
                    pass

            if to_string == reopen_status and done_time is not None:
                try:
                    reopen_time = datetime.fromisoformat(created.replace("Z", "+00:00"))
                    delta_days = (reopen_time - done_time).total_seconds() / 86400.0
                    if delta_days <= days_threshold:
                        return True
                except (ValueError, TypeError):
                    pass

    return False


def fetch_jira_data_for_keys(jira_keys: List[str]) -> Dict[str, Dict[str, Any]]:
    """Fetch issue details and changelog for a list of Jira keys.

    Returns dict keyed by issue key with 'issue' and 'changelog' sub-dicts.
    """
    base_url, email, api_token = _resolve_jira_config()
    if not base_url:
        logger.info("Jira not configured, skipping Jira data fetch")
        return {}

    results: Dict[str, Dict[str, Any]] = {}
    for key in jira_keys:
        logger.info("Fetching Jira data for %s", key)
        issue = fetch_jira_issue(key)
        changelog = fetch_jira_issue_changelog(key)
        results[key] = {
            "issue": issue,
            "changelog": changelog,
        }

    logger.info("Fetched Jira data for %d issues", len(results))
    return results
