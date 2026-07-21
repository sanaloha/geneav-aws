package com.geneav.scan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Async} so transactional mail sends off the request thread, and
 * {@code @Scheduled} for background housekeeping such as the reset-token sweep.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
