package com.geneav.scan.marketplace;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the token cache against a real (embedded JDK) HTTP server so the
 * actual request path — form encoding, JSON parsing — is covered, not mocks.
 */
class MarketplaceTokenProviderTest {

    private final AtomicInteger tokenRequests = new AtomicInteger();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private MarketplaceProperties propsFor(long expiresInSeconds, int failFirstN) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            int n = tokenRequests.incrementAndGet();
            byte[] body;
            int status;
            if (n <= failFirstN) {
                status = 401;
                body = "{\"error\":\"invalid_client\"}".getBytes(StandardCharsets.UTF_8);
            } else {
                status = 200;
                body = ("{\"access_token\":\"tok-" + n + "\",\"expires_in\":" + expiresInSeconds + "}")
                        .getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        MarketplaceProperties props = new MarketplaceProperties();
        props.setEnabled(true);
        props.setTenantId("tenant-1");
        props.setClientId("client-1");
        props.setClientSecret("secret-1");
        props.setLoginBaseUrl("http://localhost:" + server.getAddress().getPort());
        return props;
    }

    @Test
    void cachesTheTokenAcrossCalls() throws Exception {
        MarketplaceTokenProvider provider =
                new MarketplaceTokenProvider(propsFor(3600, 0), RestClient.builder());

        assertThat(provider.token()).isEqualTo("tok-1");
        assertThat(provider.token()).isEqualTo("tok-1");
        assertThat(tokenRequests.get()).isEqualTo(1);
    }

    @Test
    void refreshesWhenTheTokenIsNearExpiry() throws Exception {
        // 60 s lifetime is inside the 5-minute refresh margin, so every call refreshes.
        MarketplaceTokenProvider provider =
                new MarketplaceTokenProvider(propsFor(60, 0), RestClient.builder());

        assertThat(provider.token()).isEqualTo("tok-1");
        assertThat(provider.token()).isEqualTo("tok-2");
    }

    @Test
    void rejectedCredentialsSurfaceAs503() throws Exception {
        MarketplaceTokenProvider provider =
                new MarketplaceTokenProvider(propsFor(3600, 99), RestClient.builder());

        assertThatThrownBy(provider::token)
                .isInstanceOf(MarketplaceException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
