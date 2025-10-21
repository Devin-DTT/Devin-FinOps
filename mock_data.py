import random
from datetime import datetime, timedelta
from models import UsageLog, SessionOutcome, TaskType


def generate_mock_logs(count: int = 1000) -> list[UsageLog]:
    logs = []
    
    business_units = ["Finance", "Engineering", "Operations", "Marketing", "Sales", "HR"]
    organizations = [f"org_{i:03d}" for i in range(1, 11)]
    projects = [f"proj_{i:03d}" for i in range(1, 51)]
    users = [f"user_{i:04d}" for i in range(1, 101)]
    
    base_time = datetime(2025, 1, 1, 0, 0, 0)
    
    for i in range(count):
        session_id = f"sess_{i:06d}"
        user_id = random.choice(users)
        organization_id = random.choice(organizations)
        project_id = random.choice(projects)
        
        has_pr = random.random() > 0.3
        pull_request_id = f"pr_{random.randint(1, 9999)}" if has_pr else None
        
        timestamp = base_time + timedelta(
            days=random.randint(0, 290),
            hours=random.randint(0, 23),
            minutes=random.randint(0, 59),
            seconds=random.randint(0, 59)
        )
        
        acu_consumed = round(random.uniform(10.0, 500.0), 2)
        
        business_unit = random.choice(business_units)
        task_type = random.choice(list(TaskType))
        
        hour = timestamp.hour
        is_out_of_hours = hour < 8 or hour >= 18 or timestamp.weekday() >= 5
        
        outcome = random.choice(list(SessionOutcome))
        is_merged = outcome == SessionOutcome.SUCCESS and has_pr and random.random() > 0.2
        
        log = UsageLog(
            session_id=session_id,
            user_id=user_id,
            organization_id=organization_id,
            project_id=project_id,
            pull_request_id=pull_request_id,
            timestamp=timestamp,
            acu_consumed=acu_consumed,
            business_unit=business_unit,
            task_type=task_type,
            is_out_of_hours=is_out_of_hours,
            is_merged=is_merged,
            session_outcome=outcome
        )
        
        logs.append(log)
    
    logs.sort(key=lambda x: x.timestamp, reverse=True)
    
    return logs


MOCK_LOGS = generate_mock_logs(1000)
