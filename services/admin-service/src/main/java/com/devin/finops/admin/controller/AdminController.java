package com.devin.finops.admin.controller;

import com.devin.finops.admin.service.AdminApiProxy;
import com.devin.finops.admin.service.AdminCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminCacheService cacheService;
    private final AdminApiProxy apiProxy;

    public AdminController(AdminCacheService cacheService,
                           AdminApiProxy apiProxy) {
        this.cacheService = cacheService;
        this.apiProxy = apiProxy;
    }

    // --- Organizations ---
    @GetMapping("/organizations")
    public ResponseEntity<JsonNode> listOrganizations() {
        return cacheService.getOrganizations()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // --- Users ---
    @GetMapping("/users")
    public ResponseEntity<JsonNode> listUsers() {
        return cacheService.getUsers()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // --- Roles ---
    @GetMapping("/roles")
    public ResponseEntity<JsonNode> listRoles() {
        return cacheService.getRoles()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // --- IDP Groups ---
    @GetMapping("/idp-groups")
    public ResponseEntity<JsonNode> listIdpGroups() {
        return cacheService.getIdpGroups()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/idp-groups")
    public ResponseEntity<String> createIdpGroups(@RequestBody Map<String, Object> body) {
        String result = apiProxy.createIdpGroups(body).block();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/idp-groups/{name}")
    public ResponseEntity<String> deleteIdpGroup(@PathVariable String name) {
        String result = apiProxy.deleteIdpGroup(name).block();
        return ResponseEntity.ok(result);
    }

    // --- Knowledge ---
    @GetMapping("/knowledge")
    public ResponseEntity<JsonNode> listKnowledge() {
        return cacheService.getKnowledge()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/knowledge")
    public ResponseEntity<String> createKnowledge(
            @RequestParam(defaultValue = "default") String orgId,
            @RequestBody Map<String, Object> body) {
        String result = apiProxy.createKnowledge(orgId, body).block();
        return ResponseEntity.ok(result);
    }

    // --- Playbooks ---
    @GetMapping("/playbooks")
    public ResponseEntity<JsonNode> listPlaybooks() {
        return cacheService.getPlaybooks()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/playbooks")
    public ResponseEntity<String> createPlaybook(
            @RequestParam(defaultValue = "default") String orgId,
            @RequestBody Map<String, Object> body) {
        String result = apiProxy.createPlaybook(orgId, body).block();
        return ResponseEntity.ok(result);
    }

    // --- Secrets (NEVER cached - always proxy) ---
    @GetMapping("/secrets")
    public ResponseEntity<String> listSecrets(
            @RequestParam(defaultValue = "default") String orgId) {
        String result = apiProxy.listSecrets(orgId).block();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/secrets")
    public ResponseEntity<String> createSecret(
            @RequestParam(defaultValue = "default") String orgId,
            @RequestBody Map<String, Object> body) {
        String result = apiProxy.createSecret(orgId, body).block();
        return ResponseEntity.ok(result);
    }

    // --- Git Connections ---
    @GetMapping("/git/connections")
    public ResponseEntity<JsonNode> listGitConnections() {
        return cacheService.getGitConnections()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // --- Git Permissions ---
    @GetMapping("/git/permissions")
    public ResponseEntity<JsonNode> listGitPermissions() {
        return cacheService.getGitPermissions()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/git/permissions")
    public ResponseEntity<String> createGitPermissions(@RequestBody Map<String, Object> body) {
        String result = apiProxy.createGitPermissions(body).block();
        return ResponseEntity.ok(result);
    }

    // --- Infrastructure ---
    @GetMapping("/infrastructure/hypervisors")
    public ResponseEntity<JsonNode> listHypervisors() {
        return cacheService.getHypervisors()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/infrastructure/queue")
    public ResponseEntity<JsonNode> getQueueStatus() {
        return cacheService.getQueueStatus()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // --- IP Access List (always proxy) ---
    @GetMapping("/security/ip-access-list")
    public ResponseEntity<String> getIpAccessList() {
        String result = apiProxy.getIpAccessList().block();
        return ResponseEntity.ok(result);
    }

    // --- Audit Logs (NEVER cached - always proxy) ---
    @GetMapping("/audit/logs")
    public ResponseEntity<String> listAuditLogs() {
        String result = apiProxy.listEnterpriseAuditLogs().block();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/audit/orgs/{orgId}/logs")
    public ResponseEntity<String> listOrgAuditLogs(@PathVariable String orgId) {
        String result = apiProxy.listOrgAuditLogs(orgId).block();
        return ResponseEntity.ok(result);
    }

    // --- Guardrails ---
    @GetMapping("/guardrails/violations")
    public ResponseEntity<String> getGuardrailViolations() {
        String result = apiProxy.getGuardrailViolations().block();
        return ResponseEntity.ok(result);
    }
}
