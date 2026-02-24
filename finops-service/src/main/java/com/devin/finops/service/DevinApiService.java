package com.devin.finops.service;

import com.devin.finops.config.DevinApiProperties;
import com.devin.finops.model.ApiEndpointResult;
import com.devin.finops.model.ConsumptionData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service that replaces the Python data_adapter.py module.
 * Provides HTTP client functionality to the Cognition API (api.devin.ai/v2/enterprise)
 * with pagination, retry logic, and multi-endpoint fetching.
 */
@Service
public class DevinApiService {

    private static final Logger logger = LoggerFactory.getLogger(DevinApiService.class);

    private final WebClient webClient;
    private final DevinApiProperties apiProperties;
    private final ObjectMapper objectMapper;

    public DevinApiService(DevinApiProperties apiProperties, ObjectMapper objectMapper) {
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;

        String apiKey = System.getenv("DEVIN_ENTERPRISE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("DEVIN_ENTERPRISE_API_KEY environment variable not set");
        }

        this.webClient = WebClient.builder()
                .baseUrl(apiProperties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", "Bearer " + (apiKey != null ? apiKey : ""))
                .build();
    }

    /**
     * Fetch paginated consumption data from /consumption/daily.
     * Replaces Python fetch_cognition_data().
     *
     * @param startDate Optional start date filter (YYYY-MM-DD)
     * @param endDate   Optional end date filter (YYYY-MM-DD)
     * @return List of consumption data records
     */
    public List<ConsumptionData> fetchConsumptionData(String startDate, String endDate) {
        logger.info("Starting data fetch from Cognition API");
        logger.info("API Base URL: {}", apiProperties.getBaseUrl());

        if (startDate == null && endDate == null) {
            LocalDate now = LocalDate.now();
            endDate = now.format(DateTimeFormatter.ISO_LOCAL_DATE);
            startDate = now.minusDays(365).format(DateTimeFormatter.ISO_LOCAL_DATE);
            logger.info("Auto-calculated date range (last 12 months): {} to {}", startDate, endDate);
        }

        List<ConsumptionData> allData = new ArrayList<>();
        int skip = 0;
        boolean hasMore = true;
        int pageCount = 0;
        int pageSize = apiProperties.getPageSize();

        while (hasMore) {
            pageCount++;
            logger.info("Fetching page {} (skip={}, limit={})", pageCount, skip, pageSize);

            try {
                final String finalStartDate = startDate;
                final String finalEndDate = endDate;
                final int currentSkip = skip;

                Map<String, Object> responseData = webClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/consumption/daily")
                                    .queryParam("skip", currentSkip)
                                    .queryParam("limit", pageSize);
                            if (finalStartDate != null) {
                                uriBuilder.queryParam("start_date", finalStartDate);
                            }
                            if (finalEndDate != null) {
                                uriBuilder.queryParam("end_date", finalEndDate);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .retryWhen(Retry.backoff(apiProperties.getMaxRetries(),
                                Duration.ofMillis(apiProperties.getRetryBaseDelayMs())))
                        .block();

                if (responseData != null) {
                    Object data = responseData.get("data");
                    if (data instanceof List<?> dataList) {
                        List<ConsumptionData> pageData = objectMapper.convertValue(
                                dataList, new TypeReference<List<ConsumptionData>>() {});
                        allData.addAll(pageData);
                        logger.info("Page {} fetched: {} records", pageCount, pageData.size());
                    }
                    hasMore = Boolean.TRUE.equals(responseData.get("has_more"));
                } else {
                    hasMore = false;
                }

                skip += pageSize;

            } catch (Exception e) {
                logger.error("Error fetching page {}: {}", pageCount, e.getMessage());
                hasMore = false;
            }
        }

        logger.info("Data fetch complete: {} total records from {} pages", allData.size(), pageCount);
        return allData;
    }

    /**
     * Fetch data from multiple API endpoints.
     * Replaces Python fetch_api_data().
     *
     * @param endpointMap Map of endpoint name to endpoint path
     * @return Map of endpoint name to ApiEndpointResult
     */
    public Map<String, ApiEndpointResult> fetchMultipleEndpoints(Map<String, String> endpointMap) {
        return fetchMultipleEndpoints(endpointMap, null);
    }

    /**
     * Fetch data from multiple API endpoints with optional per-endpoint parameters.
     * Replaces Python fetch_api_data().
     *
     * @param endpointMap       Map of endpoint name to endpoint path
     * @param paramsByEndpoint  Optional per-endpoint query parameters
     * @return Map of endpoint name to ApiEndpointResult
     */
    public Map<String, ApiEndpointResult> fetchMultipleEndpoints(
            Map<String, String> endpointMap,
            Map<String, Map<String, String>> paramsByEndpoint) {

        logger.info("Starting multi-endpoint data fetch for {} endpoints", endpointMap.size());
        Map<String, ApiEndpointResult> results = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : endpointMap.entrySet()) {
            String endpointName = entry.getKey();
            String endpointPath = entry.getValue();

            logger.info("Fetching endpoint: {} ({})", endpointName, endpointPath);

            String baseUrl;
            if (endpointPath.startsWith("/audit-logs")) {
                baseUrl = "https://api.devin.ai/v2";
            } else {
                baseUrl = apiProperties.getBaseUrl();
            }

            String fullUrl = baseUrl.replaceAll("/+$", "") + endpointPath;

            Map<String, String> epParams = null;
            if (paramsByEndpoint != null && paramsByEndpoint.containsKey(endpointName)) {
                epParams = paramsByEndpoint.get(endpointName);
                logger.info("    - Using params: {}", epParams);
            }

            try {
                final Map<String, String> finalParams = epParams;

                WebClient endpointClient = WebClient.builder()
                        .defaultHeader("Content-Type", "application/json")
                        .defaultHeader("Authorization", "Bearer " +
                                (System.getenv("DEVIN_ENTERPRISE_API_KEY") != null
                                        ? System.getenv("DEVIN_ENTERPRISE_API_KEY") : ""))
                        .build();

                Object responseBody = endpointClient.get()
                        .uri(fullUrl, uriBuilder -> {
                            if (finalParams != null) {
                                finalParams.forEach(uriBuilder::queryParam);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .bodyToMono(Object.class)
                        .retryWhen(Retry.backoff(apiProperties.getMaxRetries(),
                                Duration.ofMillis(apiProperties.getRetryBaseDelayMs())))
                        .block();

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                ApiEndpointResult result = new ApiEndpointResult(
                        endpointPath, fullUrl, 200, timestamp, responseBody);
                results.put(endpointName, result);

                logger.info("    - Status: 200");

            } catch (WebClientResponseException e) {
                logger.error("    - API error for {}: {} {}", endpointName, e.getStatusCode(), e.getMessage());
                ApiEndpointResult result = new ApiEndpointResult();
                result.setEndpointPath(endpointPath);
                result.setFullUrl(fullUrl);
                result.setStatusCode(e.getStatusCode().value());
                result.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                result.setError(e.getMessage());
                results.put(endpointName, result);

            } catch (Exception e) {
                logger.error("    - Error for {}: {}", endpointName, e.getMessage());
                ApiEndpointResult result = new ApiEndpointResult();
                result.setEndpointPath(endpointPath);
                result.setFullUrl(fullUrl);
                result.setStatusCode(0);
                result.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                result.setError(e.getMessage());
                results.put(endpointName, result);
            }
        }

        logger.info("Multi-endpoint fetch complete: {} endpoints processed", results.size());
        return results;
    }

    /**
     * Fetch organization mappings for multiple users.
     * Replaces Python fetch_user_organization_mappings().
     *
     * @param userIds List of user IDs
     * @return Map of userId to organization info
     */
    public Map<String, Map<String, Object>> fetchUserOrganizationMappings(List<String> userIds) {
        logger.info("Starting organization mapping fetch for {} users", userIds.size());

        Map<String, Map<String, Object>> mappings = new HashMap<>();

        for (String userId : userIds) {
            String endpointPath = "/members/" + userId + "/organizations";
            String fullUrl = apiProperties.getBaseUrl().replaceAll("/+$", "") + endpointPath;

            logger.info("Fetching organization mapping for user: {}", userId);

            try {
                Object responseBody = webClient.get()
                        .uri(endpointPath)
                        .retrieve()
                        .bodyToMono(Object.class)
                        .retryWhen(Retry.backoff(apiProperties.getMaxRetries(),
                                Duration.ofMillis(apiProperties.getRetryBaseDelayMs())))
                        .block();

                Map<String, Object> orgMapping = parseOrganizationResponse(responseBody);
                orgMapping.put("status", 200);
                mappings.put(userId, orgMapping);

                logger.info("  + User {}: {}", userId, orgMapping.get("organization_name"));

            } catch (WebClientResponseException e) {
                logger.warn("  - User {}: HTTP {}", userId, e.getStatusCode().value());
                Map<String, Object> errorMapping = new HashMap<>();
                errorMapping.put("organization_id", "Unmapped");
                errorMapping.put("organization_name", "Unmapped");
                errorMapping.put("status", e.getStatusCode().value());
                errorMapping.put("error", e.getMessage());
                mappings.put(userId, errorMapping);

            } catch (Exception e) {
                logger.error("  - User {}: Unexpected error - {}", userId, e.getMessage());
                Map<String, Object> errorMapping = new HashMap<>();
                errorMapping.put("organization_id", "Unmapped");
                errorMapping.put("organization_name", "Unmapped");
                errorMapping.put("status", "ERROR");
                errorMapping.put("error", e.getMessage());
                mappings.put(userId, errorMapping);
            }
        }

        long successful = mappings.values().stream()
                .filter(m -> Integer.valueOf(200).equals(m.get("status")))
                .count();
        logger.info("Organization mapping fetch complete: {}/{} successful", successful, userIds.size());

        return mappings;
    }

    /**
     * Fetch paginated sessions list.
     * Replaces Python fetch_sessions_list().
     *
     * @param createdDateFrom Optional start date filter
     * @param createdDateTo   Optional end date filter
     * @param maxPages        Maximum pages to fetch
     * @return List of session maps
     */
    public List<Map<String, Object>> fetchSessionsList(String createdDateFrom,
                                                        String createdDateTo,
                                                        int maxPages) {
        logger.info("Starting sessions list fetch");

        List<Map<String, Object>> allSessions = new ArrayList<>();
        int offset = 0;
        int pageSize = 50;
        int pageCount = 0;

        while (pageCount < maxPages) {
            pageCount++;
            final int currentOffset = offset;
            final String from = createdDateFrom;
            final String to = createdDateTo;

            logger.info("Fetching sessions page {} (offset={})", pageCount, offset);

            try {
                Object responseBody = webClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/sessions")
                                    .queryParam("offset", currentOffset)
                                    .queryParam("limit", pageSize);
                            if (from != null) {
                                uriBuilder.queryParam("created_date_from", from);
                            }
                            if (to != null) {
                                uriBuilder.queryParam("created_date_to", to);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .bodyToMono(Object.class)
                        .retryWhen(Retry.backoff(apiProperties.getMaxRetries(),
                                Duration.ofMillis(apiProperties.getRetryBaseDelayMs())))
                        .block();

                List<Map<String, Object>> sessions = extractSessionsFromResponse(responseBody);
                if (sessions.isEmpty()) {
                    break;
                }

                allSessions.addAll(sessions);
                logger.info("  + Page {}: {} sessions fetched", pageCount, sessions.size());

                if (sessions.size() < pageSize) {
                    break;
                }
                offset += pageSize;

            } catch (Exception e) {
                logger.error("Failed to fetch sessions page {}: {}", pageCount, e.getMessage());
                break;
            }
        }

        logger.info("Sessions list fetch complete: {} total sessions from {} pages",
                allSessions.size(), pageCount);
        return allSessions;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseOrganizationResponse(Object responseBody) {
        Map<String, Object> result = new HashMap<>();
        result.put("organization_id", "Unknown");
        result.put("organization_name", "Unknown");

        if (responseBody instanceof Map<?, ?> responseMap) {
            if (responseMap.containsKey("organizations")) {
                Object orgs = responseMap.get("organizations");
                if (orgs instanceof List<?> orgList && !orgList.isEmpty()) {
                    Map<String, Object> firstOrg = (Map<String, Object>) orgList.get(0);
                    result.put("organization_id", firstOrg.getOrDefault("id", "Unknown"));
                    result.put("organization_name", firstOrg.getOrDefault("name", "Unknown"));
                }
            } else if (responseMap.containsKey("id")) {
                result.put("organization_id", responseMap.get("id") != null ? responseMap.get("id") : "Unknown");
                result.put("organization_name", responseMap.get("name") != null ? responseMap.get("name") : "Unknown");
            }
        } else if (responseBody instanceof List<?> responseList && !responseList.isEmpty()) {
            Map<String, Object> firstOrg = (Map<String, Object>) responseList.get(0);
            result.put("organization_id", firstOrg.getOrDefault("id", "Unknown"));
            result.put("organization_name", firstOrg.getOrDefault("name", "Unknown"));
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSessionsFromResponse(Object responseBody) {
        if (responseBody instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        if (responseBody instanceof Map<?, ?> map) {
            for (String key : new String[]{"sessions", "data", "items"}) {
                if (map.containsKey(key)) {
                    Object value = map.get(key);
                    if (value instanceof List<?> list) {
                        return (List<Map<String, Object>>) list;
                    }
                }
            }
        }
        return List.of();
    }
}
