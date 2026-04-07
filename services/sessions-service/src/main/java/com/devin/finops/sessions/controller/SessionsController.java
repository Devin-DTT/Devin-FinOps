package com.devin.finops.sessions.controller;

import com.devin.finops.sessions.service.SessionsApiProxy;
import com.devin.finops.sessions.service.SessionsCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/sessions")
public class SessionsController {

    private final SessionsCacheService cacheService;
    private final SessionsApiProxy apiProxy;

    public SessionsController(SessionsCacheService cacheService,
                              SessionsApiProxy apiProxy) {
        this.cacheService = cacheService;
        this.apiProxy = apiProxy;
    }

    /**
     * GET /api/sessions - Returns cached sessions from Redis.
     * Tries enterprise sessions first, falls back to org sessions.
     */
    @GetMapping
    public ResponseEntity<JsonNode> listSessions() {
        return cacheService.getEnterpriseSessionsList()
                .or(cacheService::getSessionsList)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * GET /api/sessions/{sessionId} - Proxy to Devin API.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<String> getSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "default") String orgId) {
        String result = apiProxy.getSession(orgId, sessionId).block();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/sessions/{sessionId}/messages - Proxy to Devin API.
     */
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<String> getSessionMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "default") String orgId) {
        String result = apiProxy.getSessionMessages(orgId, sessionId).block();
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/sessions - Proxy to Devin API.
     */
    @PostMapping
    public ResponseEntity<String> createSession(
            @RequestParam(defaultValue = "default") String orgId,
            @RequestBody Map<String, Object> body) {
        String result = apiProxy.createSession(orgId, body).block();
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/sessions/{sessionId}/messages - Proxy to Devin API.
     */
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<String> sendMessage(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "default") String orgId,
            @RequestBody Map<String, Object> body) {
        String result = apiProxy.sendMessage(orgId, sessionId, body).block();
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/sessions/{sessionId}/archive - Proxy to Devin API.
     */
    @PostMapping("/{sessionId}/archive")
    public ResponseEntity<String> archiveSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "default") String orgId) {
        String result = apiProxy.archiveSession(orgId, sessionId).block();
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/sessions/{sessionId}/terminate - Proxy to Devin API.
     */
    @PostMapping("/{sessionId}/terminate")
    public ResponseEntity<String> terminateSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "default") String orgId) {
        String result = apiProxy.terminateSession(orgId, sessionId).block();
        return ResponseEntity.ok(result);
    }

    /**
     * DELETE /api/sessions/{sessionId} - Proxy to Devin API.
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<String> deleteSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "default") String orgId) {
        String result = apiProxy.deleteSession(orgId, sessionId).block();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/sessions/{sessionId}/tags - Proxy to Devin API.
     */
    @GetMapping("/{sessionId}/tags")
    public ResponseEntity<String> listSessionTags(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "default") String orgId) {
        String result = apiProxy.listSessionTags(orgId, sessionId).block();
        return ResponseEntity.ok(result);
    }

    /**
     * PUT /api/sessions/{sessionId}/tags - Proxy to Devin API.
     */
    @PutMapping("/{sessionId}/tags")
    public ResponseEntity<String> updateSessionTags(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "default") String orgId,
            @RequestBody Map<String, Object> body) {
        String result = apiProxy.updateSessionTags(orgId, sessionId, body).block();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/sessions/{sessionId}/insights - Proxy to Devin API.
     */
    @GetMapping("/{sessionId}/insights")
    public ResponseEntity<String> getSessionInsights(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "default") String orgId) {
        String result = apiProxy.getSessionInsights(orgId, sessionId).block();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/sessions/schedules - Returns cached schedules from Redis.
     */
    @GetMapping("/schedules")
    public ResponseEntity<JsonNode> listSchedules() {
        return cacheService.getSchedulesList()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * POST /api/sessions/schedules - Proxy to Devin API.
     */
    @PostMapping("/schedules")
    public ResponseEntity<String> createSchedule(
            @RequestParam(defaultValue = "default") String orgId,
            @RequestBody Map<String, Object> body) {
        String result = apiProxy.createSchedule(orgId, body).block();
        return ResponseEntity.ok(result);
    }
}
