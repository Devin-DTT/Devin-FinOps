package com.devin.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single API endpoint definition loaded from endpoints.yaml.
 * Maps each endpoint entry with its name, HTTP method, path, scope, and resolved base URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointDefinition {

    /** Unique identifier for this endpoint (e.g. "list_sessions") */
    private String name;

    /** The URL path template (e.g. "/{org_id}/sessions") */
    private String path;

    /** HTTP method: GET, POST, PUT, PATCH, DELETE */
    private String method;

    /** Resolved base URL depending on scope (organization or enterprise) */
    private String baseUrl;

    /** Scope of the endpoint: "organization" or "enterprise" */
    private String scope;

    /** Human-readable description of the endpoint */
    private String description;

    /** Whether this is a beta endpoint */
    private boolean beta;

    /**
     * Builds the full URL by combining baseUrl + path, replacing path parameters.
     *
     * @param pathParams key-value pairs for path parameter substitution (e.g. "org_id" -> "abc123")
     * @return the fully-resolved URL ready for HTTP invocation
     */
    public String buildUrl(java.util.Map<String, String> pathParams) {
        String fullUrl = baseUrl + path;
        if (pathParams != null) {
            for (java.util.Map.Entry<String, String> entry : pathParams.entrySet()) {
                fullUrl = fullUrl.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return fullUrl;
    }
}
