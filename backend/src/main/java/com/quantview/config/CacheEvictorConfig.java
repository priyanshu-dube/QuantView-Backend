package com.quantview.config;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class CacheEvictorConfig {

    /**
     * Evict the "stocks" cache every 60 seconds.
     */
    @CacheEvict(value = "stocks", allEntries = true)
    @Scheduled(fixedRate = 60000)
    public void evictStocksCache() {}

    /**
     * Evict the "predictions" cache every 300 seconds.
     */
    @CacheEvict(value = "predictions", allEntries = true)
    @Scheduled(fixedRate = 300000)
    public void evictPredictionsCache() {}
}
