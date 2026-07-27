package com.geneav.scan.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geneav.scan.web.JsonErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Resolves an optional {@code Authorization: Bearer gav_live_…} credential.
 *
 * <ul>
 *   <li>No {@code Authorization} header → request proceeds anonymously (the free,
 *       IP-throttled tier).</li>
 *   <li>A valid key → the {@link AuthenticatedClient} is attached as a request
 *       attribute for downstream plan limits, quota, and metering.</li>
 *   <li>A malformed or unrecognised bearer token → {@code 401}.</li>
 * </ul>
 *
 * Runs before the rate-limit filter so throttling can key off the account.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    /**
     * The Marketplace webhook authenticates with a Microsoft Entra JWT in the
     * {@code Authorization} header, which this filter would otherwise reject as
     * an invalid API key. Its own controller validates that JWT.
     */
    private static final String MARKETPLACE_WEBHOOK_PATH = "/api/v1/marketplace/webhook";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (MARKETPLACE_WEBHOOK_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            chain.doFilter(request, response); // anonymous
            return;
        }

        String token = header.regionMatches(true, 0, "Bearer ", 0, 7)
                ? header.substring(7).trim()
                : header.trim();

        Optional<AuthenticatedClient> client = apiKeyService.authenticate(token);
        if (client.isEmpty()) {
            JsonErrors.write(objectMapper, request, response, HttpStatus.UNAUTHORIZED,
                    "Invalid or revoked API key.");
            return;
        }

        request.setAttribute(AuthenticatedClient.ATTRIBUTE, client.get());
        chain.doFilter(request, response);
    }

    /** Reads the authenticated client attached to a request, if any. */
    public static Optional<AuthenticatedClient> current(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthenticatedClient.ATTRIBUTE);
        return attr instanceof AuthenticatedClient c ? Optional.of(c) : Optional.empty();
    }
}
