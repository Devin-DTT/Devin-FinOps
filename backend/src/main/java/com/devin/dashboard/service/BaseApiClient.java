package com.devin.dashboard.service;

import com.devin.dashboard.model.EndpointDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Abstract base class for Devin API clients.
 * Provides common HTTP logic: Bearer token authentication, retry with exponential backoff,
 * and standardized error handling for 401/403/429/5xx responses.
 *
 * <p>Subclasses only need to supply the token and a human-readable scope label
 * (e.g. "enterprise", "organization") via {@link #getToken()} and {@link #getScopeLabel()}.</p>
 */
@Slf4j
public abstract class BaseApiClient {

    private final WebClient webClient;

    /**
     * Constructs the base client with the given Bearer token.
     *
     * @param token the Bearer token for API authentication
     */
    protected BaseApiClient(String token) {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    /**
     * Returns a human-readable label for log messages (e.g. "enterprise", "organization").
     */
    protected abstract String getScopeLabel();

    /**
     * Executes a GET request against the given endpoint definition,
     * returning the response body as a reactive {@link Flux} of String chunks.
     *
     * <p>Retries up to 3 times with exponential backoff (1s, 2s, 4s) on
     * transient HTTP errors (5xx and 429).</p>
     *
     * @param endpoint   the endpoint definition from endpoints.yaml
     * @param pathParams key-value map for URL path variable substitution
     * @return a Flux emitting the response body (streamed)
     */
    public Flux<String> get(EndpointDefinition endpoint, Map<String, String> pathParams) {
        return get(endpoint, pathParams, Collections.emptyMap());
    }

    /**
     * Executes a GET request with both path parameters and query parameters.
     *
     * @param endpoint    the endpoint definition from endpoints.yaml
     * @param pathParams  key-value map for URL path variable substitution
     * @param queryParams key-value map for URL query parameter appending
     * @return a Flux emitting the response body (streamed)
     */
    public Flux<String> get(EndpointDefinition endpoint, Map<String, String> pathParams,
                            Map<String, String> queryParams) {
        String url = endpoint.buildUrl(pathParams);
        if (queryParams != null && !queryParams.isEmpty()) {
            String qs = queryParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
            url += (url.contains("?") ? "&" : "?") + qs;
        }
        log.debug("GET {} [endpoint={}, scope={}]", url, endpoint.getName(), getScopeLabel());

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorResume(WebClientResponseException.Unauthorized.class, ex -> {
                    log.error("{} service user token is invalid or expired for endpoint {} (HTTP 401)",
                            getScopeLabel(), endpoint.getName());
                    return Flux.error(ex);
                })
                .onErrorResume(WebClientResponseException.Forbidden.class, ex -> {
                    log.error("{} service user token lacks required permissions for endpoint {} (HTTP 403)",
                            getScopeLabel(), endpoint.getName());
                    return Flux.error(ex);
                })
                .retryWhen(retrySpec(endpoint.getName()))
                .doOnError(e -> log.error("Error calling endpoint {}: {}", endpoint.getName(), e.getMessage()));
    }

    /**
     * Executes an HTTP request for the specified method against the given endpoint,
     * returning the response body as a reactive {@link Mono} of String.
     *
     * @param endpoint   the endpoint definition from endpoints.yaml
     * @param pathParams key-value map for URL path variable substitution
     * @param body       optional request body (for POST/PUT/PATCH)
     * @return a Mono emitting the full response body
     */
    public Mono<String> execute(EndpointDefinition endpoint, Map<String, String> pathParams, Object body) {
        String url = endpoint.buildUrl(pathParams);
        HttpMethod httpMethod = HttpMethod.valueOf(endpoint.getMethod().toUpperCase());
        log.debug("{} {} [endpoint={}, scope={}]", httpMethod, url, endpoint.getName(), getScopeLabel());

        WebClient.RequestBodySpec requestSpec = webClient.method(httpMethod)
                .uri(url);

        WebClient.RequestHeadersSpec<?> headersSpec;
        if (body != null && needsBody(httpMethod)) {
            headersSpec = requestSpec.bodyValue(body);
        } else {
            headersSpec = requestSpec;
        }

        return headersSpec
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(WebClientResponseException.Unauthorized.class, ex -> {
                    log.error("{} service user token is invalid or expired for endpoint {} (HTTP 401)",
                            getScopeLabel(), endpoint.getName());
                    return Mono.error(ex);
                })
                .onErrorResume(WebClientResponseException.Forbidden.class, ex -> {
                    log.error("{} service user token lacks required permissions for endpoint {} (HTTP 403)",
                            getScopeLabel(), endpoint.getName());
                    return Mono.error(ex);
                })
                .retryWhen(retrySpec(endpoint.getName()))
                .doOnError(e -> log.error("Error calling endpoint {}: {}", endpoint.getName(), e.getMessage()));
    }

    /**
     * Builds a retry specification with exponential backoff:
     * max 3 attempts, starting at 1 second, doubling each time.
     * Only retries on server errors (5xx) and rate limiting (429).
     */
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

    /**
     * Determines whether the given throwable warrants a retry.
     * Returns true for 5xx server errors and 429 Too Many Requests.
     */
    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            return status >= 500 || status == 429;
        }
        return false;
    }

    /**
     * Returns true if the HTTP method typically carries a request body.
     */
    private boolean needsBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }
}
