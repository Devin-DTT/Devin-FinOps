"""
HTML Dashboard Generator for FinOps Metrics
Generates an interactive HTML dashboard with charts for consumption visualization.
"""

import json
import logging
from typing import Dict, Any, List
from error_handling import handle_pipeline_phase, ExportError, DataValidationError

logger = logging.getLogger(__name__)


@handle_pipeline_phase(phase_name="EXPORT_HTML", error_cls=ExportError)
def generate_html_dashboard(
    summary_data: Dict[str, Any],
    daily_chart_data: List[Dict[str, Any]],
    user_chart_data: List[Dict[str, Any]]
) -> None:
    """
    Generate an interactive HTML dashboard with consumption charts.
    
    Args:
        summary_data: Dictionary containing business summary metrics
        daily_chart_data: List of dicts with 'Date' and 'ACUs' keys
        user_chart_data: List of dicts with 'User ID' and 'ACUs' keys
    
    Raises:
        DataValidationError: If summary_data is missing or invalid.
        ExportError: If HTML generation fails.
    """
    logger.info("[EXPORT_HTML] Generating HTML dashboard...")

    if summary_data is None:
        raise DataValidationError(
            "summary_data is required for HTML dashboard generation",
        )
    if not isinstance(summary_data, dict):
        raise DataValidationError(
            "summary_data must be a dictionary",
            details={"received_type": type(summary_data).__name__},
        )

    required_keys = ['total_acus', 'total_cost', 'currency', 'total_prs_merged',
                     'total_sessions_count', 'cost_per_pr', 'unique_users']
    missing_keys = [k for k in required_keys if k not in summary_data]
    if missing_keys:
        logger.warning(
            "[EXPORT_HTML] summary_data missing keys: %s",
            ", ".join(missing_keys),
        )
    
    daily_dates = [item['Date'] for item in daily_chart_data] if daily_chart_data else []
    daily_acus = [round(item['ACUs'], 2) for item in daily_chart_data] if daily_chart_data else []
    
    user_ids = [item['User ID'] for item in user_chart_data] if user_chart_data else []
    user_acus = [round(item['ACUs'], 2) for item in user_chart_data] if user_chart_data else []
    
    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FinOps Dashboard</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <style>
        * {{
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }}
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 20px;
            min-height: 100vh;
        }}
        .container {{
            max-width: 1400px;
            margin: 0 auto;
        }}
        h1 {{
            color: white;
            text-align: center;
            margin-bottom: 30px;
            font-size: 2.5em;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }}
        .metrics-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }}
        .metric-card {{
            background: white;
            border-radius: 10px;
            padding: 25px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            transition: transform 0.3s ease;
        }}
        .metric-card:hover {{
            transform: translateY(-5px);
            box-shadow: 0 6px 12px rgba(0,0,0,0.15);
        }}
        .metric-label {{
            color: #666;
            font-size: 0.9em;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-bottom: 10px;
        }}
        .metric-value {{
            color: #333;
            font-size: 2em;
            font-weight: bold;
        }}
        .metric-unit {{
            color: #999;
            font-size: 0.8em;
            margin-left: 5px;
        }}
        .charts-grid {{
            display: grid;
            grid-template-columns: 2fr 1fr;
            gap: 20px;
            margin-bottom: 30px;
        }}
        .chart-container {{
            background: white;
            border-radius: 10px;
            padding: 25px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }}
        .chart-title {{
            color: #333;
            font-size: 1.3em;
            margin-bottom: 20px;
            text-align: center;
            font-weight: 600;
        }}
        canvas {{
            max-height: 400px;
        }}
        @media (max-width: 768px) {{
            .charts-grid {{
                grid-template-columns: 1fr;
            }}
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>📊 FinOps Dashboard</h1>
        
        <div class="metrics-grid">
            <div class="metric-card">
                <div class="metric-label">Total ACUs Consumed</div>
                <div class="metric-value">{summary_data['total_acus']:.2f}<span class="metric-unit">ACUs</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Total Cost</div>
                <div class="metric-value">{summary_data['total_cost']:.2f}<span class="metric-unit">{summary_data['currency']}</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Total PRs Merged</div>
                <div class="metric-value">{summary_data['total_prs_merged']}<span class="metric-unit">PRs</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Total Sessions</div>
                <div class="metric-value">{summary_data['total_sessions_count']}<span class="metric-unit">Sessions</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Cost Per PR Merged</div>
                <div class="metric-value">{summary_data['cost_per_pr']:.2f}<span class="metric-unit">{summary_data['currency'] if summary_data['cost_per_pr'] > 0 else 'N/A'}</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Unique Users</div>
                <div class="metric-value">{summary_data['unique_users']}<span class="metric-unit">Users</span></div>
            </div>
        </div>
        
        <div class="charts-grid">
            <div class="chart-container">
                <div class="chart-title">Daily ACU Consumption Trend</div>
                <canvas id="dailyChart"></canvas>
            </div>
            <div class="chart-container">
                <div class="chart-title">Consumption by User</div>
                <canvas id="userChart"></canvas>
            </div>
        </div>
    </div>
    
    <script>
        const dailyCtx = document.getElementById('dailyChart').getContext('2d');
        const dailyChart = new Chart(dailyCtx, {{
            type: 'bar',
            data: {{
                labels: {json.dumps(daily_dates)},
                datasets: [{{
                    label: 'ACUs Consumed',
                    data: {json.dumps(daily_acus)},
                    backgroundColor: 'rgba(102, 126, 234, 0.8)',
                    borderColor: 'rgba(102, 126, 234, 1)',
                    borderWidth: 1
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: true,
                plugins: {{
                    legend: {{
                        display: true,
                        position: 'top'
                    }}
                }},
                scales: {{
                    y: {{
                        beginAtZero: true,
                        title: {{
                            display: true,
                            text: 'ACUs'
                        }}
                    }},
                    x: {{
                        title: {{
                            display: true,
                            text: 'Date'
                        }}
                    }}
                }}
            }}
        }});
        
        const userCtx = document.getElementById('userChart').getContext('2d');
        const userChart = new Chart(userCtx, {{
            type: 'doughnut',
            data: {{
                labels: {json.dumps(user_ids)},
                datasets: [{{
                    label: 'ACUs by User',
                    data: {json.dumps(user_acus)},
                    backgroundColor: [
                        'rgba(255, 99, 132, 0.8)',
                        'rgba(54, 162, 235, 0.8)',
                        'rgba(255, 206, 86, 0.8)',
                        'rgba(75, 192, 192, 0.8)',
                        'rgba(153, 102, 255, 0.8)',
                        'rgba(255, 159, 64, 0.8)',
                        'rgba(102, 126, 234, 0.8)',
                        'rgba(234, 102, 150, 0.8)',
                        'rgba(102, 234, 180, 0.8)',
                        'rgba(234, 180, 102, 0.8)'
                    ],
                    borderWidth: 2
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: true,
                plugins: {{
                    legend: {{
                        display: true,
                        position: 'bottom',
                        labels: {{
                            boxWidth: 15,
                            padding: 10
                        }}
                    }}
                }}
            }}
        }});
    </script>
</body>
</html>"""
    
    output_file = 'finops_dashboard.html'
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(html_content)
    logger.info("[EXPORT_HTML] Successfully generated %s", output_file)
    print(f"\n+ HTML dashboard generated: {output_file}")
