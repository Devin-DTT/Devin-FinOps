package com.devin.finops.admin.service;

import com.devin.finops.admin.config.AdminProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads admin data cached by the data-collector from Redis.
 * NOTE: Secrets and audit logs are NEVER cached for security reasons.
 */
@Slf4j
@Service
public class AdminCacheService {

    private final StringRedisTemplate redisTemplate;
    private final AdminProperties properties;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AdminCacheService(StringRedisTemplate redisTemplate,
                             AdminProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Optional<JsonNode> getOrganizations() {
        return readKey("list_organizations");
    }

    public Optional<JsonNode> getUsers() {
        return readKey("list_users");
    }

    public Optional<JsonNode> getRoles() {
        return readKey("list_roles");
    }

    public Optional<JsonNode> getIdpGroups() {
        return readKey("list_idp_groups");
    }

    public Optional<JsonNode> getKnowledge() {
        return readKey("list_enterprise_knowledge");
    }

    public Optional<JsonNode> getPlaybooks() {
        return readKey("list_enterprise_playbooks");
    }

    public Optional<JsonNode> getGitConnections() {
        return readKey("list_git_connections");
    }

    public Optional<JsonNode> getGitPermissions() {
        return readKey("list_git_permissions");
    }

    public Optional<JsonNode> getHypervisors() {
        return readKey("list_hypervisors");
    }

    public Optional<JsonNode> getQueueStatus() {
        return readKey("get_queue_status");
    }

    private Optional<JsonNode> readKey(String endpointName) {
        try {
            String key = properties.getRedisKeyPrefix() + endpointName;
            String raw = redisTemplate.opsForValue().get(key);
            if (raw != null && !raw.isEmpty()) {
                return Optional.of(MAPPER.readTree(raw));
            }
        } catch (Exception e) {
            log.warn("Failed to read Redis key for {}: {}", endpointName, e.getMessage());
        }
        return Optional.empty();
    }
}
