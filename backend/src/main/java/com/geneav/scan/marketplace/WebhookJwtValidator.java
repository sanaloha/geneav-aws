package com.geneav.scan.marketplace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Validates the Microsoft Entra bearer token on incoming Marketplace webhook
 * calls. There is no shared secret on this webhook — this token is the only
 * authentication, so a request that fails any check is rejected with 401
 * <b>before</b> the body is even parsed.
 *
 * <p>Checks, per Microsoft's webhook-security guidance:
 * <ul>
 *   <li>signature against Entra's JWKS for our tenant (standard decode),</li>
 *   <li>{@code aud} equals the fulfillment app's client id,</li>
 *   <li>{@code tid} equals our tenant id,</li>
 *   <li>{@code appid} <b>or</b> {@code azp} equals Microsoft's marketplace
 *       resource id — which of the two claims carries it depends on the app
 *       setup, so either is accepted.</li>
 * </ul>
 */
@Component
public class WebhookJwtValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookJwtValidator.class);

    private final MarketplaceProperties props;
    private final Supplier<JwtDecoder> decoderSupplier;
    private volatile JwtDecoder decoder;

    // Explicit: a second, package-private constructor exists as a test seam, and
    // without this Spring cannot tell which of the two to inject.
    @Autowired
    public WebhookJwtValidator(MarketplaceProperties props) {
        // Lazy: the JWKS URL depends on config that may be absent when the
        // marketplace is disabled, and building the decoder fetches nothing
        // until the first decode anyway.
        this(props, () -> NimbusJwtDecoder.withJwkSetUri(
                props.getLoginBaseUrl() + "/" + props.getTenantId() + "/discovery/v2.0/keys").build());
    }

    /** Test seam: inject a decoder built from a local key. */
    WebhookJwtValidator(MarketplaceProperties props, Supplier<JwtDecoder> decoderSupplier) {
        this.props = props;
        this.decoderSupplier = decoderSupplier;
    }

    /**
     * Validates {@code authorizationHeader} and returns the decoded token.
     *
     * @throws MarketplaceException 401 on any failure.
     */
    public Jwt validate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw unauthorized("missing bearer token");
        }
        String token = authorizationHeader.substring(7).trim();

        Jwt jwt;
        try {
            jwt = decoder().decode(token);
        } catch (JwtException e) {
            throw unauthorized("token failed decoding: " + e.getMessage());
        }

        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(props.getClientId())) {
            throw unauthorized("audience mismatch");
        }
        if (!props.getTenantId().equalsIgnoreCase(claim(jwt, "tid"))) {
            throw unauthorized("tenant mismatch");
        }
        String appId = claim(jwt, "appid");
        String azp = claim(jwt, "azp");
        if (!props.getResourceId().equalsIgnoreCase(appId) && !props.getResourceId().equalsIgnoreCase(azp)) {
            throw unauthorized("caller app mismatch");
        }
        return jwt;
    }

    private String claim(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value == null ? null : value.toString();
    }

    private MarketplaceException unauthorized(String reason) {
        // The reason goes to the log only; the response stays generic.
        log.warn("Rejected marketplace webhook call: {}", reason);
        return new MarketplaceException(HttpStatus.UNAUTHORIZED, "Invalid webhook credentials.");
    }

    private JwtDecoder decoder() {
        JwtDecoder d = decoder;
        if (d == null) {
            synchronized (this) {
                if (decoder == null) {
                    decoder = decoderSupplier.get();
                }
                d = decoder;
            }
        }
        return d;
    }
}
