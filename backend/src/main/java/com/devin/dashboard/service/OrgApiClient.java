package com.devin.dashboard.service;

import com.devin.dashboard.model.EndpointDefinition;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

/**
 * Reactive HTTP client for organization-scoped Devin API endpoints.
 * Uses Spring WebFlux {@link WebClient} with Bearer token authentication
 * from an organization service user and exponential-backoff retry logic (max 3 attempts).
 *
 * <p>Organization endpoints use the base URL {@code https://api.devin.ai/v3/organizations/{org_id}/...}
 * and require a separate token from enterprise endpoints.</p>
 */
@Slf4j
@Service
public class OrgApiClient {

    private final WebClient webClient;

    @Getter
    private final String orgId;

    /**
     * Constructs the organization API client, injecting the org service user token
     * and org ID from environment variables.
     *
     * @param orgServiceToken the organization service user token (DEVIN_ORG_SERVICE_TOKEN)
     * @param orgId           the organization ID (DEVIN_ORG_ID)
     * @throws IllegalStateException if either value is not configured
     */
    public OrgApiClient(
            @Value("${DEVIN_ORG_SERVICE_TOKEN:}") String orgServiceToken,
            @Value("${DEVIN_ORG_ID:}") String orgId) {

        if (orgServiceToken == null || orgServiceToken.isBlank()) {
            throw new IllegalStateException(
                    "DEVIN_ORG_SERVICE_TOKEN is not configured. "
                    + "Provision a Devin organization service user and set its token as DEVIN_ORG_SERVICE_TOKEN.");
        }
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalStateException(
                    "DEVIN_ORG_ID is not configured. "
                    + "Set the organization ID as DEVIN_ORG_ID.");
        }
        if (orgServiceToken.length() < 20) {
            log.warn("DEVIN_ORG_SERVICE_TOKEN appears too short ({} chars). "
                    + "Verify that the correct organization service user token is configured.",
                    orgServiceToken.length());
        }

        this.orgId = orgId;
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + orgServiceToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    /**
     * Executes a GET request against the given endpoint definition,
     * returning the response body as a reactive {@link Flux} of String chunks.
     * Path parameters are substituted into the URL template.
     *
     * <p>Retries up to 3 times with exponential backoff (1s, 2s, 4s) on
     * transient HTTP errors (5xx and 429).</p>
     *
     * @param endpoint   the endpoint definition from endpoints.yaml
     * @param pathParams key-value map for URL path variable substitution
     * @return a Flux emitting the response body (streamed)
     */
    public Flux<String> get(EndpointDefinition endpoint, Map<String, String> pathParams) {
        String url = endpoint.buildUrl(pathParams);
        log.debug("GET {} [endpoint={}]", url, endpoint.getName());

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorResume(WebClientResponseException.Unauthorized.class, ex -> {
                    log.error("Organization service user token is invalid or expired for endpoint {} (HTTP 401)",
                            endpoint.getName());
                    return Flux.error(ex);
                })
                .onErrorResume(WebClientResponseException.Forbidden.class, ex -> {
                    log.error("Organization service user token lacks required permissions for endpoint {} (HTTP 403). "
                            + "Verify the service user has the necessary organization-level permissions.",
                            endpoint.getName());
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
        log.debug("{} {} [endpoint={}]", httpMethod, url, endpoint.getName());

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
                    log.error("Organization service user token is invalid or expired for endpoint {} (HTTP 401)",
                            endpoint.getName());
                    return Mono.error(ex);
                })
                .onErrorResume(WebClientResponseException.Forbidden.class, ex -> {
                    log.error("Organization service user token lacks required permissions for endpoint {} (HTTP 403). "
                            + "Verify the service user has the necessary organization-level permissions.",
                            endpoint.getName());
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
     * <p>401 Unauthorized and 403 Forbidden errors are NOT retried.</p>
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
