package com.devin.finops.billing.controller;

import com.devin.finops.billing.model.FinOpsKpis;
import com.devin.finops.billing.service.BillingApiProxy;
import com.devin.finops.billing.service.BillingCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingCacheService cacheService;
    private final BillingApiProxy apiProxy;

    public BillingController(BillingCacheService cacheService,
                             BillingApiProxy apiProxy) {
        this.cacheService = cacheService;
        this.apiProxy = apiProxy;
    }

    @GetMapping("/cycles")
    public ResponseEntity<JsonNode> listBillingCycles() {
        return cacheService.getBillingCycles()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/consumption/daily")
    public ResponseEntity<JsonNode> getDailyConsumption() {
        return cacheService.getDailyConsumption()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/acu-limits")
    public ResponseEntity<JsonNode> getAcuLimits() {
        return cacheService.getAcuLimits()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PutMapping("/acu-limits/orgs/{orgId}")
    public ResponseEntity<String> setOrgAcuLimit(
            @PathVariable String orgId,
            @RequestBody Map<String, Object> body) {
        String result = apiProxy.setOrgAcuLimit(orgId, body).block();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/acu-limits/orgs/{orgId}")
    public ResponseEntity<String> deleteOrgAcuLimit(@PathVariable String orgId) {
        String result = apiProxy.deleteOrgAcuLimit(orgId).block();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/org-group-limits")
    public ResponseEntity<JsonNode> getOrgGroupLimits() {
        return cacheService.getOrgGroupLimits()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * GET /api/billing/finops-kpis - Returns calculated FinOps KPIs.
     */
    @GetMapping("/finops-kpis")
    public ResponseEntity<FinOpsKpis> getFinOpsKpis() {
        double currentAcu = 0;
        double currentLimit = 0;
        int totalSessions = 0;

        // Extract current cycle ACU from billing cycles
        JsonNode cycles = cacheService.getBillingCycles().orElse(null);
        if (cycles != null) {
            JsonNode cyclesArr = cycles.has("cycles") ? cycles.get("cycles")
                    : (cycles.has("items") ? cycles.get("items") : cycles);
            if (cyclesArr.isArray() && cyclesArr.size() > 0) {
                JsonNode last = cyclesArr.get(cyclesArr.size() - 1);
                currentAcu = last.path("acu_usage").asDouble(0);
                currentLimit = last.path("acu_limit").asDouble(0);
            }
        }

        // Extract total sessions count from Redis
        JsonNode sessionsData = cacheService.readKeyDirect("list_enterprise_sessions");
        if (sessionsData == null) {
            sessionsData = cacheService.readKeyDirect("list_sessions");
        }
        if (sessionsData != null) {
            if (sessionsData.has("total_count")) {
                totalSessions = sessionsData.get("total_count").asInt(0);
            } else if (sessionsData.has("items") && sessionsData.get("items").isArray()) {
                totalSessions = sessionsData.get("items").size();
            }
        }

        int userCount = cacheService.getUserCount();
        int acuUsagePercent = currentLimit > 0
                ? (int) Math.round((currentAcu / currentLimit) * 100)
                : 0;

        FinOpsKpis kpis = FinOpsKpis.builder()
                .currentCycleAcu(currentAcu)
                .currentCycleLimit(currentLimit)
                .acuUsagePercent(acuUsagePercent)
                .acuPerUser(userCount > 0 ? currentAcu / userCount : 0)
                .acuPerSession(totalSessions > 0 ? currentAcu / totalSessions : 0)
                .projectedEndOfCycleAcu(0) // TODO: calculate based on daily consumption trend
                .userCount(userCount)
                .totalSessions(totalSessions)
                .build();

        return ResponseEntity.ok(kpis);
    }
}
