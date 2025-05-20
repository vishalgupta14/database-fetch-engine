package com.platform.engine.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.engine.dto.EntityMetadata;
import com.platform.engine.dto.QueryRequest;
import com.platform.engine.service.JavaEntityQueryEngine;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/entity-query")
@RequiredArgsConstructor
public class EntityQueryController {

    private final JavaEntityQueryEngine entityQueryEngine;

    /**
     * Fetch in-memory Java entity data based on filters, sorting, and pagination.
     */
    @PostMapping(value = "/data", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<JsonNode> fetchEntityData(@RequestBody QueryRequest request) {
        return entityQueryEngine.fetchData(request);
    }

    /**
     * Count matching in-memory entity rows.
     */
    @PostMapping("/count")
    public Mono<Long> fetchEntityCount(@RequestBody QueryRequest request) {
        return entityQueryEngine.fetchCount(request);
    }

    /**
     * Delete matching entity objects from in-memory storage.
     */
    @PostMapping("/delete")
    public Mono<ResponseEntity<Long>> deleteEntityData(@RequestBody QueryRequest request) {
        return entityQueryEngine.deleteData(request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/metadata")
    public ResponseEntity<?> getMetadata(@RequestBody QueryRequest request) {
        try {
            List<EntityMetadata> metadata = entityQueryEngine.getMetadata(request);
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }


}
