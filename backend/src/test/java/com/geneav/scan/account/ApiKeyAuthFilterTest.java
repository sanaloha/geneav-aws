package com.geneav.scan.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiKeyAuthFilterTest {

    @RestController
    @RequestMapping("/api/v1")
    static class StubController {
        @PostMapping("/scan")
        String scan() {
            return "{}";
        }

        @PostMapping("/marketplace/webhook")
        String webhook() {
            return "{}";
        }
    }

    private MockMvc mvc() {
        ApiKeyService keys = mock(ApiKeyService.class);
        when(keys.authenticate(any())).thenReturn(Optional.empty()); // every key is invalid
        return MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(new ApiKeyAuthFilter(keys, new ObjectMapper()))
                .build();
    }

    @Test
    void unrecognisedBearerTokenIs401OnNormalEndpoints() throws Exception {
        mvc().perform(post("/api/v1/scan").header("Authorization", "Bearer nonsense"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void marketplaceWebhookBypassesApiKeyAuth() throws Exception {
        // Microsoft authenticates the webhook with an Entra JWT in the same
        // Authorization header; this filter must not mistake it for an API key.
        mvc().perform(post("/api/v1/marketplace/webhook").header("Authorization", "Bearer eyJhbGciOi..."))
                .andExpect(status().isOk());
    }
}
