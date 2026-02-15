# Future Metrics Roadmap

This document contains FinOps metrics that are currently **not calculable** because they require
external data sources not available through the Cognition Enterprise API. These metrics were
previously defined as N/A placeholders in the production code (`metrics_service.py`) and have
been moved here to keep the codebase clean while preserving the roadmap for future implementation.

> **Note**: When the required data sources become available, these metrics can be implemented
> in `metrics_service.py` following the same traceability pattern used by existing calculable metrics.

---

## COST VISIBILITY AND TRANSPARENCY

_Metrics for understanding and attributing costs across the organization._

| # | Metric | Reason | External Data Required |
|---|--------|--------|------------------------|
| 1 | Total Previous Month Cost | Requires consumption data from the previous reporting period. | consumption_daily from the previous period. |
| 2 | ACUs total mes anterior | Requires consumption data from the previous reporting period. | consumption_daily from the previous period. |
| 3 | Cost by Repository/PR | Requires mapping of ACU consumption to specific repositories or PRs. | Mapping ACUs to Repo/PR ID. |
| 4 | Cost by Task Type | Requires task type classification for each session. | Etiqueta task_type per session. |
| 5 | Cost by Organization | Requires full organizational breakdown from API. | Full breakdown consumption_by_org_id from API. |
| 6 | Cost per Group (IdP) | Requires mapping of users to identity provider groups. | Mapping User ID to Group ID (from /groups). |
| 7 | Coste por unidad de negocio / tribu / area | Requires mapping of organizations to business units. | Mapping Org ID to Business Unit. |
| 8 | Coste por proyecto / producto | Requires project/product tagging in consumption data. | Etiqueta project_id in consumption data. |
| 9 | % coste compartido (shared) | Requires shared resource allocation indicators. | shared_acus indicator or consumption_type field. |
| 10 | % de consumo trazable (cost allocation) | Requires baseline of unaccounted costs. | Baseline of unaccounted Total Cost. |

---

## COST OPTIMIZATION

_Metrics for identifying waste, measuring efficiency, and optimizing resource usage._

| # | Metric | Reason | External Data Required |
|---|--------|--------|------------------------|
| 1 | Coste por plan (Core/Teams) | Requires subscription plan mapping for users. | Mapping User ID to Subscription Plan. |
| 2 | ACUs por linea de codigo | Requires code change metrics (LOC) from sessions. | Lines of Code (LOC) generated/modified. |
| 3 | ACUs por outcome (tarea completada) | Requires outcome status tagging for sessions. | Etiqueta outcome_status per session. |
| 4 | ROI por sesion | Requires monetary value estimation for session outcomes. | Monetary Value of the session\ |
| 5 | % sesiones con outcome | Requires outcome status tracking for sessions. | Outcome status (success/failure) per session. |
| 6 | % sesiones sin outcome | Requires outcome status tracking for sessions. | Outcome status (success/failure) per session. |
| 7 | ACUs por hora productiva | Requires session duration tracking in hours. | Session Duration (in minutes/hours). |
| 8 | % sesiones reintentadas | Requires session retry count tracking. | session_retry_count metric. |
| 9 | Tasa de exito de PR | Requires tracking of PRs opened vs PRs merged. | prs_opened metric. |
| 10 | ACUs desperdiciados (sin ROI) | Requires waste indicator for failed sessions. | Waste indicator (e.g., ACUs from failed sessions). |
| 11 | Sesiones idle (>X min) | Requires session duration and idle time threshold. | Session Duration and idle time threshold. |
| 12 | % tareas redundantes / duplicadas | Requires task fingerprinting or deduplication logic. | Task fingerprint or task description hash. |
| 13 | Ahorro FinOps (%) | Requires manual cost baseline or comparative vendor cost. | Manual Cost Baseline or comparative vendor cost. |
| 14 | Ahorro FinOps acumulado | Requires historical savings data and baseline costs. | Manual Cost Baseline or comparative vendor cost. |
| 15 | ACU Efficiency Index (AEI) | Requires compounding data from other inviable metrics. | Requires compounding data from other inviable metrics. |
| 16 | Cost Velocity Ratio (CVR) | Requires compounding data from other inviable metrics. | Requires compounding data from other inviable metrics. |
| 17 | Waste-to-Outcome Ratio (WOR) | Requires compounding data from other inviable metrics. | Requires compounding data from other inviable metrics. |

---

## FINOPS ENABLEMENT

_Metrics for measuring team readiness, prompt quality, and adoption effectiveness._

| # | Metric | Reason | External Data Required |
|---|--------|--------|------------------------|
| 1 | Lead time con Devin vs humano | Requires human baseline time comparison data. | Session Duration vs Human Baseline Time. |
| 2 | Prompts ineficientes (alto ACU / bajo output) | Requires prompt quality and output metrics. | Requires Prompt quality/output metrics. |
| 3 | Prompts eficientes (%) | Requires prompt quality and output metrics. | Requires Prompt quality/output metrics. |
| 4 | Prompt Efficiency Index (PEI) | Requires prompt quality and output metrics. | Requires Prompt quality/output metrics. |
| 5 | Team Efficiency Spread | Requires user profile and training data. | Requires User profile data (training_status). |
| 6 | % de usuarios formados en eficiencia de prompts | Requires user profile and training data. | Requires User profile data (training_status). |

---

## CLOUD GOVERNANCE

_Metrics for enforcing budgets, policies, and compliance controls._

| # | Metric | Reason | External Data Required |
|---|--------|--------|------------------------|
| 1 | ACUs por tipo de usuario | Requires user role/level classification. | Mapping User ID to User Role/Level. |
| 2 | Dias restantes hasta presupuesto agotado | Requires total budget and consumption forecast. | Total Budget and Consumption Forecast. |
| 3 | % proyectos con limites activos | Requires project-level budget limit configuration. | Total Budget and Consumption Forecast. |
| 4 | % de proyectos con presupuesto definido | Requires project-level budget configuration. | Total Budget and Consumption Forecast. |
| 5 | Over-scope sessions (>N ACUs) | Requires maximum ACU threshold configuration per session. | Maximum ACU Threshold per session. |
| 6 | % ACUs usados fuera de horario laboral | Requires consumption timestamp and working hour definition. | Consumption timestamp + Working Hour Definition. |
| 7 | Coste por entorno (Dev/Test/Prod) | Requires environment tagging for sessions. | Etiqueta environment per session. |
| 8 | % de proyectos con alertas activas | Requires alert system configuration data. | Alert system logs/response time. |
| 9 | Tiempo medio de reaccion a alerta 90% | Requires alert system logs and response time tracking. | Alert system logs/response time. |

---

## FORECAST

_Metrics for predicting future costs, consumption trends, and budget planning._

| # | Metric | Reason | External Data Required |
|---|--------|--------|------------------------|
| 1 | Coste incremental PAYG | Requires complex historical data and forecasting models. | Requires complex historical data and forecasting models. |
| 2 | ACUs por release | Requires release tagging and tracking. | Requires complex historical data and forecasting models. |
| 3 | Peak ACU rate | Requires time-series ACU consumption data. | Requires complex historical data and forecasting models. |
| 4 | Costo por entrega (delivery) | Requires delivery/release tracking. | Requires complex historical data and forecasting models. |
| 5 | Uso presupuestario (%) | Requires total budget configuration. | Requires complex historical data and forecasting models. |
| 6 | Cumplimiento presupuestario | Requires total budget configuration and tracking. | Requires complex historical data and forecasting models. |
| 7 | % de proyectos sobre presupuesto | Requires project-level budget tracking. | Requires complex historical data and forecasting models. |
| 8 | Desviacion forecast vs real | Requires historical forecast data. | Requires complex historical data and forecasting models. |
| 9 | Tendencia semanal de ACUs | Requires time-series weekly ACU data. | Requires complex historical data and forecasting models. |
| 10 | Estacionalidad de consumo | Requires long-term historical consumption patterns. | Requires complex historical data and forecasting models. |
| 11 | Proyeccion de gasto mensual | Requires forecasting model and historical data. | Requires complex historical data and forecasting models. |
| 12 | Elasticidad del coste | Requires cost sensitivity analysis. | Requires complex historical data and forecasting models. |
| 13 | Costo incremental por usuario nuevo | Requires user onboarding cost tracking. | Requires complex historical data and forecasting models. |
| 14 | Coste por cliente (externo) | Requires external client mapping. | Requires complex historical data and forecasting models. |
| 15 | Coste recuperable | Requires cost recovery tracking and billing data. | Requires complex historical data and forecasting models. |

---

## Implementation Priority

When external data sources become available, the recommended implementation order is:

1. **COST VISIBILITY AND TRANSPARENCY** - Foundation for all other metrics
2. **COST OPTIMIZATION** - Directly impacts cost savings decisions
3. **CLOUD GOVERNANCE** - Enables policy enforcement and budget controls
4. **FORECAST** - Requires historical data accumulation
5. **FINOPS ENABLEMENT** - Requires organizational maturity data

## How to Implement

Each metric should follow the existing pattern in `metrics_service.py`:

```python
all_metrics['Metric Name'] = {
    'value': calculated_value,
    'formula': 'Description of the calculation',
    'sources_used': [
        {'source_path': 'data.source.path', 'raw_value': raw_value}
    ],
    'category': 'CATEGORY NAME'
}
```

Ensure full traceability by including `formula`, `sources_used`, and `raw_data_value` fields
for audit compliance.
