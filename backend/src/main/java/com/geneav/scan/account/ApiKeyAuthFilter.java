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
import java.util.Set;

/**
 * Requires an {@code Authorization: Bearer gav_live_…} credential on the public API.
 *
 * <ul>
 *   <li>A valid key → the {@link AuthenticatedClient} is attached as a request
 *       attribute for downstream plan limits, quota, and metering.</li>
 *   <li>A malformed or unrecognised bearer token → {@code 401}.</li>
 *   <li>No {@code Authorization} header → {@code 401}, except on the paths listed
 *       in {@link #allowsMissingKey}.</li>
 * </ul>
 *
 * <p>Until 3 Aug 2026 a missing header meant "anonymous", and {@code POST /api/v1/scan}
 * served such a caller from a free, IP-throttled tier. That left the product's one
 * metered operation open to any {@code curl} on the internet: usage could not be
 * attributed to an account, the plan quota in {@code QuotaMeteringFilter} never
 * applied, and a per-IP rate limit was the only ceiling. Every scan and chat call
 * now costs a key issued from the dashboard.
 *
 * <p>The website keeps its keyless try-it demo by calling through a server-side
 * proxy that holds a dedicated demo key — see {@code frontend/app/site-api/} — so
 * the browser never carries a credential and demo traffic meters against a real
 * account like any other client's.
 *
 * <p>Runs before the rate-limit filter so throttling can key off the account.
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

    /**
     * Reachable with no credential at all. Each entry is here because requiring a
     * key would be either impossible or actively wrong:
     * <ul>
     *   <li>{@code /signup} and {@code /auth/*} are how a caller obtains the first
     *       key in the first place — requiring one would lock everybody out.</li>
     *   <li>{@code /health} is the documented liveness probe. Load balancers and
     *       uptime monitors generally cannot carry a bearer token, and it discloses
     *       nothing beyond whether ClamAV is reachable.</li>
     * </ul>
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/health",
            "/api/v1/signup");

    private static final String AUTH_PREFIX = "/api/v1/auth/";

    /**
     * Endpoints that resolve their own caller through {@link CurrentAccount}, which
     * accepts <em>either</em> a dashboard session cookie or an API key, and answers
     * {@code 401} itself when neither is present.
     *
     * <p>They are exempt from the blanket key requirement but are <em>not</em>
     * unauthenticated: the dashboard reaches them with a session and no
     * {@code Authorization} header at all, and rejecting that here would break the
     * very page where keys are created. A key sent to them is still validated below
     * like anywhere else.
     */
    private static final Set<String> SESSION_OR_KEY_PATHS = Set.of(
            "/api/v1/keys",
            "/api/v1/usage",
            "/api/v1/marketplace/resolve",
            "/api/v1/marketplace/activate",
            "/api/v1/marketplace/subscription");

    /** {@code DELETE /api/v1/keys/{id}} — same session-or-key rule as the set above. */
    private static final String KEYS_PREFIX = "/api/v1/keys/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (MARKETPLACE_WEBHOOK_PATH.equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        // CORS preflight carries no Authorization header by definition, and Spring
        // Security's CorsFilter — which would answer it — sits far later in the
        // chain than this filter. Rejecting it here would break every cross-origin
        // dashboard call before the real request was ever sent.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            if (allowsMissingKey(path)) {
                chain.doFilter(request, response);
                return;
            }
            JsonErrors.write(objectMapper, request, response, HttpStatus.UNAUTHORIZED,
                    "This endpoint requires an API key. Send 'Authorization: Bearer "
                            + ApiKeyService.PREFIX + "...'. Create a key in your geneav dashboard.");
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

    /** Whether this path may be served without an {@code Authorization} header. */
    private static boolean allowsMissingKey(String path) {
        return PUBLIC_PATHS.contains(path)
                || path.startsWith(AUTH_PREFIX)
                || SESSION_OR_KEY_PATHS.contains(path)
                || isSingleKeyPath(path);
    }

    /**
     * Matches {@code /api/v1/keys/{id}} and nothing deeper.
     *
     * <p>A bare {@code startsWith} would exempt anything beginning with that prefix.
     * Tomcat normalises {@code ..} out of the URI long before a filter sees it, so
     * this is not closing a known hole — it just keeps the exemption as narrow as
     * the endpoint it exists for, rather than resting on that guarantee.
     */
    private static boolean isSingleKeyPath(String path) {
        if (!path.startsWith(KEYS_PREFIX)) {
            return false;
        }
        String remainder = path.substring(KEYS_PREFIX.length());
        return !remainder.isEmpty() && remainder.indexOf('/') < 0;
    }

    /** Reads the authenticated client attached to a request, if any. */
    public static Optional<AuthenticatedClient> current(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthenticatedClient.ATTRIBUTE);
        return attr instanceof AuthenticatedClient c ? Optional.of(c) : Optional.empty();
    }
}
