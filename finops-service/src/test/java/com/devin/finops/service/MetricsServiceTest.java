package com.devin.finops.service;

import com.devin.finops.config.MetricsProperties;
import com.devin.finops.model.ConsumptionData;
import com.devin.finops.model.MetricsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsService covering all 20 foundational metrics.
 * Mirrors Python test coverage from tests/test_metrics_calculator.py.
 */
class MetricsServiceTest {

    // =========================================================================
    // BASIC METRICS TESTS (equivalent to Python TestMetricsCalculatorBasic)
    // =========================================================================

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

    @Test
    void testCalculateAllMetricsValues() {
        // Equivalent to Python test_calculate_all_metrics_values
        MetricsResult result = metricsService.calculateAllMetrics(
                testSessions, "2025-10-01", "2025-10-31");
        Map<String, Object> m = result.getMetrics();

        assertEquals(21.25, (double) m.get("01_total_monthly_cost"), 0.01);
        assertEquals(425.0, (double) m.get("02_total_acus"), 0.01);
        assertEquals(4, m.get("06_total_sessions"));
        assertEquals(3, m.get("12_unique_users"));
    }

    @Test
    void testCalculateAllMetricsNullDates() {
        MetricsResult result = metricsService.calculateAllMetrics(testSessions, null, null);
        assertNotNull(result);
        assertEquals("N/A", result.getReportingPeriod().get("start_date"));
        assertEquals("N/A", result.getReportingPeriod().get("end_date"));
    }

    // =========================================================================
    // EMPTY DATA TESTS (equivalent to Python TestMetricsCalculatorEmptyData)
    // =========================================================================

    @Nested
    class EmptyDataTests {

        private MetricsService emptyService;
        private List<ConsumptionData> emptySessions;

        @BeforeEach
        void setUp() {
            MetricsProperties config = new MetricsProperties();
            config.setPricePerAcu(0.10);
            config.setCurrency("USD");
            emptyService = new MetricsService(config);
            emptySessions = List.of();
        }

        @Test
        void testEmptyTotalAcus() {
            assertEquals(0.0, emptyService.calculateTotalAcus(emptySessions));
        }

        @Test
        void testEmptyTotalMonthlyCost() {
            assertEquals(0.0, emptyService.calculateTotalMonthlyCost(emptySessions));
        }

        @Test
        void testEmptyTotalSessions() {
            assertEquals(0, emptyService.calculateTotalSessions(emptySessions));
        }

        @Test
        void testEmptyUniqueUsers() {
            assertEquals(0, emptyService.calculateUniqueUsers(emptySessions));
        }

        @Test
        void testEmptyAverageAcusPerSession() {
            assertEquals(0.0, emptyService.calculateAverageAcusPerSession(emptySessions));
        }

        @Test
        void testEmptyAverageSessionDuration() {
            assertEquals(0.0, emptyService.calculateAverageSessionDuration(emptySessions));
        }

        @Test
        void testEmptyAcusPerMinute() {
            assertEquals(0.0, emptyService.calculateAcusPerMinute(emptySessions));
        }

        @Test
        void testEmptyCostPerMinute() {
            assertEquals(0.0, emptyService.calculateCostPerMinute(emptySessions));
        }

        @Test
        void testEmptyAverageCostPerUser() {
            assertEquals(0.0, emptyService.calculateAverageCostPerUser(emptySessions));
        }

        @Test
        void testEmptyEfficiencyRatio() {
            assertEquals(0.0, emptyService.calculateEfficiencyRatio(emptySessions));
        }

        @Test
        void testEmptyCostPerUser() {
            assertTrue(emptyService.calculateCostPerUser(emptySessions).isEmpty());
        }

        @Test
        void testEmptySessionsPerUser() {
            assertTrue(emptyService.calculateSessionsPerUser(emptySessions).isEmpty());
        }

        @Test
        void testEmptySessionsByTaskType() {
            assertTrue(emptyService.calculateSessionsByTaskType(emptySessions).isEmpty());
        }

        @Test
        void testEmptySessionsByDepartment() {
            assertTrue(emptyService.calculateSessionsByDepartment(emptySessions).isEmpty());
        }

        @Test
        void testEmptyTotalDuration() {
            assertEquals(0, emptyService.calculateTotalDurationMinutes(emptySessions));
        }
    }

    // =========================================================================
    // CUSTOM CONFIG TESTS (equivalent to Python TestMetricsCalculatorCustomConfig)
    // =========================================================================

    @Nested
    class CustomConfigTests {

        private List<ConsumptionData> singleSession;

        @BeforeEach
        void setUp() {
            singleSession = List.of(new ConsumptionData(
                    "s1", "dev_001", "org_001", "proj_001", null,
                    "2025-10-01T10:00:00", 600.0, "Platform", "code_review",
                    false, true, "Success"
            ));
        }

        @Test
        void testDefaultConfig() {
            MetricsProperties defaultConfig = new MetricsProperties();
            // default pricePerAcu = 0.05
            MetricsService defaultService = new MetricsService(defaultConfig);
            assertEquals(600.0 * 0.05, defaultService.calculateTotalMonthlyCost(singleSession), 0.01);
        }

        @Test
        void testCustomPricePerAcu() {
            MetricsProperties customConfig = new MetricsProperties();
            customConfig.setPricePerAcu(0.25);
            customConfig.setCurrency("EUR");
            MetricsService customService = new MetricsService(customConfig);
            assertEquals(150.0, customService.calculateTotalMonthlyCost(singleSession), 0.01);
        }

        @Test
        void testConfigPropertiesInResult() {
            MetricsProperties customConfig = new MetricsProperties();
            customConfig.setPricePerAcu(0.08);
            customConfig.setCurrency("GBP");
            customConfig.setWorkingHoursPerDay(7);
            customConfig.setWorkingDaysPerMonth(20);
            MetricsService customService = new MetricsService(customConfig);

            MetricsResult result = customService.calculateAllMetrics(
                    singleSession, "2025-10-01", "2025-10-31");

            assertEquals(0.08, result.getConfig().get("price_per_acu"));
            assertEquals("GBP", result.getConfig().get("currency"));
            assertEquals(7, result.getConfig().get("working_hours_per_day"));
            assertEquals(20, result.getConfig().get("working_days_per_month"));
        }

        @Test
        void testCostScalesLinearly() {
            // Equivalent to Python test_cost_scales_linearly
            double[] prices = {0.01, 0.10, 1.00};
            double[] costs = new double[3];

            for (int i = 0; i < prices.length; i++) {
                MetricsProperties cfg = new MetricsProperties();
                cfg.setPricePerAcu(prices[i]);
                MetricsService svc = new MetricsService(cfg);
                costs[i] = svc.calculateTotalMonthlyCost(singleSession);
            }

            assertEquals(10.0, costs[1] / costs[0], 0.01);
            assertEquals(10.0, costs[2] / costs[1], 0.01);
        }
    }

    // =========================================================================
    // SINGLE USER TESTS (equivalent to Python TestMetricsCalculatorSingleUser)
    // =========================================================================

    @Nested
    class SingleUserTests {

        private MetricsService singleUserService;
        private List<ConsumptionData> singleUserSessions;

        @BeforeEach
        void setUp() {
            MetricsProperties config = new MetricsProperties();
            config.setPricePerAcu(0.05);
            singleUserService = new MetricsService(config);

            singleUserSessions = new ArrayList<>();
            String[] taskTypes = {"testing", "deployment", "testing", "deployment"};
            for (int i = 0; i < 4; i++) {
                singleUserSessions.add(new ConsumptionData(
                        "s" + i, "solo_user", "org_001", "proj_001", null,
                        "2025-10-01T10:00:00", 100.0, "DevOps", taskTypes[i],
                        false, true, "Success"
                ));
            }
        }

        @Test
        void testSingleUserUniqueCount() {
            assertEquals(1, singleUserService.calculateUniqueUsers(singleUserSessions));
        }

        @Test
        void testSingleUserSessions() {
            Map<String, Integer> sessions = singleUserService.calculateSessionsPerUser(singleUserSessions);
            assertEquals(4, sessions.get("solo_user@deloitte.com"));
        }

        @Test
        void testSingleUserTotalAcus() {
            assertEquals(400.0, singleUserService.calculateTotalAcus(singleUserSessions), 0.01);
        }

        @Test
        void testSingleUserAverageCostEqualsTotal() {
            double total = singleUserService.calculateTotalMonthlyCost(singleUserSessions);
            double avg = singleUserService.calculateAverageCostPerUser(singleUserSessions);
            assertEquals(total, avg, 0.01);
        }

        @Test
        void testSingleUserDepartmentSessions() {
            Map<String, Integer> dept = singleUserService.calculateSessionsByDepartment(singleUserSessions);
            assertEquals(4, dept.get("DevOps"));
        }

        @Test
        void testTaskTypeDistribution() {
            Map<String, Integer> tasks = singleUserService.calculateSessionsByTaskType(singleUserSessions);
            assertEquals(2, tasks.get("testing"));
            assertEquals(2, tasks.get("deployment"));
        }

        @Test
        void testEfficiencyRatioSingleUser() {
            // Each session: max(1, 100/5) = 20 min, total = 80 min, hours = 80/60
            // Efficiency = 400 / (80/60) = 300.0
            double ratio = singleUserService.calculateEfficiencyRatio(singleUserSessions);
            double totalHours = 80.0 / 60.0;
            assertEquals(400.0 / totalHours, ratio, 0.1);
        }
    }

    // =========================================================================
    // MISSING / NULL FIELDS TESTS (equivalent to Python TestMetricsCalculatorMissingFields)
    // =========================================================================

    @Nested
    class MissingFieldsTests {

        private MetricsService missingFieldsService;
        private List<ConsumptionData> sessionsWithNulls;

        @BeforeEach
        void setUp() {
            MetricsProperties config = new MetricsProperties();
            config.setPricePerAcu(0.10);
            missingFieldsService = new MetricsService(config);

            sessionsWithNulls = new ArrayList<>();

            // Session with null taskType and null businessUnit
            ConsumptionData s1 = new ConsumptionData();
            s1.setSessionId("s1");
            s1.setUserId("user_x");
            s1.setAcuConsumed(0.0); // missing/zero ACUs
            // taskType = null, businessUnit = null

            ConsumptionData s2 = new ConsumptionData();
            s2.setSessionId("s2");
            s2.setUserId("user_x");
            s2.setAcuConsumed(200.0);
            s2.setTaskType("Feature");
            s2.setBusinessUnit("Engineering");

            sessionsWithNulls.add(s1);
            sessionsWithNulls.add(s2);
        }

        @Test
        void testMissingAcusDefaultsToZero() {
            // Total ACUs should be 0 + 200 = 200
            assertEquals(200.0, missingFieldsService.calculateTotalAcus(sessionsWithNulls), 0.01);
        }

        @Test
        void testNullTaskTypeDefaultsToUnknown() {
            Map<String, Integer> tasks = missingFieldsService.calculateSessionsByTaskType(sessionsWithNulls);
            assertTrue(tasks.containsKey("unknown"));
            assertEquals(1, tasks.get("unknown"));
            assertEquals(1, tasks.get("Feature"));
        }

        @Test
        void testNullBusinessUnitDefaultsToUnknown() {
            Map<String, Integer> depts = missingFieldsService.calculateSessionsByDepartment(sessionsWithNulls);
            assertTrue(depts.containsKey("Unknown"));
            assertEquals(1, depts.get("Unknown"));
            assertEquals(1, depts.get("Engineering"));
        }

        @Test
        void testNullTaskTypeAcusByTaskType() {
            Map<String, Double> acusByTask = missingFieldsService.calculateAcusByTaskType(sessionsWithNulls);
            assertEquals(0.0, acusByTask.get("unknown"), 0.01);
            assertEquals(200.0, acusByTask.get("Feature"), 0.01);
        }

        @Test
        void testNullBusinessUnitAcusByDepartment() {
            Map<String, Double> acusByDept = missingFieldsService.calculateAcusByDepartment(sessionsWithNulls);
            assertEquals(0.0, acusByDept.get("Unknown"), 0.01);
            assertEquals(200.0, acusByDept.get("Engineering"), 0.01);
        }
    }

    // =========================================================================
    // LARGE DATASET TESTS (equivalent to Python TestMetricsCalculatorLargeDataset)
    // =========================================================================

    @Nested
    class LargeDatasetTests {

        private MetricsService largeService;
        private List<ConsumptionData> largeSessions;

        @BeforeEach
        void setUp() {
            MetricsProperties config = new MetricsProperties();
            config.setPricePerAcu(0.05);
            largeService = new MetricsService(config);

            largeSessions = new ArrayList<>();
            String[] taskTypes = {"dev", "test", "review", "deploy"};
            String[] departments = {"Engineering", "QA", "Operations"};

            for (int i = 0; i < 100; i++) {
                largeSessions.add(new ConsumptionData(
                        String.format("s%04d", i),
                        String.format("user_%04d", i % 10),
                        "org_001",
                        "proj_001",
                        null,
                        "2025-10-15T10:00:00",
                        100.0 + (i % 7) * 50.0,
                        departments[i % 3],
                        taskTypes[i % 4],
                        false, true, "Success"
                ));
            }
        }

        @Test
        void testTotalSessionsCount() {
            assertEquals(100, largeService.calculateTotalSessions(largeSessions));
        }

        @Test
        void testUniqueUsersCount() {
            assertEquals(10, largeService.calculateUniqueUsers(largeSessions));
        }

        @Test
        void testCostPerUserSumsToTotal() {
            Map<String, Double> costPerUser = largeService.calculateCostPerUser(largeSessions);
            double totalFromUsers = costPerUser.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalCost = largeService.calculateTotalMonthlyCost(largeSessions);
            assertEquals(totalCost, totalFromUsers, 0.01);
        }

        @Test
        void testSessionsPerUserSumsToTotal() {
            Map<String, Integer> sessionsPerUser = largeService.calculateSessionsPerUser(largeSessions);
            int totalFromUsers = sessionsPerUser.values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(100, totalFromUsers);
        }

        @Test
        void testTaskTypeSessionsSumToTotal() {
            Map<String, Integer> byTask = largeService.calculateSessionsByTaskType(largeSessions);
            int total = byTask.values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(100, total);
        }

        @Test
        void testDepartmentSessionsSumToTotal() {
            Map<String, Integer> byDept = largeService.calculateSessionsByDepartment(largeSessions);
            int total = byDept.values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(100, total);
        }

        @Test
        void testCostByTaskSumsToTotal() {
            Map<String, Double> costByTask = largeService.calculateCostByTaskType(largeSessions);
            double totalFromTasks = costByTask.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalCost = largeService.calculateTotalMonthlyCost(largeSessions);
            assertEquals(totalCost, totalFromTasks, 0.01);
        }

        @Test
        void testCostByDeptSumsToTotal() {
            Map<String, Double> costByDept = largeService.calculateCostByDepartment(largeSessions);
            double totalFromDepts = costByDept.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalCost = largeService.calculateTotalMonthlyCost(largeSessions);
            assertEquals(totalCost, totalFromDepts, 0.01);
        }

        @Test
        void testAcusByTaskSumsToTotal() {
            Map<String, Double> acusByTask = largeService.calculateAcusByTaskType(largeSessions);
            double totalFromTasks = acusByTask.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalAcus = largeService.calculateTotalAcus(largeSessions);
            assertEquals(totalAcus, totalFromTasks, 0.01);
        }

        @Test
        void testAcusByDeptSumsToTotal() {
            Map<String, Double> acusByDept = largeService.calculateAcusByDepartment(largeSessions);
            double totalFromDepts = acusByDept.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalAcus = largeService.calculateTotalAcus(largeSessions);
            assertEquals(totalAcus, totalFromDepts, 0.01);
        }
    }

    // =========================================================================
    // MONTHLY ACUS FROM DAILY (references Python calculate_monthly_acus_from_daily)
    // =========================================================================

    @Nested
    class MonthlyAcusFromDailyTests {

        /**
         * Tests for calculateMonthlyAcusFromDaily which filters consumption_by_date
         * by month prefix (YYYY-MM format).
         * Sources used: Python Function (calculate_monthly_acus_from_daily),
         * raw_value: ACUs filtered from consumption_by_date
         */

        @Test
        void testFiltersByMonthPrefix() {
            Map<String, Double> consumptionByDate = new HashMap<>();
            consumptionByDate.put("2025-11-01", 50.0);
            consumptionByDate.put("2025-11-15", 75.0);
            consumptionByDate.put("2025-11-30", 25.0);
            consumptionByDate.put("2025-12-01", 100.0);

            double novemberAcus = metricsService.calculateMonthlyAcusFromDaily(
                    consumptionByDate, "2025-11");
            assertEquals(150.0, novemberAcus, 0.01);
        }

        @Test
        void testNoMatchReturnsZero() {
            Map<String, Double> consumptionByDate = new HashMap<>();
            consumptionByDate.put("2025-09-01", 100.0);

            double result = metricsService.calculateMonthlyAcusFromDaily(
                    consumptionByDate, "2025-10");
            assertEquals(0.0, result, 0.01);
        }

        @Test
        void testEmptyMapReturnsZero() {
            double result = metricsService.calculateMonthlyAcusFromDaily(
                    new HashMap<>(), "2025-10");
            assertEquals(0.0, result, 0.01);
        }

        @Test
        void testMultipleMonthsIsolated() {
            Map<String, Double> consumptionByDate = new HashMap<>();
            consumptionByDate.put("2025-10-01", 100.0);
            consumptionByDate.put("2025-10-15", 200.0);
            consumptionByDate.put("2025-11-01", 75.0);
            consumptionByDate.put("2025-11-15", 125.0);
            consumptionByDate.put("2025-12-01", 50.0);

            assertEquals(300.0, metricsService.calculateMonthlyAcusFromDaily(
                    consumptionByDate, "2025-10"), 0.01);
            assertEquals(200.0, metricsService.calculateMonthlyAcusFromDaily(
                    consumptionByDate, "2025-11"), 0.01);
            assertEquals(50.0, metricsService.calculateMonthlyAcusFromDaily(
                    consumptionByDate, "2025-12"), 0.01);
        }
    }
}
