package com.geneav.scan.marketplace;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates the webhook's Entra JWT checks against locally minted tokens: the
 * decoder is injected via the validator's test seam, built from a local RSA key
 * instead of Entra's JWKS.
 */
class WebhookJwtValidatorTest {

    private static final String CLIENT_ID = "fulfillment-app-client-id";
    private static final String TENANT_ID = "11111111-2222-3333-4444-555555555555";
    private static final String MARKETPLACE_RESOURCE = "20e940b3-4c77-4b0b-9a53-9e16a1b010a7";

    private final KeyPair keys = rsaKeys();
    private final WebhookJwtValidator validator = new WebhookJwtValidator(props(),
            () -> NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());

    private static KeyPair rsaKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static MarketplaceProperties props() {
        MarketplaceProperties p = new MarketplaceProperties();
        p.setEnabled(true);
        p.setTenantId(TENANT_ID);
        p.setClientId(CLIENT_ID);
        p.setClientSecret("secret");
        return p;
    }

    private String token(Map<String, Object> claims) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                    .issueTime(new Date());
            claims.forEach((name, value) -> {
                if ("aud".equals(name)) {
                    builder.audience((String) value);
                } else {
                    builder.claim(name, value);
                }
            });
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), builder.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertRejected(String authorizationHeader) {
        assertThatThrownBy(() -> validator.validate(authorizationHeader))
                .isInstanceOf(MarketplaceException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenWithAppidPasses() {
        String token = token(Map.of("aud", CLIENT_ID, "tid", TENANT_ID, "appid", MARKETPLACE_RESOURCE));
        assertThat(validator.validate("Bearer " + token).getAudience()).contains(CLIENT_ID);
    }

    @Test
    void validTokenWithAzpPasses() {
        // Depending on app setup Microsoft sends the caller id as azp, not appid.
        String token = token(Map.of("aud", CLIENT_ID, "tid", TENANT_ID, "azp", MARKETPLACE_RESOURCE));
        assertThat(validator.validate("Bearer " + token)).isNotNull();
    }

    @Test
    void missingHeaderIsRejected() {
        assertRejected(null);
        assertRejected("Basic abc");
    }

    @Test
    void garbageTokenIsRejected() {
        assertRejected("Bearer not-a-jwt");
    }

    @Test
    void wrongAudienceIsRejected() {
        assertRejected("Bearer " + token(Map.of(
                "aud", "someone-else", "tid", TENANT_ID, "appid", MARKETPLACE_RESOURCE)));
    }

    @Test
    void wrongTenantIsRejected() {
        assertRejected("Bearer " + token(Map.of(
                "aud", CLIENT_ID, "tid", "99999999-8888-7777-6666-555555555555",
                "appid", MARKETPLACE_RESOURCE)));
    }

    @Test
    void wrongCallerAppIsRejected() {
        assertRejected("Bearer " + token(Map.of(
                "aud", CLIENT_ID, "tid", TENANT_ID, "appid", "not-the-marketplace")));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(CLIENT_ID)
                .claim("tid", TENANT_ID)
                .claim("appid", MARKETPLACE_RESOURCE)
                .expirationTime(new Date(System.currentTimeMillis() - 600_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
        assertRejected("Bearer " + jwt.serialize());
    }
}
