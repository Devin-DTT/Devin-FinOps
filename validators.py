"""
Pydantic Validation Models for FinOps Pipeline.
Provides input data validation for API parameters, sessions, and user data.
"""

from typing import Optional, List
from pydantic import BaseModel, Field, field_validator


class APIRequestParams(BaseModel):
    """Validation model for API request parameters."""

    base_url: str = Field(
        default="https://api.devin.ai/v2/enterprise",
        description="Base URL for the API",
    )
    endpoint: str = Field(
        default="/consumption/daily",
        description="API endpoint path",
    )
    api_key: Optional[str] = Field(
        default=None,
        description="API key for authentication",
    )
    page_size: int = Field(
        default=100,
        ge=1,
        le=1000,
        description="Number of records per page",
    )
    start_date: Optional[str] = Field(
        default=None,
        pattern=r"^\d{4}-\d{2}-\d{2}$",
        description="Start date in YYYY-MM-DD format",
    )
    end_date: Optional[str] = Field(
        default=None,
        pattern=r"^\d{4}-\d{2}-\d{2}$",
        description="End date in YYYY-MM-DD format",
    )


class SessionData(BaseModel):
    """Validation model for a single session record."""

    session_id: str = Field(description="Unique session identifier")
    user_email: str = Field(description="User email address")
    duration_minutes: int = Field(ge=0, description="Session duration in minutes")
    acus_consumed: int = Field(ge=0, description="ACUs consumed in the session")
    task_type: str = Field(default="unknown", description="Type of task performed")
    status: str = Field(default="unknown", description="Session status")


class UserDetail(BaseModel):
    """Validation model for user detail records."""

    user_email: str = Field(description="User email address")
    department: str = Field(default="Unknown", description="User department")
    role: str = Field(default="Unknown", description="User role")


class ReportingPeriod(BaseModel):
    """Validation model for reporting period."""

    start_date: Optional[str] = Field(
        default=None,
        pattern=r"^\d{4}-\d{2}-\d{2}$",
        description="Start date in YYYY-MM-DD format",
    )
    end_date: Optional[str] = Field(
        default=None,
        pattern=r"^\d{4}-\d{2}-\d{2}$",
        description="End date in YYYY-MM-DD format",
    )
    month: Optional[str] = Field(default=None, description="Reporting month label")


class UsageData(BaseModel):
    """Validation model for the complete usage data payload."""

    organization: Optional[str] = Field(default=None, description="Organization name")
    reporting_period: ReportingPeriod = Field(default_factory=ReportingPeriod)
    sessions: List[SessionData] = Field(default_factory=list)
    user_details: List[UserDetail] = Field(default_factory=list)


class MetricsConfigInput(BaseModel):
    """Validation model for metrics configuration parameters."""

    price_per_acu: float = Field(
        default=0.05,
        gt=0,
        description="Cost per ACU",
    )
    currency: str = Field(
        default="USD",
        min_length=3,
        max_length=3,
        description="ISO 4217 currency code",
    )
    working_hours_per_day: int = Field(
        default=8,
        ge=1,
        le=24,
        description="Working hours per day",
    )
    working_days_per_month: int = Field(
        default=22,
        ge=1,
        le=31,
        description="Working days per month",
    )


class EndpointListInput(BaseModel):
    """Validation model for multi-endpoint fetch parameters."""

    endpoint_list: dict[str, str] = Field(
        description="Dictionary of endpoint_name -> endpoint_path",
    )
    params_by_endpoint: Optional[dict[str, dict[str, str]]] = Field(
        default=None,
        description="Optional parameters per endpoint",
    )

    @field_validator("endpoint_list")
    @classmethod
    def validate_endpoint_list_not_empty(cls, v: dict[str, str]) -> dict[str, str]:
        if not v:
            raise ValueError("endpoint_list must contain at least one endpoint")
        return v
