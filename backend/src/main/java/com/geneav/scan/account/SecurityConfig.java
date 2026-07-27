package com.geneav.scan.account;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security for the dashboard's password login and cookie session.
 *
 * <p>Endpoint authorization is intentionally left as {@code permitAll}: the data
 * plane (scan/chat) and management endpoints do their own checks (API-key filter,
 * or the controller resolving the session/key via {@link CurrentAccount}). Spring
 * Security's job here is to (a) hold the authenticated session, (b) hash/verify
 * passwords, and (c) own CORS so credentialed cross-origin dashboard calls work.
 *
 * <p>CSRF is disabled in favour of a {@code SameSite=Lax} session cookie (set in
 * application.yml): the cookie is not sent on cross-site state-changing requests,
 * which is what CSRF tokens would otherwise guard against for this JSON API.
 * "Sign in with Microsoft" (Entra ID) is wired only when a client registration
 * exists — see {@link MicrosoftOidcConfig}; an unconfigured server has no
 * OAuth2 routes at all.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String allowedOrigins;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final ObjectProvider<MicrosoftOidcHandler> microsoftHandler;

    public SecurityConfig(@Value("${geneav.cors.allowed-origins:http://localhost:3000}") String allowedOrigins,
                          ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                          ObjectProvider<MicrosoftOidcHandler> microsoftHandler) {
        this.allowedOrigins = allowedOrigins;
        this.clientRegistrations = clientRegistrations;
        this.microsoftHandler = microsoftHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                // Never redirect to a login page; unauthenticated access yields a clean 401.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpStatus.UNAUTHORIZED.value())));

        // Microsoft sign-in, only when configured. The custom handlers replace
        // Spring's OAuth2 security context with the dashboard's own session
        // shape, so downstream code sees no difference from a password login.
        MicrosoftOidcHandler handler = microsoftHandler.getIfAvailable();
        if (clientRegistrations.getIfAvailable() != null && handler != null) {
            http.oauth2Login(oauth -> oauth
                    .successHandler(handler)
                    .failureHandler(handler));
        }
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true); // session cookie on cross-origin dashboard calls
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
