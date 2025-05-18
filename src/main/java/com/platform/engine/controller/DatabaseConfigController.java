package com.platform.engine.controller;

import com.platform.engine.model.DatabaseConfig;
import com.platform.engine.service.DatabaseConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/configs")
@RequiredArgsConstructor
public class DatabaseConfigController {

    private final DatabaseConfigService service;

    @GetMapping
    public Flux<DatabaseConfig> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<DatabaseConfig>> getById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Mono<ResponseEntity<DatabaseConfig>> create(@RequestBody DatabaseConfig config) {
        return service.create(config).map(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<DatabaseConfig>> update(@PathVariable String id, @RequestBody DatabaseConfig config) {
        return service.update(id, config).map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable String id) {
        return service.delete(id).thenReturn(ResponseEntity.noContent().build());
    }
}
