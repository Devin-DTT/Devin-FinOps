"""
Error Handling Module for FinOps Pipeline.
Provides a decorator for consistent API error handling with retry logic.
"""

import time
import logging
import functools
from typing import TypeVar, Callable, Any

import requests

logger = logging.getLogger(__name__)

F = TypeVar("F", bound=Callable[..., Any])


class APIError(Exception):
    """Base exception for API-related errors."""

    def __init__(self, message: str, status_code: int | str | None = None, endpoint: str = ""):
        self.status_code = status_code
        self.endpoint = endpoint
        super().__init__(message)


class AuthenticationError(APIError):
    """Raised when API authentication fails (401/403)."""


class RateLimitError(APIError):
    """Raised when API rate limit is exceeded (429)."""


class ServerError(APIError):
    """Raised for server-side errors (5xx)."""


def handle_api_errors(
    max_retries: int = 3,
    base_delay: float = 1.0,
    max_delay: float = 30.0,
    retryable_status_codes: tuple[int, ...] = (429, 500, 502, 503, 504),
) -> Callable[[F], F]:
    """
    Decorator for consistent API error handling with retry logic.

    Args:
        max_retries: Maximum number of retry attempts for retryable errors.
        base_delay: Initial delay in seconds between retries (exponential backoff).
        max_delay: Maximum delay in seconds between retries.
        retryable_status_codes: HTTP status codes that should trigger a retry.

    Returns:
        Decorated function with error handling and retry logic.
    """

    def decorator(func: F) -> F:
        @functools.wraps(func)
        def wrapper(*args: Any, **kwargs: Any) -> Any:
            func_name = func.__name__
            last_exception: Exception | None = None

            for attempt in range(1, max_retries + 1):
                try:
                    return func(*args, **kwargs)

                except requests.exceptions.HTTPError as exc:
                    response = exc.response
                    status_code = response.status_code if response is not None else None

                    if status_code in (401, 403):
                        logger.error(
                            "[%s] Authentication error (HTTP %s) on attempt %d/%d",
                            func_name,
                            status_code,
                            attempt,
                            max_retries,
                        )
                        raise AuthenticationError(
                            f"Authentication failed: HTTP {status_code}",
                            status_code=status_code,
                        ) from exc

                    if status_code is not None and status_code in retryable_status_codes:
                        delay = min(base_delay * (2 ** (attempt - 1)), max_delay)
                        logger.warning(
                            "[%s] Retryable HTTP %s on attempt %d/%d. Retrying in %.1fs...",
                            func_name,
                            status_code,
                            attempt,
                            max_retries,
                            delay,
                        )
                        last_exception = exc
                        if attempt < max_retries:
                            time.sleep(delay)
                            continue
                        raise ServerError(
                            f"Server error after {max_retries} retries: HTTP {status_code}",
                            status_code=status_code,
                        ) from exc

                    logger.error(
                        "[%s] Non-retryable HTTP %s on attempt %d/%d",
                        func_name,
                        status_code,
                        attempt,
                        max_retries,
                    )
                    raise APIError(
                        f"HTTP error: {status_code}",
                        status_code=status_code,
                    ) from exc

                except requests.exceptions.Timeout as exc:
                    delay = min(base_delay * (2 ** (attempt - 1)), max_delay)
                    logger.warning(
                        "[%s] Request timeout on attempt %d/%d. Retrying in %.1fs...",
                        func_name,
                        attempt,
                        max_retries,
                        delay,
                    )
                    last_exception = exc
                    if attempt < max_retries:
                        time.sleep(delay)
                        continue
                    raise APIError(
                        f"Request timed out after {max_retries} retries",
                        status_code="TIMEOUT",
                    ) from exc

                except requests.exceptions.ConnectionError as exc:
                    delay = min(base_delay * (2 ** (attempt - 1)), max_delay)
                    logger.warning(
                        "[%s] Connection error on attempt %d/%d. Retrying in %.1fs...",
                        func_name,
                        attempt,
                        max_retries,
                        delay,
                    )
                    last_exception = exc
                    if attempt < max_retries:
                        time.sleep(delay)
                        continue
                    raise APIError(
                        f"Connection failed after {max_retries} retries: {exc}",
                        status_code="CONNECTION_ERROR",
                    ) from exc

                except (APIError, AuthenticationError, RateLimitError, ServerError):
                    raise

                except Exception as exc:
                    logger.error(
                        "[%s] Unexpected error on attempt %d/%d: %s",
                        func_name,
                        attempt,
                        max_retries,
                        exc,
                        exc_info=True,
                    )
                    raise APIError(
                        f"Unexpected error in {func_name}: {exc}",
                        status_code="ERROR",
                    ) from exc

            raise APIError(
                f"{func_name} failed after {max_retries} retries"
            ) from last_exception

        return wrapper  # type: ignore[return-value]

    return decorator
