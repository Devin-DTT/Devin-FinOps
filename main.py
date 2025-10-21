from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse
from models import UsageLogsResponse, CostSettings
from mock_data import MOCK_LOGS
import math

app = FastAPI(
    title="FinOps Mock Usage Data API",
    description="Mock API for serving usage data logs for the FinOps Automation Project",
    version="1.0.0"
)


@app.get("/")
def root():
    return {
        "message": "FinOps Mock Usage Data API",
        "version": "1.0.0",
        "endpoints": {
            "usage_logs": "/api/v1/usage_logs",
            "cost_settings": "/api/v1/cost_settings"
        }
    }


@app.get("/api/v1/usage_logs", response_model=UsageLogsResponse)
def get_usage_logs(
    page: int = Query(1, ge=1, description="Page number"),
    page_size: int = Query(50, ge=1, le=500, description="Number of items per page")
):
    total = len(MOCK_LOGS)
    total_pages = math.ceil(total / page_size)
    
    if page > total_pages and total > 0:
        return JSONResponse(
            status_code=404,
            content={
                "detail": f"Page {page} not found. Total pages: {total_pages}"
            }
        )
    
    start_idx = (page - 1) * page_size
    end_idx = start_idx + page_size
    
    paginated_logs = MOCK_LOGS[start_idx:end_idx]
    
    return UsageLogsResponse(
        data=paginated_logs,
        total=total,
        page=page,
        page_size=page_size,
        total_pages=total_pages
    )


@app.get("/api/v1/cost_settings", response_model=CostSettings)
def get_cost_settings():
    return CostSettings(
        acu_base_cost=0.10,
        out_of_hours_multiplier=1.5,
        business_unit_rates={
            "Finance": 1.2,
            "Engineering": 1.0,
            "Operations": 0.9,
            "Marketing": 1.1,
            "Sales": 1.15,
            "HR": 0.95
        }
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
