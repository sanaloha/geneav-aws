package com.geneav.scan.account;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * "Sign in with Microsoft" wiring, active only when
 * {@code geneav.auth.microsoft.enabled=true}. When absent, no
 * {@code ClientRegistrationRepository} bean exists and {@link SecurityConfig}
 * leaves OAuth2 login entirely unconfigured.
 *
 * <p><b>The multi-tenant issuer wrinkle.</b> A multi-tenant Entra app cannot
 * use a plain {@code issuer-uri}: tokens carry the buyer's real tenant in
 * {@code iss} ({@code https://login.microsoftonline.com/{tid}/v2.0}), which
 * never equals the {@code /common} metadata issuer, so standard issuer
 * validation rejects every login. The registration therefore names the
 * {@code /common} endpoints explicitly (no issuer metadata), and a custom ID
 * token validator accepts exactly the issuer derived from the token's own
 * {@code tid} claim — no other issuer shape passes.
 */
@Configuration
@EnableConfigurationProperties(MicrosoftAuthProperties.class)
public class MicrosoftOidcConfig {

    private static final String LOGIN_BASE = "https://login.microsoftonline.com";

    @Bean
    @ConditionalOnProperty(prefix = "geneav.auth.microsoft", name = "enabled", havingValue = "true")
    public ClientRegistrationRepository clientRegistrationRepository(MicrosoftAuthProperties props) {
        ClientRegistration microsoft = ClientRegistration.withRegistrationId("microsoft")
                .clientId(props.getClientId())
                .clientSecret(props.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(LOGIN_BASE + "/common/oauth2/v2.0/authorize")
                .tokenUri(LOGIN_BASE + "/common/oauth2/v2.0/token")
                .jwkSetUri(LOGIN_BASE + "/common/discovery/v2.0/keys")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Microsoft")
                .build();
        return new InMemoryClientRegistrationRepository(microsoft);
    }

    @Bean
    @ConditionalOnProperty(prefix = "geneav.auth.microsoft", name = "enabled", havingValue = "true")
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        // Default claim checks (aud contains our client id, exp/iat, azp when
        // multiple audiences) come from OidcIdTokenValidator via the factory's
        // default; here we swap in a validator set that adds the per-tenant
        // issuer rule instead of metadata-issuer equality.
        factory.setJwtValidatorFactory(registration -> new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator(registration),
                perTenantIssuerValidator()));
        return factory;
    }

    /**
     * Accepts {@code iss} only when it is exactly
     * {@code https://login.microsoftonline.com/<tid>/v2.0} for the token's own
     * {@code tid} claim.
     */
    private static OAuth2TokenValidator<Jwt> perTenantIssuerValidator() {
        return jwt -> {
            Object tid = jwt.getClaims().get("tid");
            String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            if (tid != null && issuer != null
                    && issuer.equals(LOGIN_BASE + "/" + tid + "/v2.0")) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_issuer", "The ID token issuer does not match its tenant claim.", null));
        };
    }
}
