package com.devin.dashboard.config;

import com.devin.dashboard.model.EndpointDefinition;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and parses the endpoints.yaml file from the project root.
 * Resolves the correct base URL (organizations or enterprise) for each endpoint
 * and exposes the full list of {@link EndpointDefinition} objects for injection.
 */
@Slf4j
@Component
public class EndpointLoader {

    private static final String ENDPOINTS_FILE = "endpoints.yaml";

    @Getter
    private List<EndpointDefinition> endpoints = Collections.emptyList();

    @Getter
    private Map<String, String> baseUrls = Collections.emptyMap();

    @PostConstruct
    public void init() {
        loadEndpoints();
    }

    /**
     * Reads endpoints.yaml from the project root (or classpath as fallback),
     * parses every endpoint entry, and resolves its base URL based on scope.
     */
    @SuppressWarnings("unchecked")
    private void loadEndpoints() {
        Yaml yaml = new Yaml();
        Map<String, Object> root = null;

        // Try loading from project root first
        Path projectRoot = Paths.get(ENDPOINTS_FILE);
        if (Files.exists(projectRoot)) {
            try (InputStream is = Files.newInputStream(projectRoot)) {
                root = yaml.load(is);
                log.info("Loaded endpoints.yaml from project root: {}", projectRoot.toAbsolutePath());
            } catch (Exception e) {
                log.warn("Failed to read endpoints.yaml from project root, falling back to classpath", e);
            }
        }

        // Fallback: load from classpath
        if (root == null) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(ENDPOINTS_FILE)) {
                if (is != null) {
                    root = yaml.load(is);
                    log.info("Loaded endpoints.yaml from classpath");
                }
            } catch (Exception e) {
                log.error("Failed to load endpoints.yaml from classpath", e);
            }
        }

        if (root == null) {
            log.error("endpoints.yaml not found – no endpoints will be available");
            return;
        }

        // Parse base_urls
        Map<String, String> urls = (Map<String, String>) root.get("base_urls");
        if (urls != null) {
            this.baseUrls = Map.copyOf(urls);
        }
        String orgBaseUrl = baseUrls.getOrDefault("organizations", "https://api.devin.ai/v3/organizations");
        String entBaseUrl = baseUrls.getOrDefault("enterprise", "https://api.devin.ai/v3/enterprise");

        // Parse endpoints list
        List<Map<String, Object>> rawEndpoints = (List<Map<String, Object>>) root.get("endpoints");
        if (rawEndpoints == null || rawEndpoints.isEmpty()) {
            log.warn("No endpoints found in endpoints.yaml");
            return;
        }

        List<EndpointDefinition> parsed = new ArrayList<>();
        for (Map<String, Object> entry : rawEndpoints) {
            String name = (String) entry.get("name");
            String scope = (String) entry.getOrDefault("scope", "organization");
            String path = (String) entry.get("path");
            String method = (String) entry.getOrDefault("method", "GET");
            String description = (String) entry.get("description");
            boolean beta = Boolean.TRUE.equals(entry.get("beta"));

            // Resolve base URL: beta endpoints may override; otherwise scope-based
            String resolvedBaseUrl;
            if (entry.containsKey("base_url_override")) {
                resolvedBaseUrl = (String) entry.get("base_url_override");
            } else if ("enterprise".equalsIgnoreCase(scope)) {
                resolvedBaseUrl = entBaseUrl;
            } else {
                resolvedBaseUrl = orgBaseUrl;
            }

            EndpointDefinition def = EndpointDefinition.builder()
                    .name(name)
                    .path(path)
                    .method(method)
                    .baseUrl(resolvedBaseUrl)
                    .scope(scope)
                    .description(description)
                    .beta(beta)
                    .build();

            parsed.add(def);
        }

        this.endpoints = Collections.unmodifiableList(parsed);
        log.info("Loaded {} endpoint definitions from endpoints.yaml", endpoints.size());
    }

    /**
     * Find an endpoint definition by its unique name.
     *
     * @param name the endpoint name (e.g. "list_sessions")
     * @return an Optional containing the endpoint if found
     */
    public Optional<EndpointDefinition> findByName(String name) {
        return endpoints.stream()
                .filter(e -> e.getName().equals(name))
                .findFirst();
    }

    /**
     * Get all endpoints matching a given scope.
     *
     * @param scope "organization" or "enterprise"
     * @return list of matching endpoint definitions
     */
    public List<EndpointDefinition> findByScope(String scope) {
        return endpoints.stream()
                .filter(e -> scope.equalsIgnoreCase(e.getScope()))
                .toList();
    }

    /**
     * Get only GET endpoints (useful for polling/dashboard data).
     *
     * @return list of GET endpoint definitions
     */
    public List<EndpointDefinition> getReadEndpoints() {
        return endpoints.stream()
                .filter(e -> "GET".equalsIgnoreCase(e.getMethod()))
                .toList();
    }
}
