package com.geneav.scan.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.plan.PlanProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitFilterTest {

    @RestController
    @RequestMapping("/api/v1")
    static class StubController {
        @PostMapping("/chat")
        String chat() {
            return "{}";
        }

        @GetMapping("/health")
        String health() {
            return "{}";
        }
    }

    private MockMvc mvcWith(RateLimitProperties props) {
        RateLimiterService limiter = new RateLimiterService(props);
        PlanCatalog plans = new PlanCatalog(new PlanProperties());
        RateLimitFilter filter = new RateLimitFilter(limiter, props, plans, new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(filter)
                .build();
    }

    private RateLimitProperties chatCapacity(int capacity) {
        RateLimitProperties p = new RateLimitProperties();
        p.setChat(new RateLimitProperties.Limit(capacity, 1)); // tiny refill so it stays empty
        return p;
    }

    @Test
    void allowsUpToCapacityThenReturns429() throws Exception {
        MockMvc mvc = mvcWith(chatCapacity(2));

        mvc.perform(post("/api/v1/chat")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/chat")).andExpect(status().isOk());

        mvc.perform(post("/api/v1/chat"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/v1/chat"));
    }

    @Test
    void healthEndpointIsNeverThrottled() throws Exception {
        RateLimitProperties props = new RateLimitProperties();
        props.setOther(new RateLimitProperties.Limit(1, 1));
        MockMvc mvc = mvcWith(props);

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/health").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void disabledFilterLetsEverythingThrough() throws Exception {
        RateLimitProperties props = chatCapacity(1);
        props.setEnabled(false);
        MockMvc mvc = mvcWith(props);

        mvc.perform(post("/api/v1/chat")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/chat")).andExpect(status().isOk());
    }

    @Test
    void clientIpPrefersLastForwardedForEntry() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2");
        assertThat(RateLimitFilter.clientIp(req)).isEqualTo("2.2.2.2");
    }

    @Test
    void clientIpFallsBackToRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        assertThat(RateLimitFilter.clientIp(req)).isEqualTo("10.0.0.1");
    }
}
