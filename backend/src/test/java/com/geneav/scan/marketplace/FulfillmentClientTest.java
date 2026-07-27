package com.geneav.scan.marketplace;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the client against an embedded HTTP server. This deliberately uses the
 * real request factory: the operations ACK is a PATCH, which the default
 * HttpURLConnection factory cannot send, and only a real request proves the
 * JDK-client factory handles it.
 */
class FulfillmentClientTest {

    private static final UUID SUB_ID = UUID.fromString("7f1a2b3c-0000-1111-2222-333344445555");
    private static final UUID OP_ID = UUID.fromString("8a1b2c3d-0000-1111-2222-333344445555");

    private final Map<String, String> received = new ConcurrentHashMap<>();
    private HttpServer server;
    private FulfillmentClient client;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            received.put(key, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String marketplaceToken = exchange.getRequestHeaders().getFirst("x-ms-marketplace-token");

            byte[] body;
            int status;
            if (key.equals("POST /subscriptions/resolve") && "expired".equals(marketplaceToken)) {
                status = 400;
                body = "{}".getBytes(StandardCharsets.UTF_8);
            } else if (key.equals("POST /subscriptions/resolve")) {
                status = 200;
                body = ("""
                        {
                          "id": "%s",
                          "subscriptionName": "geneav pro",
                          "offerId": "geneav-scan-api",
                          "planId": "geneav-pro",
                          "unknownFutureField": {"nested": true},
                          "subscription": {
                            "id": "%s",
                            "saasSubscriptionStatus": "PendingFulfillmentStart",
                            "isFreeTrial": true,
                            "term": {"termUnit": "P1M"},
                            "beneficiary": {"emailId": "buyer@corp.com"}
                          }
                        }
                        """.formatted(SUB_ID, SUB_ID)).getBytes(StandardCharsets.UTF_8);
            } else {
                status = 200;
                body = "{}".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        MarketplaceProperties props = new MarketplaceProperties();
        props.setApiBaseUrl("http://localhost:" + server.getAddress().getPort());
        MarketplaceTokenProvider tokens = mock(MarketplaceTokenProvider.class);
        when(tokens.token()).thenReturn("publisher-token");
        client = new FulfillmentClient(props, tokens, RestClient.builder());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void resolveParsesTheSubscriptionAndIgnoresUnknownFields() {
        FulfillmentClient.ResolvedPurchase resolved = client.resolve("good-token");

        assertThat(resolved.planId()).isEqualTo("geneav-pro");
        assertThat(resolved.subscription().id()).isEqualTo(SUB_ID);
        assertThat(resolved.subscription().isFreeTrial()).isTrue();
        assertThat(resolved.subscription().beneficiary().emailId()).isEqualTo("buyer@corp.com");
    }

    @Test
    void expiredResolveTokenBecomesTheDocumentedClientError() {
        assertThatThrownBy(() -> client.resolve("expired"))
                .isInstanceOf(MarketplaceException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void patchOperationActuallySendsAPatch() {
        client.patchOperation(SUB_ID, OP_ID, "Success");

        String body = received.get("PATCH /subscriptions/" + SUB_ID + "/operations/" + OP_ID);
        assertThat(body).contains("\"status\":\"Success\"");
    }

    @Test
    void activateSendsThePlanId() {
        client.activate(SUB_ID, "geneav-pro");

        assertThat(received.get("POST /subscriptions/" + SUB_ID + "/activate"))
                .contains("\"planId\":\"geneav-pro\"");
    }

    @Test
    void everyCallCarriesAuthAndTracingHeaders() throws Exception {
        // Re-register the handler to capture headers for one call.
        List<String> headers = new java.util.concurrent.CopyOnWriteArrayList<>();
        server.removeContext("/");
        server.createContext("/", exchange -> {
            headers.add(exchange.getRequestHeaders().getFirst("Authorization"));
            headers.add(exchange.getRequestHeaders().getFirst("x-ms-requestid"));
            headers.add(exchange.getRequestHeaders().getFirst("x-ms-correlationid"));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        client.getSubscription(SUB_ID);

        assertThat(headers.get(0)).isEqualTo("Bearer publisher-token");
        assertThat(headers.get(1)).isNotBlank();
        assertThat(headers.get(2)).isNotBlank();
    }
}
