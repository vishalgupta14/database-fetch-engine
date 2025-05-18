package com.platform.engine.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.platform.engine.model.DatabaseConfig;
import com.platform.engine.repository.DatabaseConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseConfigService {

    private final DatabaseConfigRepository repository;
    private final Cache<String, DSLContext> dslContextCache;
    private final DatabaseFetchEngine databaseFetchEngine;

    public Flux<DatabaseConfig> findAll() {
        return repository.findAll();
    }

    public Mono<DatabaseConfig> findById(String id) {
        return repository.findById(id);
    }

    public Mono<DatabaseConfig> create(DatabaseConfig config) {
        return validate(config)
                .flatMap(valid ->
                        repository.findByName(valid.getName())
                                .flatMap(existing -> Mono.<DatabaseConfig>error(new IllegalArgumentException("A config with this name already exists")))
                                .switchIfEmpty(testAndSave(valid))
                );
    }

    // Extracted method for connection test, save, and caching
    private Mono<DatabaseConfig> testAndSave(DatabaseConfig config) {
        return testConnection(config)
                .flatMap(repository::save)
                .flatMap(saved -> Mono.fromCallable(() -> {
                    DSLContext context = databaseFetchEngine.createDslContext(saved);
                    dslContextCache.put(saved.getId(), context);
                    log.info("✅ DSLContext cached for config ID: {}", saved.getId());
                    return saved;
                }).cast(DatabaseConfig.class));
    }



    public Mono<DatabaseConfig> update(String id, DatabaseConfig updated) {
        return validate(updated)
                .flatMap(valid ->
                        repository.findByName(valid.getName())
                                .flatMap(existing -> {
                                    if (!existing.getId().equals(id)) {
                                        return Mono.error(new IllegalArgumentException("A config with this name already exists"));
                                    }
                                    return Mono.just(valid);
                                })
                                .switchIfEmpty(Mono.just(valid)) // Name not in use or same config
                )
                .flatMap(this::testConnection)
                .flatMap(valid -> {
                    updated.setId(id); // Required to update instead of insert
                    return repository.save(updated);
                })
                .flatMap(saved -> Mono.fromCallable(() -> {
                    DSLContext context = databaseFetchEngine.createDslContext(saved);
                    dslContextCache.put(saved.getId(), context);
                    log.info("♻️ DSLContext updated in cache for ID: {}", saved.getId());
                    return saved;
                }));
    }


    public Mono<Void> delete(String id) {
        return repository.deleteById(id)
                .doOnSuccess(v -> {
                    dslContextCache.invalidate(id);
                    log.info("🗑️ DSLContext removed from cache for deleted config ID: {}", id);
                });
    }

    // Validate required fields
    private Mono<DatabaseConfig> validate(DatabaseConfig config) {
        if (config == null ||
                isBlank(config.getName()) ||
                isBlank(config.getDbType()) ||
                isBlank(config.getHost()) ||
                config.getPort() == null ||
                isBlank(config.getDatabase()) ||
                isBlank(config.getUsername()) ||
                isBlank(config.getPassword())) {
            return Mono.error(new IllegalArgumentException("Missing required database configuration fields"));
        }

        if (!config.getDbType().equalsIgnoreCase("POSTGRES") &&
                !config.getDbType().equalsIgnoreCase("MYSQL")) {
            return Mono.error(new IllegalArgumentException("Unsupported DB type: " + config.getDbType()));
        }

        return Mono.just(config);
    }

    // Test DB connection before saving
    private Mono<DatabaseConfig> testConnection(DatabaseConfig config) {
        return Mono.fromCallable(() -> {
            try (Connection conn = databaseFetchEngine.createConnection(config)) {
                log.info("✅ Database connection verified for {}", config.getName());
                return config;
            }
        }).onErrorResume(e -> {
            log.error("❌ Failed to connect to DB [{}]: {}", config.getName(), e.getMessage());
            return Mono.error(new RuntimeException("Database connection failed: " + e.getMessage()));
        });
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
