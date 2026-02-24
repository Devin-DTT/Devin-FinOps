package com.devin.finops.service;

import com.devin.finops.config.MetricsProperties;
import com.devin.finops.model.ConsumptionData;
import com.devin.finops.model.MetricsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service that replaces the Python metrics_calculator.py / metrics_engine.py.
 * Computes all 20 foundational metrics from Devin session data.
 *
 * Metrics:
 *  1. Total monthly cost          11. Cost per minute
 *  2. Total ACUs                  12. Unique users
 *  3. Cost per user               13. Sessions by task type
 *  4. ACUs per session            14. ACUs by task type
 *  5. Average ACUs per session    15. Cost by task type
 *  6. Total sessions              16. Sessions by department
 *  7. Sessions per user           17. ACUs by department
 *  8. Total duration (minutes)    18. Cost by department
 *  9. Average session duration    19. Average cost per user
 * 10. ACUs per minute             20. Efficiency ratio (ACUs/hour)
 */
@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private final MetricsProperties config;

    public MetricsService(MetricsProperties config) {
        this.config = config;
    }

    // =========================================================================
    // UTILITY FUNCTIONS (from metrics_engine.py)
    // =========================================================================

    /**
     * Calculate total ACUs for a specific month by filtering consumption_by_date.
     * Replaces Python calculate_monthly_acus_from_daily().
     *
     * @param consumptionByDate Map of ISO date strings to ACU values
     * @param monthPrefixStr    Target month prefix in YYYY-MM format (e.g. "2025-11")
     * @return Total ACUs consumed in the specified month
     */
    public double calculateMonthlyAcusFromDaily(Map<String, Double> consumptionByDate,
                                                 String monthPrefixStr) {
        double totalAcus = 0.0;
        for (Map.Entry<String, Double> entry : consumptionByDate.entrySet()) {
            if (entry.getKey().startsWith(monthPrefixStr)) {
                totalAcus += entry.getValue();
            }
        }
        return totalAcus;
    }

    /**
     * Calculate cost from ACUs by applying the cost multiplier.
     * Replaces Python calculate_cost_from_acus().
     *
     * @param acus       ACU value to convert
     * @param costPerAcu Cost per ACU constant
     * @return Calculated cost rounded to 2 decimal places
     */
    public double calculateCostFromAcus(double acus, double costPerAcu) {
        return Math.round(acus * costPerAcu * 100.0) / 100.0;
    }

    // =========================================================================
    // 20 FOUNDATIONAL METRICS (Layer 2 from metrics_engine.py)
    // =========================================================================

    /** Metric 1: Total monthly cost (Coste total mensual) */
    public double calculateTotalMonthlyCost(List<ConsumptionData> sessions) {
        double totalAcus = calculateTotalAcus(sessions);
        return totalAcus * config.getPricePerAcu();
    }

    /** Metric 2: Total ACUs (ACUs totales) */
    public double calculateTotalAcus(List<ConsumptionData> sessions) {
        return sessions.stream()
                .mapToDouble(ConsumptionData::getAcuConsumed)
                .sum();
    }

    /** Metric 3: Cost per user (Coste por usuario) */
    public Map<String, Double> calculateCostPerUser(List<ConsumptionData> sessions) {
        Map<String, Double> userAcus = new HashMap<>();
        for (ConsumptionData session : sessions) {
            String userEmail = session.getUserId() + "@deloitte.com";
            userAcus.merge(userEmail, session.getAcuConsumed(), Double::sum);
        }
        Map<String, Double> costPerUser = new HashMap<>();
        for (Map.Entry<String, Double> entry : userAcus.entrySet()) {
            costPerUser.put(entry.getKey(), entry.getValue() * config.getPricePerAcu());
        }
        return costPerUser;
    }

    /** Metric 4: ACUs per session (ACUs por sesion) */
    public Map<String, Double> calculateAcusPerSession(List<ConsumptionData> sessions) {
        Map<String, Double> acusPerSession = new LinkedHashMap<>();
        for (ConsumptionData session : sessions) {
            acusPerSession.put(session.getSessionId(), session.getAcuConsumed());
        }
        return acusPerSession;
    }

    /** Metric 5: Average ACUs per session (Promedio de ACUs por sesion) */
    public double calculateAverageAcusPerSession(List<ConsumptionData> sessions) {
        if (sessions.isEmpty()) {
            return 0.0;
        }
        return calculateTotalAcus(sessions) / sessions.size();
    }

    /** Metric 6: Total sessions (Total de sesiones) */
    public int calculateTotalSessions(List<ConsumptionData> sessions) {
        return sessions.size();
    }

    /** Metric 7: Sessions per user (Sesiones por usuario) */
    public Map<String, Integer> calculateSessionsPerUser(List<ConsumptionData> sessions) {
        Map<String, Integer> sessionsPerUser = new HashMap<>();
        for (ConsumptionData session : sessions) {
            String userEmail = session.getUserId() + "@deloitte.com";
            sessionsPerUser.merge(userEmail, 1, Integer::sum);
        }
        return sessionsPerUser;
    }

    /** Metric 8: Total duration in minutes (Duracion total) */
    public int calculateTotalDurationMinutes(List<ConsumptionData> sessions) {
        return sessions.stream()
                .mapToInt(s -> Math.max(1, (int) (s.getAcuConsumed() / 5)))
                .sum();
    }

    /** Metric 9: Average session duration in minutes (Duracion promedio por sesion) */
    public double calculateAverageSessionDuration(List<ConsumptionData> sessions) {
        if (sessions.isEmpty()) {
            return 0.0;
        }
        return (double) calculateTotalDurationMinutes(sessions) / sessions.size();
    }

    /** Metric 10: ACUs per minute (ACUs por minuto) */
    public double calculateAcusPerMinute(List<ConsumptionData> sessions) {
        int totalDuration = calculateTotalDurationMinutes(sessions);
        if (totalDuration == 0) {
            return 0.0;
        }
        return calculateTotalAcus(sessions) / totalDuration;
    }

    /** Metric 11: Cost per minute (Coste por minuto) */
    public double calculateCostPerMinute(List<ConsumptionData> sessions) {
        return calculateAcusPerMinute(sessions) * config.getPricePerAcu();
    }

    /** Metric 12: Unique users (Usuarios unicos) */
    public int calculateUniqueUsers(List<ConsumptionData> sessions) {
        Set<String> uniqueUsers = sessions.stream()
                .map(s -> s.getUserId() + "@deloitte.com")
                .collect(Collectors.toSet());
        return uniqueUsers.size();
    }

    /** Metric 13: Sessions by task type (Sesiones por tipo de tarea) */
    public Map<String, Integer> calculateSessionsByTaskType(List<ConsumptionData> sessions) {
        Map<String, Integer> taskSessions = new HashMap<>();
        for (ConsumptionData session : sessions) {
            String taskType = session.getTaskType() != null ? session.getTaskType() : "unknown";
            taskSessions.merge(taskType, 1, Integer::sum);
        }
        return taskSessions;
    }

    /** Metric 14: ACUs by task type (ACUs por tipo de tarea) */
    public Map<String, Double> calculateAcusByTaskType(List<ConsumptionData> sessions) {
        Map<String, Double> taskAcus = new HashMap<>();
        for (ConsumptionData session : sessions) {
            String taskType = session.getTaskType() != null ? session.getTaskType() : "unknown";
            taskAcus.merge(taskType, session.getAcuConsumed(), Double::sum);
        }
        return taskAcus;
    }

    /** Metric 15: Cost by task type (Coste por tipo de tarea) */
    public Map<String, Double> calculateCostByTaskType(List<ConsumptionData> sessions) {
        Map<String, Double> acusByTask = calculateAcusByTaskType(sessions);
        Map<String, Double> costByTask = new HashMap<>();
        for (Map.Entry<String, Double> entry : acusByTask.entrySet()) {
            costByTask.put(entry.getKey(), entry.getValue() * config.getPricePerAcu());
        }
        return costByTask;
    }

    /** Metric 16: Sessions by department (Sesiones por departamento) */
    public Map<String, Integer> calculateSessionsByDepartment(List<ConsumptionData> sessions) {
        Map<String, Integer> deptSessions = new HashMap<>();
        for (ConsumptionData session : sessions) {
            String dept = session.getBusinessUnit() != null ? session.getBusinessUnit() : "Unknown";
            deptSessions.merge(dept, 1, Integer::sum);
        }
        return deptSessions;
    }

    /** Metric 17: ACUs by department (ACUs por departamento) */
    public Map<String, Double> calculateAcusByDepartment(List<ConsumptionData> sessions) {
        Map<String, Double> deptAcus = new HashMap<>();
        for (ConsumptionData session : sessions) {
            String dept = session.getBusinessUnit() != null ? session.getBusinessUnit() : "Unknown";
            deptAcus.merge(dept, session.getAcuConsumed(), Double::sum);
        }
        return deptAcus;
    }

    /** Metric 18: Cost by department (Coste por departamento) */
    public Map<String, Double> calculateCostByDepartment(List<ConsumptionData> sessions) {
        Map<String, Double> acusByDept = calculateAcusByDepartment(sessions);
        Map<String, Double> costByDept = new HashMap<>();
        for (Map.Entry<String, Double> entry : acusByDept.entrySet()) {
            costByDept.put(entry.getKey(), entry.getValue() * config.getPricePerAcu());
        }
        return costByDept;
    }

    /** Metric 19: Average cost per user (Coste promedio por usuario) */
    public double calculateAverageCostPerUser(List<ConsumptionData> sessions) {
        int uniqueUsers = calculateUniqueUsers(sessions);
        if (uniqueUsers == 0) {
            return 0.0;
        }
        return calculateTotalMonthlyCost(sessions) / uniqueUsers;
    }

    /** Metric 20: Efficiency ratio - ACUs per hour (Ratio de eficiencia) */
    public double calculateEfficiencyRatio(List<ConsumptionData> sessions) {
        double totalDurationHours = calculateTotalDurationMinutes(sessions) / 60.0;
        if (totalDurationHours == 0) {
            return 0.0;
        }
        return calculateTotalAcus(sessions) / totalDurationHours;
    }

    // =========================================================================
    // AGGREGATE CALCULATION
    // =========================================================================

    /**
     * Calculate all 20 foundational metrics.
     * Replaces Python MetricsCalculator.calculate_all_metrics().
     *
     * @param sessions       List of consumption data records
     * @param startDate      Reporting period start date
     * @param endDate        Reporting period end date
     * @return MetricsResult containing config, reporting period, and all metrics
     */
    public MetricsResult calculateAllMetrics(List<ConsumptionData> sessions,
                                              String startDate, String endDate) {
        logger.info("Starting calculation of all metrics for {} sessions", sessions.size());

        Map<String, Object> configMap = new LinkedHashMap<>();
        configMap.put("price_per_acu", config.getPricePerAcu());
        configMap.put("currency", config.getCurrency());
        configMap.put("working_hours_per_day", config.getWorkingHoursPerDay());
        configMap.put("working_days_per_month", config.getWorkingDaysPerMonth());

        Map<String, String> reportingPeriod = new LinkedHashMap<>();
        reportingPeriod.put("start_date", startDate != null ? startDate : "N/A");
        reportingPeriod.put("end_date", endDate != null ? endDate : "N/A");
        reportingPeriod.put("month", (startDate != null ? startDate : "N/A") + " to "
                + (endDate != null ? endDate : "N/A"));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("01_total_monthly_cost", calculateTotalMonthlyCost(sessions));
        metrics.put("02_total_acus", calculateTotalAcus(sessions));
        metrics.put("03_cost_per_user", calculateCostPerUser(sessions));
        metrics.put("04_acus_per_session", calculateAcusPerSession(sessions));
        metrics.put("05_average_acus_per_session", calculateAverageAcusPerSession(sessions));
        metrics.put("06_total_sessions", calculateTotalSessions(sessions));
        metrics.put("07_sessions_per_user", calculateSessionsPerUser(sessions));
        metrics.put("08_total_duration_minutes", calculateTotalDurationMinutes(sessions));
        metrics.put("09_average_session_duration", calculateAverageSessionDuration(sessions));
        metrics.put("10_acus_per_minute", calculateAcusPerMinute(sessions));
        metrics.put("11_cost_per_minute", calculateCostPerMinute(sessions));
        metrics.put("12_unique_users", calculateUniqueUsers(sessions));
        metrics.put("13_sessions_by_task_type", calculateSessionsByTaskType(sessions));
        metrics.put("14_acus_by_task_type", calculateAcusByTaskType(sessions));
        metrics.put("15_cost_by_task_type", calculateCostByTaskType(sessions));
        metrics.put("16_sessions_by_department", calculateSessionsByDepartment(sessions));
        metrics.put("17_acus_by_department", calculateAcusByDepartment(sessions));
        metrics.put("18_cost_by_department", calculateCostByDepartment(sessions));
        metrics.put("19_average_cost_per_user", calculateAverageCostPerUser(sessions));
        metrics.put("20_efficiency_ratio", calculateEfficiencyRatio(sessions));

        logger.info("Metrics calculation complete");
        return new MetricsResult(configMap, reportingPeriod, metrics);
    }
}
