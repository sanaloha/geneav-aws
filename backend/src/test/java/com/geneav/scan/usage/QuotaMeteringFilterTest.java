package com.geneav.scan.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geneav.scan.account.Account;
import com.geneav.scan.account.ApiKey;
import com.geneav.scan.account.ApiKeyService;
import com.geneav.scan.account.AuthenticatedClient;
import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.plan.PlanProperties;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotaMeteringFilterTest {

    @RestController
    @RequestMapping("/api/v1")
    static class StubController {
        @PostMapping("/scan")
        String scan() {
            return "{}";
        }
    }

    private final UsageService usage = mock(UsageService.class);
    private final ApiKeyService apiKeyService = mock(ApiKeyService.class);
    private final UUID accountId = UUID.randomUUID();
    private final UUID keyId = UUID.randomUUID();

    private PlanCatalog catalogWithQuota(long quota) {
        PlanProperties props = new PlanProperties();
        props.setDefaultPlan("free");
        PlanProperties.Plan plan = new PlanProperties.Plan();
        plan.setMonthlyScanQuota(quota);
        plan.setRatePerMinute(1000);
        plan.setBurst(1000);
        props.getDefinitions().put("free", plan);
        return new PlanCatalog(props);
    }

    /** Injects an authenticated client into the request, like ApiKeyAuthFilter would. */
    private Filter authAs(AuthenticatedClient client) {
        return (req, res, chain) -> {
            req.setAttribute(AuthenticatedClient.ATTRIBUTE, client);
            chain.doFilter(req, res);
        };
    }

    private AuthenticatedClient client() {
        Account account = new Account(accountId, "a@b.com", "free", "active", Instant.now());
        ApiKey key = new ApiKey(keyId, accountId, "k", "hash", "gav_live_abcd", "wxyz", "active", Instant.now());
        return new AuthenticatedClient(account, key);
    }

    private MockMvc mvc(PlanCatalog plans, Filter... pre) {
        QuotaMeteringFilter filter = new QuotaMeteringFilter(usage, plans, apiKeyService, new ObjectMapper());
        var builder = MockMvcBuilders.standaloneSetup(new StubController());
        for (Filter f : pre) {
            builder.addFilters(f);
        }
        return builder.addFilters(filter).build();
    }

    @Test
    void overQuotaReturns402AndDoesNotMeter() throws Exception {
        when(usage.currentCount(accountId, "scan")).thenReturn(5L);

        mvc(catalogWithQuota(5), authAs(client()))
                .perform(post("/api/v1/scan"))
                .andExpect(status().isPaymentRequired());

        verify(usage, never()).record(eq(accountId), eq("scan"));
    }

    @Test
    void underQuotaPassesThroughAndMetersOnSuccess() throws Exception {
        when(usage.currentCount(accountId, "scan")).thenReturn(1L);

        mvc(catalogWithQuota(100), authAs(client()))
                .perform(post("/api/v1/scan"))
                .andExpect(status().isOk());

        verify(usage).record(accountId, "scan");
        verify(apiKeyService).touchLastUsed(keyId);
    }

    @Test
    void anonymousRequestIsNotMetered() throws Exception {
        mvc(catalogWithQuota(1)) // no auth pre-filter -> anonymous
                .perform(post("/api/v1/scan"))
                .andExpect(status().isOk());

        verify(usage, never()).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
