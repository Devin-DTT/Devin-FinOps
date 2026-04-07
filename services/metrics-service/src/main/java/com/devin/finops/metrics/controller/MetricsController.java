package com.devin.finops.metrics.controller;

import com.devin.finops.metrics.service.MetricsCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsCacheService cacheService;

    public MetricsController(MetricsCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @GetMapping("/dau")
    public ResponseEntity<JsonNode> getDauMetrics() {
        return cacheService.getDauMetrics()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/wau")
    public ResponseEntity<JsonNode> getWauMetrics() {
        return cacheService.getWauMetrics()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/mau")
    public ResponseEntity<JsonNode> getMauMetrics() {
        return cacheService.getMauMetrics()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/active-users")
    public ResponseEntity<JsonNode> getActiveUsersMetrics() {
        return cacheService.getActiveUsersMetrics()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/sessions")
    public ResponseEntity<JsonNode> getSessionsMetrics() {
        return cacheService.getSessionsMetrics()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/searches")
    public ResponseEntity<JsonNode> getSearchesMetrics() {
        return cacheService.getSearchesMetrics()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/prs")
    public ResponseEntity<JsonNode> getPrsMetrics() {
        return cacheService.getPrsMetrics()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/usage")
    public ResponseEntity<JsonNode> getUsageMetrics() {
        return cacheService.getUsageMetrics()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
