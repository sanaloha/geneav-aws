package com.geneav.scan.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;

/**
 * Publisher authorization token for the SaaS Fulfillment APIs: an Entra
 * client-credentials token scoped to Microsoft's fixed marketplace resource id.
 *
 * <p>Tokens live one hour; this caches one and refreshes it behind a lock a few
 * minutes before expiry, so concurrent fulfillment calls never stampede the
 * token endpoint. The token itself is never logged.
 */
@Component
@EnableConfigurationProperties(MarketplaceProperties.class)
public class MarketplaceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceTokenProvider.class);

    /** Refresh this long before the token actually expires. */
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    private final MarketplaceProperties props;
    private final RestClient client;
    private final Object refreshLock = new Object();

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public MarketplaceTokenProvider(MarketplaceProperties props, RestClient.Builder builder) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.client = builder.requestFactory(factory).build();
    }

    /**
     * A currently-valid bearer token.
     *
     * @throws MarketplaceException 503 if the token endpoint cannot be reached
     *                              or rejects the credentials.
     */
    public String token() {
        String token = cachedToken;
        if (token != null && Instant.now().isBefore(expiresAt.minus(REFRESH_MARGIN))) {
            return token;
        }
        synchronized (refreshLock) {
            // Another caller may have refreshed while we waited on the lock.
            token = cachedToken;
            if (token != null && Instant.now().isBefore(expiresAt.minus(REFRESH_MARGIN))) {
                return token;
            }
            return refresh();
        }
    }

    private String refresh() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("scope", props.getResourceId() + "/.default");

        try {
            TokenResponse response = client.post()
                    .uri(props.getLoginBaseUrl() + "/" + props.getTenantId() + "/oauth2/v2.0/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new MarketplaceException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Marketplace token endpoint returned no token.");
            }
            cachedToken = response.accessToken();
            expiresAt = Instant.now().plusSeconds(Math.max(60, response.expiresIn()));
            log.debug("Refreshed marketplace publisher token; expires at {}", expiresAt);
            return cachedToken;
        } catch (RestClientException e) {
            // Never include the request (it carries the client secret) in the message.
            log.error("Marketplace token request failed: {}", e.getMessage());
            throw new MarketplaceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not authenticate with the Microsoft Marketplace. Please try again.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {
    }
}
