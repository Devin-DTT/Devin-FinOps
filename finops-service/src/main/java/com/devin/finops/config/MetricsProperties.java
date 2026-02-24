package com.devin.finops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for metrics calculation.
 * Maps to the Python MetricsConfig class (config.py).
 *
 * Configurable via application.properties with prefix "finops.metrics".
 */
@Component
@ConfigurationProperties(prefix = "finops.metrics")
public class MetricsProperties {

    private double pricePerAcu = 0.05;
    private String currency = "USD";
    private int workingHoursPerDay = 8;
    private int workingDaysPerMonth = 22;

    public double getPricePerAcu() {
        return pricePerAcu;
    }

    public void setPricePerAcu(double pricePerAcu) {
        this.pricePerAcu = pricePerAcu;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getWorkingHoursPerDay() {
        return workingHoursPerDay;
    }

    public void setWorkingHoursPerDay(int workingHoursPerDay) {
        this.workingHoursPerDay = workingHoursPerDay;
    }

    public int getWorkingDaysPerMonth() {
        return workingDaysPerMonth;
    }

    public void setWorkingDaysPerMonth(int workingDaysPerMonth) {
        this.workingDaysPerMonth = workingDaysPerMonth;
    }
}
