"""
GitHub REST API Adapter for KPI Metrics.

Provides functions to fetch PR details, reviews, commits, check runs,
files, and security alerts from GitHub repositories.
All functions use the GITHUB_TOKEN environment variable for authentication.
"""

import os
import re
import logging
import time
from typing import Dict, List, Any, Optional, Tuple

import requests

from error_handling import handle_api_errors, APIError, AuthenticationError

logger = logging.getLogger(__name__)

_forbidden_repos: set = set()

GITHUB_API_BASE = "https://api.github.com"


def _resolve_github_token() -> str:
    token = os.getenv("GITHUB_TOKEN", "")
    if not token:
        logger.warning("GITHUB_TOKEN environment variable not set")
    return token


def _github_headers(token: str) -> Dict[str, str]:
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def parse_pr_url(pr_url: str) -> Optional[Tuple[str, str, int]]:
    """Extract owner, repo, pull_number from a GitHub PR URL.

    Supports formats:
        https://github.com/owner/repo/pull/123
        https://api.github.com/repos/owner/repo/pulls/123
    """
    patterns = [
        r"github\.com/([^/]+)/([^/]+)/pull/(\d+)",
        r"api\.github\.com/repos/([^/]+)/([^/]+)/pulls/(\d+)",
    ]
    for pattern in patterns:
        match = re.search(pattern, pr_url)
        if match:
            return match.group(1), match.group(2), int(match.group(3))
    return None


@handle_api_errors(max_retries=3, base_delay=1.0)
def _github_get(url: str, headers: Dict[str, str], params: Optional[Dict[str, str]] = None) -> requests.Response:
    response = requests.get(url, headers=headers, params=params, timeout=30)
    if response.status_code == 403 and "rate limit" in response.text.lower():
        reset_time = int(response.headers.get("X-RateLimit-Reset", 0))
        wait = max(reset_time - int(time.time()), 1)
        logger.warning("GitHub rate limit hit, waiting %ds", wait)
        time.sleep(min(wait, 60))
        response = requests.get(url, headers=headers, params=params, timeout=30)
    response.raise_for_status()
    return response


def fetch_pr_details(owner: str, repo: str, pull_number: int) -> Dict[str, Any]:
    """GET /repos/{owner}/{repo}/pulls/{pull_number}"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/pulls/{pull_number}"
    resp = _github_get(url, headers)
    return resp.json()


def fetch_pr_reviews(owner: str, repo: str, pull_number: int) -> List[Dict[str, Any]]:
    """GET /repos/{owner}/{repo}/pulls/{pull_number}/reviews"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/pulls/{pull_number}/reviews"
    all_reviews: List[Dict[str, Any]] = []
    page = 1
    while True:
        resp = _github_get(url, headers, {"per_page": "100", "page": str(page)})
        data = resp.json()
        if not data:
            break
        all_reviews.extend(data)
        if len(data) < 100:
            break
        page += 1
    return all_reviews


def fetch_pr_commits(owner: str, repo: str, pull_number: int) -> List[Dict[str, Any]]:
    """GET /repos/{owner}/{repo}/pulls/{pull_number}/commits"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/pulls/{pull_number}/commits"
    all_commits: List[Dict[str, Any]] = []
    page = 1
    while True:
        resp = _github_get(url, headers, {"per_page": "100", "page": str(page)})
        data = resp.json()
        if not data:
            break
        all_commits.extend(data)
        if len(data) < 100:
            break
        page += 1
    return all_commits


def fetch_pr_files(owner: str, repo: str, pull_number: int) -> List[Dict[str, Any]]:
    """GET /repos/{owner}/{repo}/pulls/{pull_number}/files"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/pulls/{pull_number}/files"
    all_files: List[Dict[str, Any]] = []
    page = 1
    while True:
        resp = _github_get(url, headers, {"per_page": "100", "page": str(page)})
        data = resp.json()
        if not data:
            break
        all_files.extend(data)
        if len(data) < 100:
            break
        page += 1
    return all_files


def fetch_check_runs(owner: str, repo: str, ref: str) -> List[Dict[str, Any]]:
    """GET /repos/{owner}/{repo}/commits/{ref}/check-runs"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/commits/{ref}/check-runs"
    all_runs: List[Dict[str, Any]] = []
    page = 1
    while True:
        resp = _github_get(url, headers, {"per_page": "100", "page": str(page)})
        data = resp.json()
        runs = data.get("check_runs", [])
        all_runs.extend(runs)
        if len(runs) < 100:
            break
        page += 1
    return all_runs


def fetch_repo_pulls(
    owner: str,
    repo: str,
    state: str = "all",
    since: Optional[str] = None,
    per_page: int = 100,
    max_pages: int = 10,
) -> List[Dict[str, Any]]:
    """GET /repos/{owner}/{repo}/pulls?state=all"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/pulls"
    all_prs: List[Dict[str, Any]] = []
    page = 1
    while page <= max_pages:
        params: Dict[str, str] = {
            "state": state,
            "per_page": str(per_page),
            "page": str(page),
            "sort": "created",
            "direction": "desc",
        }
        if since:
            params["since"] = since
        resp = _github_get(url, headers, params)
        data = resp.json()
        if not data:
            break
        all_prs.extend(data)
        if len(data) < per_page:
            break
        page += 1
    return all_prs


def fetch_code_scanning_alerts(owner: str, repo: str, state: str = "open") -> List[Dict[str, Any]]:
    """GET /repos/{owner}/{repo}/code-scanning/alerts"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/code-scanning/alerts"
    try:
        resp = _github_get(url, headers, {"state": state, "per_page": "100"})
        return resp.json()
    except Exception as e:
        logger.warning("Code scanning alerts not available for %s/%s: %s", owner, repo, e)
        return []


def fetch_secret_scanning_alerts_org(org: str) -> List[Dict[str, Any]]:
    """GET /orgs/{org}/secret-scanning/alerts"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/orgs/{org}/secret-scanning/alerts"
    try:
        resp = _github_get(url, headers, {"per_page": "100"})
        return resp.json()
    except Exception as e:
        logger.warning("Secret scanning alerts not available for org %s: %s", org, e)
        return []


def fetch_dependabot_alerts(owner: str, repo: str) -> List[Dict[str, Any]]:
    """GET /repos/{owner}/{repo}/dependabot/alerts"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/dependabot/alerts"
    try:
        resp = _github_get(url, headers, {"per_page": "100"})
        return resp.json()
    except Exception as e:
        logger.warning("Dependabot alerts not available for %s/%s: %s", owner, repo, e)
        return []


def fetch_dependency_review(owner: str, repo: str, base: str, head: str) -> Dict[str, Any]:
    """GET /repos/{owner}/{repo}/dependency-graph/compare/{base}...{head}"""
    token = _resolve_github_token()
    headers = _github_headers(token)
    url = f"{GITHUB_API_BASE}/repos/{owner}/{repo}/dependency-graph/compare/{base}...{head}"
    try:
        resp = _github_get(url, headers)
        return resp.json()
    except Exception as e:
        logger.warning("Dependency review not available for %s/%s: %s", owner, repo, e)
        return {}


def fetch_all_pr_data(pr_urls: List[str]) -> Dict[str, Dict[str, Any]]:
    """Fetch PR details, reviews, commits, files, and check runs for a list of PR URLs.

    Returns a dict keyed by PR URL containing sub-dicts for each data type.
    """
    token = _resolve_github_token()
    if not token:
        logger.warning("No GITHUB_TOKEN available, skipping GitHub PR data fetch")
        return {}

    results: Dict[str, Dict[str, Any]] = {}
    for pr_url in pr_urls:
        parsed = parse_pr_url(pr_url)
        if not parsed:
            logger.warning("Could not parse PR URL: %s", pr_url)
            continue

        owner, repo, pull_number = parsed
        repo_key = f"{owner}/{repo}"
        pr_key = f"{repo_key}#{pull_number}"

        if repo_key in _forbidden_repos:
            logger.info("Skipping %s (repo previously returned 403)", pr_key)
            continue

        logger.info("Fetching GitHub data for %s", pr_key)

        pr_data: Dict[str, Any] = {"url": pr_url, "owner": owner, "repo": repo, "pull_number": pull_number}

        try:
            pr_data["details"] = fetch_pr_details(owner, repo, pull_number)
        except AuthenticationError:
            logger.warning("No access to %s, skipping remaining calls", repo_key)
            _forbidden_repos.add(repo_key)
            pr_data["details"] = {}
            results[pr_url] = pr_data
            continue
        except Exception as e:
            logger.warning("Failed to fetch PR details for %s: %s", pr_key, e)
            pr_data["details"] = {}
            results[pr_url] = pr_data
            continue

        try:
            pr_data["reviews"] = fetch_pr_reviews(owner, repo, pull_number)
        except Exception as e:
            logger.warning("Failed to fetch PR reviews for %s: %s", pr_key, e)
            pr_data["reviews"] = []

        try:
            pr_data["commits"] = fetch_pr_commits(owner, repo, pull_number)
        except Exception as e:
            logger.warning("Failed to fetch PR commits for %s: %s", pr_key, e)
            pr_data["commits"] = []

        try:
            pr_data["files"] = fetch_pr_files(owner, repo, pull_number)
        except Exception as e:
            logger.warning("Failed to fetch PR files for %s: %s", pr_key, e)
            pr_data["files"] = []

        head_sha = pr_data.get("details", {}).get("head", {}).get("sha", "")
        if head_sha:
            try:
                pr_data["check_runs"] = fetch_check_runs(owner, repo, head_sha)
            except Exception as e:
                logger.warning("Failed to fetch check runs for %s: %s", pr_key, e)
                pr_data["check_runs"] = []
        else:
            pr_data["check_runs"] = []

        results[pr_url] = pr_data

    logger.info("Fetched GitHub data for %d PRs", len(results))
    return results
