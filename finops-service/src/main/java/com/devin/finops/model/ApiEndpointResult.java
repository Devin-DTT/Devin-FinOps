package com.devin.finops.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the result of fetching a single API endpoint.
 * Mirrors the Python fetch_api_data() return structure:
 *   {endpoint_name: {status_code, timestamp, response, endpoint_path, full_url}}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiEndpointResult {

    private String endpointPath;
    private String fullUrl;
    private int statusCode;
    private String timestamp;
    private Object response;
    private String error;

    public ApiEndpointResult() {
    }

    public ApiEndpointResult(String endpointPath, String fullUrl, int statusCode,
                             String timestamp, Object response) {
        this.endpointPath = endpointPath;
        this.fullUrl = fullUrl;
        this.statusCode = statusCode;
        this.timestamp = timestamp;
        this.response = response;
    }

    public String getEndpointPath() {
        return endpointPath;
    }

    public void setEndpointPath(String endpointPath) {
        this.endpointPath = endpointPath;
    }

    public String getFullUrl() {
        return fullUrl;
    }

    public void setFullUrl(String fullUrl) {
        this.fullUrl = fullUrl;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "ApiEndpointResult{" +
                "endpointPath='" + endpointPath + '\'' +
                ", statusCode=" + statusCode +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
