package com.devin.common.service;

import com.devin.common.model.EndpointDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Abstract base class for Devin API clients.
 * Provides common HTTP logic: Bearer token authentication, retry with exponential backoff,
 * and standardized error handling for 401/403/429/5xx responses.
 */
@Slf4j
public abstract class BaseApiClient {

    private final WebClient webClient;

    protected BaseApiClient(String token) {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    protected abstract String getScopeLabel();

    /**
     * GET with path params only.
     */
    public Flux<String> get(EndpointDefinition endpoint,
                            Map<String, String> pathParams) {
        return get(endpoint, pathParams, Collections.emptyMap());
    }

    /**
     * GET with path params and query params.
     */
    public Flux<String> get(EndpointDefinition endpoint,
                            Map<String, String> pathParams,
                            Map<String, String> queryParams) {
        String baseUrl = endpoint.buildUrl(pathParams);
        if (queryParams != null && !queryParams.isEmpty()) {
            String qs = queryParams.entrySet().stream()
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                            + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            baseUrl += (baseUrl.contains("?") ? "&" : "?") + qs;
        }
        final String url = baseUrl;
        log.debug("GET {} [endpoint={}, scope={}]",
                url, endpoint.getName(), getScopeLabel());

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorResume(
                        WebClientResponseException.Unauthorized.class, ex -> {
                    log.error("{} service user token invalid/expired for {} (HTTP 401). Re-provision the service user.",
                            getScopeLabel(), endpoint.getName());
                    return Flux.error(ex);
                })
                .onErrorResume(
                        WebClientResponseException.Forbidden.class, ex -> {
                    log.error("{} service user token lacks permissions for {} (HTTP 403). Check service user permissions.",
                            getScopeLabel(), endpoint.getName());
                    return Flux.error(ex);
                })
                .retryWhen(retrySpec(endpoint.getName()))
                .doOnError(e -> {
                    if (!(e instanceof WebClientResponseException.NotFound)) {
                        log.error("Error calling endpoint {}: {}",
                                endpoint.getName(), e.getMessage());
                    }
                });
    }

    /**
     * Execute an arbitrary HTTP method against an endpoint.
     */
    public Mono<String> execute(EndpointDefinition endpoint,
                                Map<String, String> pathParams,
                                Object body) {
        String url = endpoint.buildUrl(pathParams);
        HttpMethod httpMethod =
                HttpMethod.valueOf(endpoint.getMethod().toUpperCase());
        log.debug("{} {} [endpoint={}, scope={}]",
                httpMethod, url, endpoint.getName(), getScopeLabel());

        WebClient.RequestBodySpec requestSpec =
                webClient.method(httpMethod).uri(url);

        WebClient.RequestHeadersSpec<?> headersSpec;
        if (body != null && needsBody(httpMethod)) {
            headersSpec = requestSpec.bodyValue(body);
        } else {
            headersSpec = requestSpec;
        }

        return headersSpec
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(
                        WebClientResponseException.Unauthorized.class, ex -> {
                    log.error("{} service user token invalid/expired for {} (HTTP 401). Re-provision the service user.",
                            getScopeLabel(), endpoint.getName());
                    return Mono.error(ex);
                })
                .onErrorResume(
                        WebClientResponseException.Forbidden.class, ex -> {
                    log.error("{} service user token lacks permissions for {} (HTTP 403). Check service user permissions.",
                            getScopeLabel(), endpoint.getName());
                    return Mono.error(ex);
                })
                .retryWhen(retrySpec(endpoint.getName()))
                .doOnError(e -> {
                    if (!(e instanceof WebClientResponseException.NotFound)) {
                        log.error("Error calling endpoint {}: {}",
                                endpoint.getName(), e.getMessage());
                    }
                });
    }

    private Retry retrySpec(String endpointName) {
        return Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(8))
                .filter(this::isRetryable)
                .doBeforeRetry(signal ->
                        log.warn("Retrying endpoint {} (attempt {}): {}",
                                endpointName,
                                signal.totalRetries() + 1,
                                signal.failure().getMessage()));
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            return status >= 500 || status == 429;
        }
        return false;
    }

    private boolean needsBody(HttpMethod method) {
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH;
    }
}
