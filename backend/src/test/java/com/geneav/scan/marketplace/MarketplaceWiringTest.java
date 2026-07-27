package com.geneav.scan.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the marketplace beans can actually be constructed by Spring.
 *
 * <p>The other tests in this package build these classes directly, which hides
 * dependency-injection problems: {@link WebhookJwtValidator} has a second,
 * package-private constructor as a test seam, and without an explicit
 * {@code @Autowired} Spring cannot choose between the two — it looks for a
 * no-arg constructor instead and the whole application fails to start. That
 * only showed up when the app was run for real. This test closes the gap
 * without needing a database or a live ClamAV.
 */
class MarketplaceWiringTest {

    @Test
    void marketplaceBeansAreConstructableBySpring() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            // MarketplaceProperties is contributed by @EnableConfigurationProperties
            // on MarketplaceTokenProvider — registering it here too would make the
            // dependency ambiguous.
            ctx.getBeanFactory().registerSingleton("restClientBuilder", RestClient.builder());
            ctx.register(MarketplaceTokenProvider.class, FulfillmentClient.class, WebhookJwtValidator.class);
            ctx.refresh();

            assertThat(ctx.getBean(WebhookJwtValidator.class)).isNotNull();
            assertThat(ctx.getBean(FulfillmentClient.class)).isNotNull();
            assertThat(ctx.getBean(MarketplaceTokenProvider.class)).isNotNull();
        }
    }
}
