package com.devin.finops.model;

import java.util.Map;

/**
 * Container for the complete metrics calculation result.
 * Mirrors the Python MetricsCalculator.calculate_all_metrics() return value.
 */
public class MetricsResult {

    private Map<String, Object> config;
    private Map<String, String> reportingPeriod;
    private Map<String, Object> metrics;

    public MetricsResult() {
    }

    public MetricsResult(Map<String, Object> config, Map<String, String> reportingPeriod,
                         Map<String, Object> metrics) {
        this.config = config;
        this.reportingPeriod = reportingPeriod;
        this.metrics = metrics;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public Map<String, String> getReportingPeriod() {
        return reportingPeriod;
    }

    public void setReportingPeriod(Map<String, String> reportingPeriod) {
        this.reportingPeriod = reportingPeriod;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }
}
