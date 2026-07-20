package com.geneav.scan.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.usage.QuotaMeteringFilter;
import com.geneav.scan.usage.UsageService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Orders the API request pipeline for {@code /api/v1/*}:
 * <ol>
 *   <li>{@link ApiKeyAuthFilter} (HIGHEST+5) — resolve the key, attach the client.</li>
 *   <li>{@code RateLimitFilter} (HIGHEST+10) — throttle by plan or IP.</li>
 *   <li>{@link QuotaMeteringFilter} (HIGHEST+15) — enforce quota, meter usage.</li>
 * </ol>
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
            ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration =
                new FilterRegistrationBean<>(new ApiKeyAuthFilter(apiKeyService, objectMapper));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        registration.setName("apiKeyAuthFilter");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<QuotaMeteringFilter> quotaMeteringFilter(
            UsageService usage, PlanCatalog plans, ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        FilterRegistrationBean<QuotaMeteringFilter> registration =
                new FilterRegistrationBean<>(new QuotaMeteringFilter(usage, plans, apiKeyService, objectMapper));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 15);
        registration.setName("quotaMeteringFilter");
        return registration;
    }
}
