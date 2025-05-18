package com.platform.engine.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, DSLContext> dslContextCache() {
        return Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .build();
    }
}
