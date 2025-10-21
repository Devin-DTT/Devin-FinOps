from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from enum import Enum


class SessionOutcome(str, Enum):
    SUCCESS = "Success"
    FAILURE = "Failure"
    IDLE = "Idle"


class TaskType(str, Enum):
    BUGFIX = "BugFix"
    REFACTOR = "Refactor"
    FEATURE = "Feature"
    TESTING = "Testing"
    DOCUMENTATION = "Documentation"


class UsageLog(BaseModel):
    session_id: str = Field(..., description="Unique session identifier")
    user_id: str = Field(..., description="User identifier")
    organization_id: str = Field(..., description="Organization identifier")
    project_id: str = Field(..., description="Project identifier")
    pull_request_id: Optional[str] = Field(None, description="Pull request identifier if applicable")
    timestamp: datetime = Field(..., description="Timestamp of the log entry")
    acu_consumed: float = Field(..., description="Agent Compute Units consumed", ge=0)
    business_unit: str = Field(..., description="Business unit for tribal cost allocation")
    task_type: TaskType = Field(..., description="Type of task being performed")
    is_out_of_hours: bool = Field(..., description="Whether the work was done outside business hours")
    is_merged: bool = Field(..., description="Whether the PR was merged successfully")
    session_outcome: SessionOutcome = Field(..., description="Outcome of the session")

    class Config:
        json_schema_extra = {
            "example": {
                "session_id": "sess_abc123",
                "user_id": "user_001",
                "organization_id": "org_deloitte",
                "project_id": "proj_finops",
                "pull_request_id": "pr_456",
                "timestamp": "2025-10-21T10:00:00Z",
                "acu_consumed": 125.5,
                "business_unit": "Finance",
                "task_type": "BugFix",
                "is_out_of_hours": False,
                "is_merged": True,
                "session_outcome": "Success"
            }
        }


class UsageLogsResponse(BaseModel):
    data: list[UsageLog]
    total: int
    page: int
    page_size: int
    total_pages: int


class CostSettings(BaseModel):
    acu_base_cost: float = Field(..., description="Base cost per ACU")
    out_of_hours_multiplier: float = Field(..., description="Cost multiplier for out-of-hours work")
    business_unit_rates: dict[str, float] = Field(..., description="Cost rates by business unit")
    
    class Config:
        json_schema_extra = {
            "example": {
                "acu_base_cost": 0.10,
                "out_of_hours_multiplier": 1.5,
                "business_unit_rates": {
                    "Finance": 1.2,
                    "Engineering": 1.0,
                    "Operations": 0.9
                }
            }
        }
