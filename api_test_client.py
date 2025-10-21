import requests
import json
from typing import Optional


class FinOpsAPIClient:
    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url
    
    def test_usage_logs(self, page: int = 1, page_size: int = 50) -> dict:
        url = f"{self.base_url}/api/v1/usage_logs"
        params = {"page": page, "page_size": page_size}
        
        try:
            response = requests.get(url, params=params)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"Error fetching usage logs: {e}")
            return None
    
    def test_cost_settings(self) -> dict:
        url = f"{self.base_url}/api/v1/cost_settings"
        
        try:
            response = requests.get(url)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"Error fetching cost settings: {e}")
            return None
    
    def validate_usage_log_schema(self, log: dict) -> bool:
        required_fields = [
            "session_id", "user_id", "organization_id", "project_id",
            "timestamp", "acu_consumed", "business_unit", "task_type",
            "is_out_of_hours", "is_merged", "session_outcome"
        ]
        
        for field in required_fields:
            if field not in log:
                print(f"Missing required field: {field}")
                return False
        
        if not isinstance(log["acu_consumed"], (int, float)):
            print(f"Invalid type for acu_consumed: {type(log['acu_consumed'])}")
            return False
        
        if not isinstance(log["is_out_of_hours"], bool):
            print(f"Invalid type for is_out_of_hours: {type(log['is_out_of_hours'])}")
            return False
        
        if not isinstance(log["is_merged"], bool):
            print(f"Invalid type for is_merged: {type(log['is_merged'])}")
            return False
        
        return True
    
    def run_tests(self):
        print("=" * 70)
        print("FinOps Mock Usage Data API - Test Client")
        print("=" * 70)
        print()
        
        print("Test 1: Fetching usage logs (page 1, page_size=10)")
        print("-" * 70)
        response = self.test_usage_logs(page=1, page_size=10)
        if response:
            print(f"✓ Response received")
            print(f"  Total records: {response['total']}")
            print(f"  Page: {response['page']}")
            print(f"  Page size: {response['page_size']}")
            print(f"  Total pages: {response['total_pages']}")
            print(f"  Records in this page: {len(response['data'])}")
            print()
            
            if response['data']:
                print("Test 2: Validating data schema for first record")
                print("-" * 70)
                first_log = response['data'][0]
                if self.validate_usage_log_schema(first_log):
                    print("✓ Schema validation passed")
                    print()
                    print("Sample record:")
                    print(json.dumps(first_log, indent=2))
                else:
                    print("✗ Schema validation failed")
                print()
        else:
            print("✗ Failed to fetch usage logs")
            print()
        
        print("Test 3: Testing pagination (page 2)")
        print("-" * 70)
        response_page2 = self.test_usage_logs(page=2, page_size=10)
        if response_page2:
            print(f"✓ Page 2 fetched successfully")
            print(f"  Records in page 2: {len(response_page2['data'])}")
            print()
        else:
            print("✗ Failed to fetch page 2")
            print()
        
        print("Test 4: Testing large page size")
        print("-" * 70)
        response_large = self.test_usage_logs(page=1, page_size=100)
        if response_large:
            print(f"✓ Large page fetched successfully")
            print(f"  Records fetched: {len(response_large['data'])}")
            print()
        else:
            print("✗ Failed to fetch large page")
            print()
        
        print("Test 5: Fetching cost settings")
        print("-" * 70)
        cost_settings = self.test_cost_settings()
        if cost_settings:
            print("✓ Cost settings retrieved successfully")
            print(json.dumps(cost_settings, indent=2))
            print()
        else:
            print("✗ Failed to fetch cost settings")
            print()
        
        print("Test 6: Verifying data variability")
        print("-" * 70)
        if response:
            business_units = set()
            task_types = set()
            outcomes = set()
            
            for log in response['data']:
                business_units.add(log['business_unit'])
                task_types.add(log['task_type'])
                outcomes.add(log['session_outcome'])
            
            print(f"✓ Data variability confirmed")
            print(f"  Unique business units: {len(business_units)} - {business_units}")
            print(f"  Unique task types: {len(task_types)} - {task_types}")
            print(f"  Unique outcomes: {len(outcomes)} - {outcomes}")
            print()
        
        print("=" * 70)
        print("All tests completed!")
        print("=" * 70)


if __name__ == "__main__":
    client = FinOpsAPIClient()
    client.run_tests()
