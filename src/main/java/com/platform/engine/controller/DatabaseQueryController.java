package com.platform.engine.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.engine.dto.QueryRequest;
import com.platform.engine.service.DatabaseFetchEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class DatabaseQueryController {

    private final DatabaseFetchEngine fetchEngine;

    @PostMapping(value = "/data", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<JsonNode> fetchData(@RequestBody QueryRequest request) {
        return fetchEngine.fetchData(request);
    }

    @PostMapping("/count")
    public Mono<Long> fetchCount(@RequestBody QueryRequest request) {
        return fetchEngine.fetchCount(request);
    }

    @PostMapping("/delete")
    public Mono<ResponseEntity<Long>> deleteData(@RequestBody QueryRequest request) {
        return fetchEngine.deleteData(request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/schema")
    public Mono<ResponseEntity<JsonNode>> getTableSchema(@RequestBody QueryRequest request) {
        return fetchEngine.getTableSchema(request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }


}

