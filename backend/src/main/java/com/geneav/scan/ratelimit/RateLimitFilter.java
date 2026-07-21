package com.geneav.scan.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geneav.scan.account.ApiKeyAuthFilter;
import com.geneav.scan.account.AuthenticatedClient;
import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.plan.PlanProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-client rate limiting for the public API, applied before Spring parses the
 * request body — so a throttled upload is rejected without buffering its bytes.
 *
 * <p>Two independent guards:
 * <ul>
 *   <li><b>Rate</b>: a token bucket keyed by endpoint + client IP (scan and chat
 *       get their own, tighter, budgets since each costs real resources).</li>
 *   <li><b>Concurrency</b>: scans additionally take a permit from a small global
 *       pool so a burst cannot exhaust ClamAV's memory.</li>
 * </ul>
 * Both breaches return {@code 429} with a {@code Retry-After} header and the same
 * JSON error shape the rest of the API uses.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String SCAN_PATH = "/api/v1/scan";
    private static final String CHAT_PATH = "/api/v1/chat";
    private static final String AUTH_PREFIX = "/api/v1/auth/";

    private final RateLimiterService limiter;
    private final RateLimitProperties props;
    private final PlanCatalog plans;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterService limiter, RateLimitProperties props,
                           PlanCatalog plans, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.props = props;
        this.plans = plans;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!props.isEnabled() || isExempt(request)) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String type = typeOf(path);

        // Authenticated clients are throttled by their plan and keyed by account;
        // anonymous clients fall back to the free per-IP budgets.
        AuthenticatedClient client = ApiKeyAuthFilter.current(request).orElse(null);
        RateLimitProperties.Limit limit;
        String key;
        if ("auth".equals(type)) {
            // Always per-IP, never per-plan: a generous plan must not buy a higher
            // password-guessing or reset-email budget.
            limit = props.getAuth();
            key = "auth:" + clientIp(request);
        } else if (client != null) {
            PlanProperties.Plan plan = plans.resolve(client.account().getPlan());
            limit = new RateLimitProperties.Limit(plan.getBurst(), plan.getRatePerMinute());
            key = "acct:" + client.account().getId() + ":" + type;
        } else {
            limit = limitFor(type);
            key = type + ":" + clientIp(request);
        }

        if (!limiter.tryAcquire(key, limit)) {
            reject(request, response, limiter.retryAfterSeconds(key, limit),
                    "Rate limit exceeded. Please slow down and retry shortly.");
            return;
        }

        if (!SCAN_PATH.equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Scan: also hold a concurrency permit for the duration of the request.
        boolean acquired = false;
        try {
            acquired = limiter.tryAcquireScanSlot();
            if (!acquired) {
                reject(request, response, 2,
                        "The scanner is busy processing other requests. Please retry in a moment.");
                return;
            }
            chain.doFilter(request, response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reject(request, response, 2, "The scanner is busy. Please retry in a moment.");
        } finally {
            if (acquired) {
                limiter.releaseScanSlot();
            }
        }
    }

    /** Health/monitoring probes are never throttled. */
    private boolean isExempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/")
                || path.equals("/api/v1/health")
                || path.equals("/api/v1/chat/health")
                // Polled on every dashboard page load; throttling it would break
                // the UI long before it deterred anyone.
                || path.equals("/api/v1/auth/me")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private String typeOf(String path) {
        if (SCAN_PATH.equals(path)) {
            return "scan";
        }
        if (CHAT_PATH.equals(path)) {
            return "chat";
        }
        if (path.startsWith(AUTH_PREFIX)) {
            return "auth";
        }
        return "other";
    }

    private RateLimitProperties.Limit limitFor(String type) {
        return switch (type) {
            case "scan" -> props.getScan();
            case "chat" -> props.getChat();
            default -> props.getOther();
        };
    }

    /**
     * The client's IP. Behind Caddy the real address is the last entry of
     * {@code X-Forwarded-For} (Caddy appends the peer it actually saw, so any
     * value a client tries to inject sits to the left and is ignored). Falls back
     * to the socket address for direct/local calls.
     */
    static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds, String message)
            throws IOException {
        log.debug("rate-limit: {} {} from {} -> 429", request.getMethod(), request.getRequestURI(), clientIp(request));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (retryAfterSeconds > 0) {
            response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
