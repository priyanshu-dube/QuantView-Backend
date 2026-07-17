package com.quantview.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a CacheManager bean so @Cacheable annotations
 * on StockController and PredictionController actually work.
 * Uses in-memory ConcurrentMapCache (suitable for single-instance deploys).
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("stocks", "predictions");
    }
}
