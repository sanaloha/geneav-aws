package com.geneav.scan.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * The connection webhook Microsoft calls for asynchronous subscription events
 * (Subscribe, ChangePlan, Suspend, Unsubscribe, …). Public — no session, no
 * API key — but every call must carry a valid Entra JWT, validated
 * <b>before</b> the body is parsed.
 *
 * <p>Timing matters here: {@code ChangePlan}/{@code ChangeQuantity} are
 * auto-accepted if we do not PATCH a status within 10 seconds, so validation,
 * application, and acknowledgement all happen synchronously inside this
 * request (the fulfillment client's timeouts are sized for it).
 *
 * <p>This path is exempted from both {@code ApiKeyAuthFilter} (the Entra JWT
 * would otherwise be rejected as an invalid API key) and {@code RateLimitFilter}
 * / Caddy throttling (Microsoft retries 500 times over eight hours from few
 * source IPs) — the JWT, not throttling, is the guard.
 */
@RestController
@RequestMapping(MarketplaceWebhookController.PATH)
@Hidden // internal contract with Microsoft, not part of the public API surface
public class MarketplaceWebhookController {

    public static final String PATH = "/api/v1/marketplace/webhook";

    private static final Logger log = LoggerFactory.getLogger(MarketplaceWebhookController.class);

    private final MarketplaceService marketplace;
    private final WebhookJwtValidator jwtValidator;
    private final FulfillmentClient fulfillment;
    private final ObjectMapper objectMapper;

    public MarketplaceWebhookController(MarketplaceService marketplace, WebhookJwtValidator jwtValidator,
                                        FulfillmentClient fulfillment, ObjectMapper objectMapper) {
        this.marketplace = marketplace;
        this.jwtValidator = jwtValidator;
        this.fulfillment = fulfillment;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public void receive(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                        @RequestBody String rawBody) {
        marketplace.requireConfigured();
        jwtValidator.validate(authorization); // 401 before the body is even parsed

        MarketplaceService.WebhookNotification payload;
        try {
            payload = objectMapper.readValue(rawBody, MarketplaceService.WebhookNotification.class)
                    .withRawJson(rawBody);
        } catch (JsonProcessingException e) {
            log.warn("Unparseable marketplace webhook body: {}", e.getOriginalMessage());
            throw new MarketplaceException(HttpStatus.BAD_REQUEST, "Malformed webhook payload.");
        }

        MarketplaceService.AckDecision ack = marketplace.applyWebhook(payload);

        // Owed PATCH: do it now, inside the request, or Microsoft auto-accepts
        // in 10 s regardless of whether we could actually apply the change.
        if (ack.required()) {
            try {
                fulfillment.patchOperation(ack.subscriptionId(), ack.operationId(), ack.status());
            } catch (RuntimeException e) {
                // The event is stored and applied; a missed ACK only means
                // Microsoft auto-accepts. Log, return 200, move on.
                log.error("Failed to PATCH operation {} as {}: {}", ack.operationId(), ack.status(),
                        e.getMessage());
            }
        }
        // Implicit 200: acknowledges receipt so Microsoft stops retrying.
    }
}
