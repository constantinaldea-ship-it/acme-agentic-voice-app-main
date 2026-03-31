package com.voicebanking.bfa.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for BFA service.
 * 
 * <p>Caches consent and legitimation results for performance.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Configuration
public class CacheConfig {
    
    @Value("${bfa.consent.cache.ttl-seconds:300}")
    private int consentCacheTtl;
    
    @Value("${bfa.legitimation.cache.ttl-seconds:300}")
    private int legitimationCacheTtl;
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .recordStats());
        
        // Register specific caches
        cacheManager.setCacheNames(java.util.List.of("consents", "legitimation"));
        
        return cacheManager;
    }
}
