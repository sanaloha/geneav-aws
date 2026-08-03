package com.geneav.scan.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiKeyAuthFilterTest {

    private static final String VALID_KEY = ApiKeyService.PREFIX + "goodkey";

    @RestController
    @RequestMapping("/api/v1")
    static class StubController {
        @PostMapping("/scan")
        String scan() {
            return "{}";
        }

        @PostMapping("/chat")
        String chat() {
            return "{}";
        }

        @GetMapping("/chat/suggestions")
        String suggestions() {
            return "[]";
        }

        @GetMapping("/health")
        String health() {
            return "{}";
        }

        @PostMapping("/signup")
        String signup() {
            return "{}";
        }

        @PostMapping("/auth/login")
        String login() {
            return "{}";
        }

        @GetMapping("/keys")
        String keys() {
            return "[]";
        }

        @DeleteMapping("/keys/{id}")
        String revokeKey() {
            return "{}";
        }

        @GetMapping("/usage")
        String usage() {
            return "{}";
        }

        @PostMapping("/marketplace/webhook")
        String webhook() {
            return "{}";
        }
    }

    private MockMvc mvc() {
        ApiKeyService keys = mock(ApiKeyService.class);
        when(keys.authenticate(any())).thenReturn(Optional.empty()); // every other key is invalid
        Account account = new Account(UUID.randomUUID(), "dev@example.com", "free", "active", Instant.now());
        ApiKey key = new ApiKey(UUID.randomUUID(), account.getId(), "test", "hash",
                ApiKeyService.PREFIX + "good", "ykey", "active", Instant.now());
        when(keys.authenticate(eq(VALID_KEY)))
                .thenReturn(Optional.of(new AuthenticatedClient(account, key)));
        return MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(new ApiKeyAuthFilter(keys, new ObjectMapper()))
                .build();
    }

    @Test
    void scanWithoutAnyKeyIs401() throws Exception {
        // The whole point of the change: a keyless curl no longer gets a free scan.
        mvc().perform(post("/api/v1/scan"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("requires an API key")));
    }

    @Test
    void chatWithoutAnyKeyIs401() throws Exception {
        mvc().perform(post("/api/v1/chat")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/v1/chat/suggestions")).andExpect(status().isUnauthorized());
    }

    @Test
    void scanWithAValidKeyIsAllowedAndAttachesTheClient() throws Exception {
        mvc().perform(post("/api/v1/scan").header("Authorization", "Bearer " + VALID_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void unrecognisedBearerTokenIs401OnNormalEndpoints() throws Exception {
        mvc().perform(post("/api/v1/scan").header("Authorization", "Bearer nonsense"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndSignupAndAuthStayReachableWithoutAKey() throws Exception {
        // Monitors cannot carry a bearer token, and signup/auth are how a caller
        // gets a key at all — requiring one here would lock everybody out.
        mvc().perform(get("/api/v1/health")).andExpect(status().isOk());
        mvc().perform(post("/api/v1/signup")).andExpect(status().isOk());
        mvc().perform(post("/api/v1/auth/login")).andExpect(status().isOk());
    }

    @Test
    void dashboardEndpointsPassThroughForTheSessionToAuthenticate() throws Exception {
        // These carry a session cookie, not a key; CurrentAccount enforces auth in
        // the controller and 401s on its own when neither credential is present.
        mvc().perform(get("/api/v1/keys")).andExpect(status().isOk());
        mvc().perform(delete("/api/v1/keys/" + UUID.randomUUID())).andExpect(status().isOk());
        mvc().perform(get("/api/v1/usage")).andExpect(status().isOk());
    }

    @Test
    void dashboardEndpointsStillRejectABadKey() throws Exception {
        mvc().perform(get("/api/v1/keys").header("Authorization", "Bearer nonsense"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightIsNotRejected() throws Exception {
        // Preflight never carries Authorization, and Spring Security's CorsFilter
        // that would answer it runs after this one.
        mvc().perform(options("/api/v1/scan")).andExpect(status().isOk());
    }

    @Test
    void marketplaceWebhookBypassesApiKeyAuth() throws Exception {
        // Microsoft authenticates the webhook with an Entra JWT in the same
        // Authorization header; this filter must not mistake it for an API key.
        mvc().perform(post("/api/v1/marketplace/webhook").header("Authorization", "Bearer eyJhbGciOi..."))
                .andExpect(status().isOk());
    }
}
