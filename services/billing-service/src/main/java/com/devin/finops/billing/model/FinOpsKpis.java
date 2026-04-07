package com.devin.finops.billing.model;

import lombok.Builder;
import lombok.Data;

/**
 * FinOps KPI calculations derived from billing and session data.
 */
@Data
@Builder
public class FinOpsKpis {

    private double currentCycleAcu;
    private double currentCycleLimit;
    private int acuUsagePercent;
    private double acuPerUser;
    private double acuPerSession;
    private double projectedEndOfCycleAcu;
    private int userCount;
    private int totalSessions;
}
