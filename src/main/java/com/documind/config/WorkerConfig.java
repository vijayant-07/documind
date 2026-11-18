package com.documind.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling for background job processing and maintenance tasks
 * when documind.worker.enabled is true (default).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "documind.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkerConfig {
}

