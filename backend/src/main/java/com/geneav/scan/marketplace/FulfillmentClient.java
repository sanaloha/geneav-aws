package com.geneav.scan.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The only class that speaks HTTP to the SaaS Fulfillment APIs
 * ({@code https://marketplaceapi.microsoft.com/api/saas}, api-version
 * 2018-08-31). Returns geneav records, never raw JSON.
 *
 * <p>Timeouts are deliberately tight (2 s connect / 5 s read): the webhook path
 * must validate an operation and PATCH its status inside Microsoft's 10-second
 * acknowledgement window, so a slow Microsoft must fail fast rather than hang.
 *
 * <p>Every call sends {@code x-ms-requestid} / {@code x-ms-correlationid}
 * GUIDs, and failures log the pair — it is what Microsoft support asks for
 * first.
 */
@Component
public class FulfillmentClient {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentClient.class);

    private final MarketplaceProperties props;
    private final MarketplaceTokenProvider tokens;
    private final RestClient client;

    public FulfillmentClient(MarketplaceProperties props, MarketplaceTokenProvider tokens,
                             RestClient.Builder builder) {
        this.props = props;
        this.tokens = tokens;
        // JDK HttpClient, not HttpURLConnection: the operations ACK is a PATCH,
        // which HttpURLConnection (SimpleClientHttpRequestFactory) cannot send.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = builder.requestFactory(factory).build();
    }

    /**
     * Exchanges the purchase identification token from the landing page URL for
     * the subscription's identity and details. The token is valid 24 hours and
     * arrives URL-encoded; the caller passes it already decoded (the query
     * parser has done that).
     */
    public ResolvedPurchase resolve(String marketplaceToken) {
        return call("resolve", () -> client.post()
                .uri(url("/subscriptions/resolve"))
                .headers(h -> {
                    standardHeaders(h::add);
                    h.add("x-ms-marketplace-token", marketplaceToken);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ResolvedPurchase.class));
    }

    /** Tells Microsoft to start billing. The customer is not billed until this succeeds. */
    public void activate(UUID subscriptionId, String planId) {
        call("activate", () -> {
            client.post()
                    .uri(url("/subscriptions/" + subscriptionId + "/activate"))
                    .headers(h -> standardHeaders(h::add))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("planId", planId))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public SubscriptionDetails getSubscription(UUID subscriptionId) {
        return call("getSubscription", () -> client.get()
                .uri(url("/subscriptions/" + subscriptionId))
                .headers(h -> standardHeaders(h::add))
                .retrieve()
                .body(SubscriptionDetails.class));
    }

    /** Validates a webhook-notified operation really exists on the Microsoft side. */
    public OperationDetails getOperation(UUID subscriptionId, UUID operationId) {
        return call("getOperation", () -> client.get()
                .uri(url("/subscriptions/" + subscriptionId + "/operations/" + operationId))
                .headers(h -> standardHeaders(h::add))
                .retrieve()
                .body(OperationDetails.class));
    }

    /** Acknowledges an operation: {@code status} is {@code Success} or {@code Failure}. */
    public void patchOperation(UUID subscriptionId, UUID operationId, String status) {
        call("patchOperation", () -> {
            client.patch()
                    .uri(url("/subscriptions/" + subscriptionId + "/operations/" + operationId))
                    .headers(h -> standardHeaders(h::add))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", status))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private String url(String path) {
        return props.getApiBaseUrl() + path + "?api-version=" + props.getApiVersion();
    }

    private void standardHeaders(java.util.function.BiConsumer<String, String> add) {
        add.accept("authorization", "Bearer " + tokens.token());
        add.accept("x-ms-requestid", UUID.randomUUID().toString());
        add.accept("x-ms-correlationid", UUID.randomUUID().toString());
    }

    private <T> T call(String operation, java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            // 400 on resolve = bad/expired marketplace token; surface that as the
            // caller's error, everything else as a gateway problem.
            log.error("Marketplace {} failed with {}: request-id headers {} / {}", operation,
                    e.getStatusCode(),
                    e.getResponseHeaders() == null ? "-" : e.getResponseHeaders().getFirst("x-ms-requestid"),
                    e.getResponseHeaders() == null ? "-" : e.getResponseHeaders().getFirst("x-ms-correlationid"));
            if ("resolve".equals(operation) && e.getStatusCode().value() == 400) {
                throw new MarketplaceException(HttpStatus.BAD_REQUEST,
                        "We couldn't identify this purchase. Reopen this SaaS subscription in the Azure portal "
                                + "or Microsoft 365 Admin Center and select Configure Account to try again.");
            }
            throw new MarketplaceException(HttpStatus.BAD_GATEWAY,
                    "The Microsoft Marketplace rejected the " + operation + " request. Please try again.");
        } catch (ResourceAccessException e) {
            log.error("Marketplace {} unreachable: {}", operation, e.getMessage());
            throw new MarketplaceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Microsoft Marketplace is temporarily unreachable. Please try again.");
        }
    }

    // --- Wire types. NEVER deserialize strictly: Microsoft reserves the right
    // --- to extend these payloads, so unknown fields must be ignored.

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResolvedPurchase(UUID id, String subscriptionName, String offerId, String planId,
                                   Integer quantity, SubscriptionDetails subscription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionDetails(UUID id, String name, String publisherId, String offerId,
                                      String planId, Integer quantity, Party beneficiary, Party purchaser,
                                      Term term, Boolean autoRenew, Boolean isFreeTrial, Boolean isTest,
                                      String saasSubscriptionStatus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Party(String emailId, UUID objectId, UUID tenantId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Term(String termUnit, Instant startDate, Instant endDate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OperationDetails(UUID id, UUID subscriptionId, String offerId, String planId,
                                   Integer quantity, String action, String status) {
    }
}
