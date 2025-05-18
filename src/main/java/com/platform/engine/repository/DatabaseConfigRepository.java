package com.platform.engine.repository;

import com.platform.engine.model.DatabaseConfig;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface DatabaseConfigRepository extends ReactiveMongoRepository<DatabaseConfig, String> {

    Mono<DatabaseConfig> findByName(String name);
}
