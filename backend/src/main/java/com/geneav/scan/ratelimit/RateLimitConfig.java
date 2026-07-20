package com.geneav.scan.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geneav.scan.plan.PlanCatalog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the rate-limit filter into the servlet chain with high precedence so it
 * runs before request-body parsing, and enables the scheduled bucket eviction.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiterService limiter, RateLimitProperties props, PlanCatalog plans, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(limiter, props, plans, objectMapper));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("rateLimitFilter");
        return registration;
    }
}
