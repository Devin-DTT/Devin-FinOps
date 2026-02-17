"""
KPI Dashboard Generator.

Generates an interactive HTML dashboard displaying all KPIs organized by category
with summary cards, tables, and charts.
"""

import json
import logging
from typing import Dict, Any

logger = logging.getLogger(__name__)

CATEGORY_ORDER = [
    "ADOPCION / UTILIZACION",
    "PRODUCTIVIDAD / FLUJO",
    "CALIDAD / CI / TESTING",
    "SEGURIDAD",
    "JIRA",
    "GOBIERNO / AUDITORIA",
]

CATEGORY_ICONS = {
    "ADOPCION / UTILIZACION": "&#128202;",
    "PRODUCTIVIDAD / FLUJO": "&#9889;",
    "CALIDAD / CI / TESTING": "&#128736;",
    "SEGURIDAD": "&#128274;",
    "JIRA": "&#128203;",
    "GOBIERNO / AUDITORIA": "&#128220;",
}

CATEGORY_COLORS = {
    "ADOPCION / UTILIZACION": "#4F46E5",
    "PRODUCTIVIDAD / FLUJO": "#059669",
    "CALIDAD / CI / TESTING": "#D97706",
    "SEGURIDAD": "#DC2626",
    "JIRA": "#7C3AED",
    "GOBIERNO / AUDITORIA": "#0891B2",
}


def _format_value(value: Any) -> str:
    if value is None or value == "N/A":
        return "N/A"
    if isinstance(value, dict):
        parts = []
        for k, v in value.items():
            if k == "note":
                continue
            parts.append(f"<strong>{k}</strong>: {_format_value(v)}")
        return "<br>".join(parts)
    if isinstance(value, float):
        return f"{value:,.2f}"
    if isinstance(value, int):
        return f"{value:,}"
    return str(value)


def _format_value_short(value: Any) -> str:
    if value is None or value == "N/A":
        return "N/A"
    if isinstance(value, dict):
        first_val = next(iter(value.values()), "N/A")
        if isinstance(first_val, (int, float)):
            return f"{first_val:,.2f}" if isinstance(first_val, float) else f"{first_val:,}"
        return str(first_val)
    if isinstance(value, float):
        return f"{value:,.2f}"
    if isinstance(value, int):
        return f"{value:,}"
    return str(value)


def _build_category_section(category: str, kpis: Dict[str, Dict[str, Any]]) -> str:
    color = CATEGORY_COLORS.get(category, "#374151")
    icon = CATEGORY_ICONS.get(category, "&#128200;")

    cards_html = ""
    for name, data in kpis.items():
        value = data.get("value", "N/A")
        formula = data.get("formula", "")
        implementable = data.get("implementable", True)
        badge = ""
        if not implementable:
            badge = '<span class="badge badge-na">Not Implementable</span>'

        cards_html += f"""
        <div class="kpi-card">
            <div class="kpi-header">
                <span class="kpi-name">{name}</span>
                {badge}
            </div>
            <div class="kpi-value">{_format_value(value)}</div>
            <div class="kpi-formula">{formula}</div>
        </div>
        """

    return f"""
    <div class="category-section">
        <div class="category-title" style="border-left: 5px solid {color};">
            <span class="category-icon">{icon}</span> {category}
            <span class="kpi-count">{len(kpis)} KPIs</span>
        </div>
        <div class="kpi-grid">
            {cards_html}
        </div>
    </div>
    """


def _build_summary_table(all_kpis: Dict[str, Dict[str, Any]]) -> str:
    rows = ""
    for name, data in all_kpis.items():
        cat = data.get("category", "")
        value = _format_value_short(data.get("value", "N/A"))
        impl = "Yes" if data.get("implementable", True) else "No"
        impl_class = "impl-yes" if impl == "Yes" else "impl-no"
        color = CATEGORY_COLORS.get(cat, "#374151")

        rows += f"""
        <tr>
            <td><span class="cat-dot" style="background:{color};"></span>{cat}</td>
            <td class="kpi-name-col">{name}</td>
            <td class="value-col">{value}</td>
            <td class="{impl_class}">{impl}</td>
        </tr>
        """

    return f"""
    <div class="summary-table-container">
        <h2>KPI Summary Table</h2>
        <table class="summary-table">
            <thead>
                <tr>
                    <th>Category</th>
                    <th>KPI</th>
                    <th>Value</th>
                    <th>Implementable</th>
                </tr>
            </thead>
            <tbody>
                {rows}
            </tbody>
        </table>
    </div>
    """


def generate_kpi_dashboard(
    all_kpis: Dict[str, Dict[str, Any]],
    start_date: str,
    end_date: str,
    output_file: str = "kpi_dashboard.html",
) -> None:
    """Generate interactive HTML dashboard for all KPIs."""
    logger.info("Generating KPI dashboard...")

    categorized: Dict[str, Dict[str, Dict[str, Any]]] = {}
    for name, data in all_kpis.items():
        cat = data.get("category", "OTHER")
        if cat not in categorized:
            categorized[cat] = {}
        categorized[cat][name] = data

    category_sections = ""
    for cat in CATEGORY_ORDER:
        if cat in categorized:
            category_sections += _build_category_section(cat, categorized[cat])

    for cat, kpis in categorized.items():
        if cat not in CATEGORY_ORDER:
            category_sections += _build_category_section(cat, kpis)

    summary_table = _build_summary_table(all_kpis)

    total_kpis = len(all_kpis)
    implementable_count = sum(1 for d in all_kpis.values() if d.get("implementable", True))
    categories_count = len(categorized)

    adoption_chart_data = {}
    for name, data in categorized.get("ADOPCION / UTILIZACION", {}).items():
        val = data.get("value")
        if isinstance(val, (int, float)):
            adoption_chart_data[name] = val

    productivity_chart_data = {}
    for name, data in categorized.get("PRODUCTIVIDAD / FLUJO", {}).items():
        val = data.get("value")
        if isinstance(val, (int, float)):
            productivity_chart_data[name] = val

    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Devin KPI Dashboard</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{
            font-family: 'Inter', 'Segoe UI', system-ui, -apple-system, sans-serif;
            background: #f1f5f9;
            color: #1e293b;
            min-height: 100vh;
        }}
        .header {{
            background: linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #4338ca 100%);
            color: white;
            padding: 30px 40px;
            position: relative;
            overflow: hidden;
        }}
        .header h1 {{
            font-size: 2.2em;
            font-weight: 700;
            margin-bottom: 8px;
        }}
        .header .subtitle {{
            opacity: 0.85;
            font-size: 1.05em;
        }}
        .stats-bar {{
            display: flex;
            gap: 30px;
            margin-top: 20px;
        }}
        .stat-item {{
            text-align: center;
        }}
        .stat-value {{
            font-size: 2em;
            font-weight: 700;
        }}
        .stat-label {{
            font-size: 0.85em;
            opacity: 0.8;
            text-transform: uppercase;
            letter-spacing: 1px;
        }}
        .container {{
            max-width: 1400px;
            margin: 0 auto;
            padding: 30px 20px;
        }}
        .tabs {{
            display: flex;
            gap: 10px;
            margin-bottom: 25px;
            flex-wrap: wrap;
        }}
        .tab {{
            padding: 10px 20px;
            background: white;
            border: 2px solid #e2e8f0;
            border-radius: 8px;
            cursor: pointer;
            font-weight: 600;
            font-size: 0.9em;
            transition: all 0.2s;
        }}
        .tab:hover {{ border-color: #4338ca; color: #4338ca; }}
        .tab.active {{ background: #4338ca; color: white; border-color: #4338ca; }}
        .tab-content {{ display: none; }}
        .tab-content.active {{ display: block; }}
        .category-section {{
            margin-bottom: 35px;
        }}
        .category-title {{
            font-size: 1.3em;
            font-weight: 700;
            padding: 12px 20px;
            background: white;
            border-radius: 8px;
            margin-bottom: 15px;
            display: flex;
            align-items: center;
            gap: 10px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.08);
        }}
        .category-icon {{ font-size: 1.3em; }}
        .kpi-count {{
            margin-left: auto;
            font-size: 0.75em;
            background: #e2e8f0;
            padding: 4px 12px;
            border-radius: 20px;
            font-weight: 600;
        }}
        .kpi-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
            gap: 15px;
        }}
        .kpi-card {{
            background: white;
            border-radius: 10px;
            padding: 20px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.08);
            transition: transform 0.2s, box-shadow 0.2s;
            border-left: 4px solid #4338ca;
        }}
        .kpi-card:hover {{
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0,0,0,0.12);
        }}
        .kpi-header {{
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 10px;
        }}
        .kpi-name {{
            font-weight: 600;
            font-size: 0.95em;
            color: #334155;
        }}
        .badge {{
            font-size: 0.7em;
            padding: 3px 8px;
            border-radius: 10px;
            font-weight: 600;
        }}
        .badge-na {{
            background: #fee2e2;
            color: #dc2626;
        }}
        .kpi-value {{
            font-size: 1.5em;
            font-weight: 700;
            color: #1e293b;
            margin-bottom: 8px;
            word-break: break-word;
        }}
        .kpi-formula {{
            font-size: 0.8em;
            color: #64748b;
            font-style: italic;
        }}
        .charts-section {{
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
            margin-bottom: 30px;
        }}
        .chart-card {{
            background: white;
            border-radius: 10px;
            padding: 25px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.08);
        }}
        .chart-card h3 {{
            margin-bottom: 15px;
            color: #334155;
        }}
        .summary-table-container {{
            background: white;
            border-radius: 10px;
            padding: 25px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.08);
            overflow-x: auto;
        }}
        .summary-table-container h2 {{
            margin-bottom: 15px;
            color: #1e293b;
        }}
        .summary-table {{
            width: 100%;
            border-collapse: collapse;
        }}
        .summary-table th {{
            background: #f8fafc;
            padding: 12px 15px;
            text-align: left;
            font-size: 0.85em;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: #64748b;
            border-bottom: 2px solid #e2e8f0;
        }}
        .summary-table td {{
            padding: 10px 15px;
            border-bottom: 1px solid #f1f5f9;
            font-size: 0.9em;
        }}
        .summary-table tr:hover {{ background: #f8fafc; }}
        .cat-dot {{
            display: inline-block;
            width: 10px;
            height: 10px;
            border-radius: 50%;
            margin-right: 8px;
        }}
        .kpi-name-col {{ font-weight: 500; }}
        .value-col {{ font-weight: 600; text-align: right; }}
        .impl-yes {{ color: #059669; font-weight: 600; }}
        .impl-no {{ color: #dc2626; font-weight: 600; }}
        @media (max-width: 768px) {{
            .kpi-grid {{ grid-template-columns: 1fr; }}
            .charts-section {{ grid-template-columns: 1fr; }}
            .stats-bar {{ flex-wrap: wrap; gap: 15px; }}
        }}
    </style>
</head>
<body>
    <div class="header">
        <h1>Devin KPI Dashboard</h1>
        <div class="subtitle">Period: {start_date} to {end_date}</div>
        <div class="stats-bar">
            <div class="stat-item">
                <div class="stat-value">{total_kpis}</div>
                <div class="stat-label">Total KPIs</div>
            </div>
            <div class="stat-item">
                <div class="stat-value">{implementable_count}</div>
                <div class="stat-label">Implementable</div>
            </div>
            <div class="stat-item">
                <div class="stat-value">{categories_count}</div>
                <div class="stat-label">Categories</div>
            </div>
        </div>
    </div>

    <div class="container">
        <div class="tabs">
            <div class="tab active" onclick="showTab('categories')">By Category</div>
            <div class="tab" onclick="showTab('charts')">Charts</div>
            <div class="tab" onclick="showTab('table')">Summary Table</div>
        </div>

        <div id="tab-categories" class="tab-content active">
            {category_sections}
        </div>

        <div id="tab-charts" class="tab-content">
            <div class="charts-section">
                <div class="chart-card">
                    <h3>Adoption Metrics</h3>
                    <canvas id="adoptionChart"></canvas>
                </div>
                <div class="chart-card">
                    <h3>Productivity Metrics</h3>
                    <canvas id="productivityChart"></canvas>
                </div>
            </div>
            <div class="charts-section">
                <div class="chart-card">
                    <h3>KPIs per Category</h3>
                    <canvas id="categoryChart"></canvas>
                </div>
                <div class="chart-card">
                    <h3>Implementable vs Not Implementable</h3>
                    <canvas id="implChart"></canvas>
                </div>
            </div>
        </div>

        <div id="tab-table" class="tab-content">
            {summary_table}
        </div>
    </div>

    <script>
        function showTab(tabName) {{
            document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
            document.querySelectorAll('.tab').forEach(el => el.classList.remove('active'));
            document.getElementById('tab-' + tabName).classList.add('active');
            event.target.classList.add('active');
        }}

        const adoptionLabels = {json.dumps(list(adoption_chart_data.keys()))};
        const adoptionValues = {json.dumps(list(adoption_chart_data.values()))};
        if (adoptionLabels.length > 0) {{
            new Chart(document.getElementById('adoptionChart'), {{
                type: 'bar',
                data: {{
                    labels: adoptionLabels,
                    datasets: [{{ label: 'Value', data: adoptionValues, backgroundColor: '#4F46E5' }}]
                }},
                options: {{ responsive: true, plugins: {{ legend: {{ display: false }} }}, scales: {{ y: {{ beginAtZero: true }} }} }}
            }});
        }}

        const prodLabels = {json.dumps(list(productivity_chart_data.keys()))};
        const prodValues = {json.dumps(list(productivity_chart_data.values()))};
        if (prodLabels.length > 0) {{
            new Chart(document.getElementById('productivityChart'), {{
                type: 'bar',
                data: {{
                    labels: prodLabels,
                    datasets: [{{ label: 'Value', data: prodValues, backgroundColor: '#059669' }}]
                }},
                options: {{ responsive: true, plugins: {{ legend: {{ display: false }} }}, scales: {{ y: {{ beginAtZero: true }} }} }}
            }});
        }}

        const catLabels = {json.dumps([c for c in CATEGORY_ORDER if c in categorized])};
        const catValues = {json.dumps([len(categorized.get(c, {})) for c in CATEGORY_ORDER if c in categorized])};
        const catColors = {json.dumps([CATEGORY_COLORS.get(c, '#374151') for c in CATEGORY_ORDER if c in categorized])};
        new Chart(document.getElementById('categoryChart'), {{
            type: 'doughnut',
            data: {{
                labels: catLabels,
                datasets: [{{ data: catValues, backgroundColor: catColors }}]
            }},
            options: {{ responsive: true, plugins: {{ legend: {{ position: 'bottom' }} }} }}
        }});

        new Chart(document.getElementById('implChart'), {{
            type: 'pie',
            data: {{
                labels: ['Implementable', 'Not Implementable'],
                datasets: [{{ data: [{implementable_count}, {total_kpis - implementable_count}], backgroundColor: ['#059669', '#DC2626'] }}]
            }},
            options: {{ responsive: true, plugins: {{ legend: {{ position: 'bottom' }} }} }}
        }});
    </script>
</body>
</html>"""

    try:
        with open(output_file, "w", encoding="utf-8") as f:
            f.write(html_content)
        logger.info("KPI dashboard generated: %s", output_file)
        print(f"\n+ KPI dashboard generated: {output_file}")
    except Exception as e:
        logger.error("Failed to generate KPI dashboard: %s", e)
        print(f"\n- Error generating KPI dashboard: {e}")
