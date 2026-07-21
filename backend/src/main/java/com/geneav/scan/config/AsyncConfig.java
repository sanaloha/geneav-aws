package com.geneav.scan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async} so transactional mail sends off the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
