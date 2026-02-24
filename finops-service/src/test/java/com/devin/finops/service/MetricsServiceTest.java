package com.devin.finops.service;

import com.devin.finops.config.MetricsProperties;
import com.devin.finops.model.ConsumptionData;
import com.devin.finops.model.MetricsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsService covering all 20 foundational metrics.
 */
class MetricsServiceTest {

    private MetricsService metricsService;
    private List<ConsumptionData> testSessions;

    @BeforeEach
    void setUp() {
        MetricsProperties config = new MetricsProperties();
        config.setPricePerAcu(0.05);
        config.setCurrency("USD");
        config.setWorkingHoursPerDay(8);
        config.setWorkingDaysPerMonth(22);

        metricsService = new MetricsService(config);

        testSessions = new ArrayList<>();

        // Session 1: user_0001, Engineering, Feature, 100 ACUs
        testSessions.add(new ConsumptionData(
                "sess_001", "user_0001", "org_001", "proj_001", "pr_100",
                "2025-10-15T10:00:00", 100.0, "Engineering", "Feature",
                false, true, "Success"
        ));

        // Session 2: user_0001, Engineering, BugFix, 50 ACUs
        testSessions.add(new ConsumptionData(
                "sess_002", "user_0001", "org_001", "proj_001", null,
                "2025-10-15T14:00:00", 50.0, "Engineering", "BugFix",
                false, false, "Success"
        ));

        // Session 3: user_0002, Marketing, Feature, 200 ACUs
        testSessions.add(new ConsumptionData(
                "sess_003", "user_0002", "org_002", "proj_002", "pr_200",
                "2025-10-16T09:00:00", 200.0, "Marketing", "Feature",
                true, true, "Success"
        ));

        // Session 4: user_0003, Sales, Testing, 75 ACUs
        testSessions.add(new ConsumptionData(
                "sess_004", "user_0003", "org_001", "proj_003", null,
                "2025-10-16T11:00:00", 75.0, "Sales", "Testing",
                false, false, "Failure"
        ));
    }

    @Test
    void testCalculateTotalMonthlyCost() {
        // Total ACUs = 100 + 50 + 200 + 75 = 425
        // Cost = 425 * 0.05 = 21.25
        double cost = metricsService.calculateTotalMonthlyCost(testSessions);
        assertEquals(21.25, cost, 0.01);
    }

    @Test
    void testCalculateTotalAcus() {
        double totalAcus = metricsService.calculateTotalAcus(testSessions);
        assertEquals(425.0, totalAcus, 0.01);
    }

    @Test
    void testCalculateCostPerUser() {
        Map<String, Double> costPerUser = metricsService.calculateCostPerUser(testSessions);
        assertEquals(3, costPerUser.size());
        // user_0001: (100 + 50) * 0.05 = 7.50
        assertEquals(7.50, costPerUser.get("user_0001@deloitte.com"), 0.01);
        // user_0002: 200 * 0.05 = 10.00
        assertEquals(10.00, costPerUser.get("user_0002@deloitte.com"), 0.01);
        // user_0003: 75 * 0.05 = 3.75
        assertEquals(3.75, costPerUser.get("user_0003@deloitte.com"), 0.01);
    }

    @Test
    void testCalculateAcusPerSession() {
        Map<String, Double> acusPerSession = metricsService.calculateAcusPerSession(testSessions);
        assertEquals(4, acusPerSession.size());
        assertEquals(100.0, acusPerSession.get("sess_001"), 0.01);
        assertEquals(50.0, acusPerSession.get("sess_002"), 0.01);
        assertEquals(200.0, acusPerSession.get("sess_003"), 0.01);
        assertEquals(75.0, acusPerSession.get("sess_004"), 0.01);
    }

    @Test
    void testCalculateAverageAcusPerSession() {
        // 425 / 4 = 106.25
        double avg = metricsService.calculateAverageAcusPerSession(testSessions);
        assertEquals(106.25, avg, 0.01);
    }

    @Test
    void testCalculateAverageAcusPerSessionEmpty() {
        assertEquals(0.0, metricsService.calculateAverageAcusPerSession(List.of()));
    }

    @Test
    void testCalculateTotalSessions() {
        assertEquals(4, metricsService.calculateTotalSessions(testSessions));
    }

    @Test
    void testCalculateSessionsPerUser() {
        Map<String, Integer> sessionsPerUser = metricsService.calculateSessionsPerUser(testSessions);
        assertEquals(3, sessionsPerUser.size());
        assertEquals(2, sessionsPerUser.get("user_0001@deloitte.com"));
        assertEquals(1, sessionsPerUser.get("user_0002@deloitte.com"));
        assertEquals(1, sessionsPerUser.get("user_0003@deloitte.com"));
    }

    @Test
    void testCalculateTotalDurationMinutes() {
        // Duration = max(1, acu/5): 20 + 10 + 40 + 15 = 85
        int duration = metricsService.calculateTotalDurationMinutes(testSessions);
        assertEquals(85, duration);
    }

    @Test
    void testCalculateAverageSessionDuration() {
        // 85 / 4 = 21.25
        double avg = metricsService.calculateAverageSessionDuration(testSessions);
        assertEquals(21.25, avg, 0.01);
    }

    @Test
    void testCalculateAverageSessionDurationEmpty() {
        assertEquals(0.0, metricsService.calculateAverageSessionDuration(List.of()));
    }

    @Test
    void testCalculateAcusPerMinute() {
        // 425 / 85 = 5.0
        double acusPerMinute = metricsService.calculateAcusPerMinute(testSessions);
        assertEquals(5.0, acusPerMinute, 0.01);
    }

    @Test
    void testCalculateAcusPerMinuteEmpty() {
        assertEquals(0.0, metricsService.calculateAcusPerMinute(List.of()));
    }

    @Test
    void testCalculateCostPerMinute() {
        // 5.0 * 0.05 = 0.25
        double costPerMinute = metricsService.calculateCostPerMinute(testSessions);
        assertEquals(0.25, costPerMinute, 0.01);
    }

    @Test
    void testCalculateUniqueUsers() {
        assertEquals(3, metricsService.calculateUniqueUsers(testSessions));
    }

    @Test
    void testCalculateSessionsByTaskType() {
        Map<String, Integer> sessionsByType = metricsService.calculateSessionsByTaskType(testSessions);
        assertEquals(3, sessionsByType.size());
        assertEquals(2, sessionsByType.get("Feature"));
        assertEquals(1, sessionsByType.get("BugFix"));
        assertEquals(1, sessionsByType.get("Testing"));
    }

    @Test
    void testCalculateAcusByTaskType() {
        Map<String, Double> acusByType = metricsService.calculateAcusByTaskType(testSessions);
        assertEquals(3, acusByType.size());
        assertEquals(300.0, acusByType.get("Feature"), 0.01);    // 100 + 200
        assertEquals(50.0, acusByType.get("BugFix"), 0.01);
        assertEquals(75.0, acusByType.get("Testing"), 0.01);
    }

    @Test
    void testCalculateCostByTaskType() {
        Map<String, Double> costByType = metricsService.calculateCostByTaskType(testSessions);
        assertEquals(3, costByType.size());
        assertEquals(15.0, costByType.get("Feature"), 0.01);     // 300 * 0.05
        assertEquals(2.50, costByType.get("BugFix"), 0.01);      // 50 * 0.05
        assertEquals(3.75, costByType.get("Testing"), 0.01);     // 75 * 0.05
    }

    @Test
    void testCalculateSessionsByDepartment() {
        Map<String, Integer> sessionsByDept = metricsService.calculateSessionsByDepartment(testSessions);
        assertEquals(3, sessionsByDept.size());
        assertEquals(2, sessionsByDept.get("Engineering"));
        assertEquals(1, sessionsByDept.get("Marketing"));
        assertEquals(1, sessionsByDept.get("Sales"));
    }

    @Test
    void testCalculateAcusByDepartment() {
        Map<String, Double> acusByDept = metricsService.calculateAcusByDepartment(testSessions);
        assertEquals(3, acusByDept.size());
        assertEquals(150.0, acusByDept.get("Engineering"), 0.01);  // 100 + 50
        assertEquals(200.0, acusByDept.get("Marketing"), 0.01);
        assertEquals(75.0, acusByDept.get("Sales"), 0.01);
    }

    @Test
    void testCalculateCostByDepartment() {
        Map<String, Double> costByDept = metricsService.calculateCostByDepartment(testSessions);
        assertEquals(3, costByDept.size());
        assertEquals(7.50, costByDept.get("Engineering"), 0.01);   // 150 * 0.05
        assertEquals(10.00, costByDept.get("Marketing"), 0.01);   // 200 * 0.05
        assertEquals(3.75, costByDept.get("Sales"), 0.01);        // 75 * 0.05
    }

    @Test
    void testCalculateAverageCostPerUser() {
        // Total cost = 21.25, unique users = 3
        // 21.25 / 3 = 7.0833...
        double avgCost = metricsService.calculateAverageCostPerUser(testSessions);
        assertEquals(7.08, avgCost, 0.01);
    }

    @Test
    void testCalculateAverageCostPerUserEmpty() {
        assertEquals(0.0, metricsService.calculateAverageCostPerUser(List.of()));
    }

    @Test
    void testCalculateEfficiencyRatio() {
        // Total duration in hours = 85 / 60 = 1.4167
        // Total ACUs = 425
        // Efficiency = 425 / 1.4167 = 300.0
        double ratio = metricsService.calculateEfficiencyRatio(testSessions);
        assertEquals(300.0, ratio, 0.1);
    }

    @Test
    void testCalculateEfficiencyRatioEmpty() {
        assertEquals(0.0, metricsService.calculateEfficiencyRatio(List.of()));
    }

    @Test
    void testCalculateAllMetrics() {
        MetricsResult result = metricsService.calculateAllMetrics(
                testSessions, "2025-10-01", "2025-10-31");

        assertNotNull(result);
        assertNotNull(result.getConfig());
        assertNotNull(result.getReportingPeriod());
        assertNotNull(result.getMetrics());

        assertEquals(0.05, result.getConfig().get("price_per_acu"));
        assertEquals("USD", result.getConfig().get("currency"));
        assertEquals("2025-10-01", result.getReportingPeriod().get("start_date"));
        assertEquals("2025-10-31", result.getReportingPeriod().get("end_date"));

        // Verify all 20 metrics are present
        Map<String, Object> metrics = result.getMetrics();
        assertEquals(20, metrics.size());
        assertTrue(metrics.containsKey("01_total_monthly_cost"));
        assertTrue(metrics.containsKey("02_total_acus"));
        assertTrue(metrics.containsKey("03_cost_per_user"));
        assertTrue(metrics.containsKey("04_acus_per_session"));
        assertTrue(metrics.containsKey("05_average_acus_per_session"));
        assertTrue(metrics.containsKey("06_total_sessions"));
        assertTrue(metrics.containsKey("07_sessions_per_user"));
        assertTrue(metrics.containsKey("08_total_duration_minutes"));
        assertTrue(metrics.containsKey("09_average_session_duration"));
        assertTrue(metrics.containsKey("10_acus_per_minute"));
        assertTrue(metrics.containsKey("11_cost_per_minute"));
        assertTrue(metrics.containsKey("12_unique_users"));
        assertTrue(metrics.containsKey("13_sessions_by_task_type"));
        assertTrue(metrics.containsKey("14_acus_by_task_type"));
        assertTrue(metrics.containsKey("15_cost_by_task_type"));
        assertTrue(metrics.containsKey("16_sessions_by_department"));
        assertTrue(metrics.containsKey("17_acus_by_department"));
        assertTrue(metrics.containsKey("18_cost_by_department"));
        assertTrue(metrics.containsKey("19_average_cost_per_user"));
        assertTrue(metrics.containsKey("20_efficiency_ratio"));
    }

    @Test
    void testCalculateMonthlyAcusFromDaily() {
        Map<String, Double> consumptionByDate = new HashMap<>();
        consumptionByDate.put("2025-10-01", 100.0);
        consumptionByDate.put("2025-10-15", 200.0);
        consumptionByDate.put("2025-10-31", 50.0);
        consumptionByDate.put("2025-11-01", 75.0);
        consumptionByDate.put("2025-11-15", 125.0);

        double octoberAcus = metricsService.calculateMonthlyAcusFromDaily(consumptionByDate, "2025-10");
        assertEquals(350.0, octoberAcus, 0.01);

        double novemberAcus = metricsService.calculateMonthlyAcusFromDaily(consumptionByDate, "2025-11");
        assertEquals(200.0, novemberAcus, 0.01);

        double decemberAcus = metricsService.calculateMonthlyAcusFromDaily(consumptionByDate, "2025-12");
        assertEquals(0.0, decemberAcus, 0.01);
    }

    @Test
    void testCalculateCostFromAcus() {
        assertEquals(5.0, metricsService.calculateCostFromAcus(100.0, 0.05), 0.01);
        assertEquals(0.0, metricsService.calculateCostFromAcus(0.0, 0.05), 0.01);
        assertEquals(25.0, metricsService.calculateCostFromAcus(500.0, 0.05), 0.01);
    }

    @Test
    void testEmptySessions() {
        List<ConsumptionData> empty = List.of();

        assertEquals(0.0, metricsService.calculateTotalMonthlyCost(empty));
        assertEquals(0.0, metricsService.calculateTotalAcus(empty));
        assertTrue(metricsService.calculateCostPerUser(empty).isEmpty());
        assertTrue(metricsService.calculateAcusPerSession(empty).isEmpty());
        assertEquals(0, metricsService.calculateTotalSessions(empty));
        assertTrue(metricsService.calculateSessionsPerUser(empty).isEmpty());
        assertEquals(0, metricsService.calculateTotalDurationMinutes(empty));
        assertEquals(0, metricsService.calculateUniqueUsers(empty));
        assertTrue(metricsService.calculateSessionsByTaskType(empty).isEmpty());
        assertTrue(metricsService.calculateAcusByTaskType(empty).isEmpty());
        assertTrue(metricsService.calculateCostByTaskType(empty).isEmpty());
        assertTrue(metricsService.calculateSessionsByDepartment(empty).isEmpty());
        assertTrue(metricsService.calculateAcusByDepartment(empty).isEmpty());
        assertTrue(metricsService.calculateCostByDepartment(empty).isEmpty());
    }
}
