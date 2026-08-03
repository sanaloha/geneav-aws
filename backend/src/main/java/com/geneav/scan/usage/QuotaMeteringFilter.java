package com.geneav.scan.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geneav.scan.account.ApiKeyAuthFilter;
import com.geneav.scan.account.ApiKeyService;
import com.geneav.scan.account.AuthenticatedClient;
import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.web.JsonErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces the monthly scan quota and meters usage for authenticated scans.
 *
 * <p>For {@code POST /api/v1/scan}: the month's usage is checked against the plan
 * quota up front ({@code 402} when exhausted), and one unit is recorded only after
 * the scan actually succeeds — failed scans are not billed.
 *
 * <p>Everything else passes straight through. Since 3 Aug 2026 a scan cannot reach
 * this filter without a key — {@link com.geneav.scan.account.ApiKeyAuthFilter} answers
 * {@code 401} first — so the unauthenticated case that used to slip past unmetered
 * no longer exists. The null check below is kept as a guard, not as a supported path.
 */
public class QuotaMeteringFilter extends OncePerRequestFilter {

    private static final String SCAN_PATH = "/api/v1/scan";
    private static final String ENDPOINT = "scan";

    private final UsageService usage;
    private final PlanCatalog plans;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public QuotaMeteringFilter(UsageService usage, PlanCatalog plans,
                               ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.usage = usage;
        this.plans = plans;
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        AuthenticatedClient client = ApiKeyAuthFilter.current(request).orElse(null);
        boolean metered = client != null && SCAN_PATH.equals(request.getRequestURI())
                && "POST".equalsIgnoreCase(request.getMethod());

        if (!metered) {
            chain.doFilter(request, response);
            return;
        }

        long quota = plans.resolve(client.account().getPlan()).getMonthlyScanQuota();
        if (usage.currentCount(client.account().getId(), ENDPOINT) >= quota) {
            JsonErrors.write(objectMapper, request, response, HttpStatus.PAYMENT_REQUIRED,
                    "Monthly scan quota of " + quota + " reached. Upgrade your plan to continue.");
            return;
        }

        chain.doFilter(request, response);

        // Bill only successful scans.
        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            usage.record(client.account().getId(), ENDPOINT);
            apiKeyService.touchLastUsed(client.apiKey().getId());
        }
    }
}
