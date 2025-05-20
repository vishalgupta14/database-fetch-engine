package com.platform.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.platform.engine.dto.DirectDatabaseConfig;
import com.platform.engine.dto.JoinRequest;
import com.platform.engine.dto.QueryRequest;
import com.platform.engine.filter.JooqFilterUtils;
import com.platform.engine.model.DatabaseConfig;
import com.platform.engine.repository.DatabaseConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseFetchEngine {

    private static final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(new JavaTimeModule());


    private final DatabaseConfigRepository configRepository;

    private final Cache<String, DSLContext> dslContextCache;

    private final Cache<String, Map<String, Field<?>>> schemaCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();


    public Mono<DSLContext> getDslContext(QueryRequest request) {
        // Prefer configId if provided
        if (request.getConfigId() != null) {
            DSLContext cached = dslContextCache.getIfPresent(request.getConfigId());
            if (cached != null) return Mono.just(cached);

            return configRepository.findById(request.getConfigId())
                    .flatMap(config -> Mono.fromCallable(() -> {
                        DSLContext ctx = createDslContext(config);
                        dslContextCache.put(config.getId(), ctx);
                        return ctx;
                    }));
        }

        // Handle direct config
        if (request.getDirectConfig() != null) {
            String key = generateDirectCacheKeySafe(request.getDirectConfig());
            if (key != null) {
                DSLContext cached = dslContextCache.getIfPresent(key);
                if (cached != null) return Mono.just(cached);

                return Mono.fromCallable(() -> {
                    DatabaseConfig cfg = new DatabaseConfig();
                    cfg.setDbType(request.getDirectConfig().getDbType());
                    cfg.setHost(request.getDirectConfig().getHost());
                    cfg.setPort(request.getDirectConfig().getPort());
                    cfg.setDatabase(request.getDirectConfig().getDatabase());
                    cfg.setUsername(request.getDirectConfig().getUsername());
                    cfg.setPassword(request.getDirectConfig().getPassword());
                    cfg.setSchema(request.getDirectConfig().getSchema());

                    DSLContext ctx = createDslContext(cfg);
                    dslContextCache.put(key, ctx);
                    return ctx;
                });
            }
        }

        return Mono.error(new IllegalArgumentException("❌ Either configId or directConfig must be provided"));
    }



    public DSLContext createDslContext(DatabaseConfig config) throws Exception {
        String jdbcUrl;
        SQLDialect dialect;

        switch (config.getDbType().toUpperCase()) {
            case "POSTGRES" -> {
                jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                        config.getHost(), config.getPort(), config.getDatabase());
                dialect = SQLDialect.POSTGRES;
                Class.forName("org.postgresql.Driver"); //  PostgreSQL
            }
            case "MYSQL" -> {
                jdbcUrl = String.format("jdbc:mysql://%s:%d/%s",
                        config.getHost(), config.getPort(), config.getDatabase());
                dialect = SQLDialect.MYSQL;
                Class.forName("com.mysql.cj.jdbc.Driver"); //  MySQL
            }
            default -> throw new IllegalArgumentException("Unsupported DB type: " + config.getDbType());
        }

        Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword());

        return DSL.using(conn, dialect);
    }


    @PostConstruct
    public void preloadContextsOnStartup() {
        log.info("✅ Starting preload of DSLContext instances from MongoDB configs...");

        configRepository.findAll()
                .flatMap(config -> Mono.fromCallable(() -> {
                    try {
                        DSLContext context = createDslContext(config);
                        dslContextCache.put(config.getId(), context);
                        log.info("✅ Cached DSLContext for config ID: {}", config.getId());
                        return context;
                    } catch (Exception e) {
                        log.error("❌ Failed to create DSLContext for config ID: {}", config.getId(), e);
                        throw new RuntimeException("Failed to create DSLContext for config: " + config.getId(), e);
                    }
                }))
                .doOnError(e -> log.error("❌ Error during preload execution: {}", e.getMessage(), e))
                .subscribe();

        log.info("✅ Preloading of DSLContext triggered.");
    }

    public Flux<JsonNode> fetchData(QueryRequest request) {
        return getDslContext(request)
                .flatMapMany(ctx -> {
                    String schemaCacheKey = request.getConfigId() + ":" + request.getTable();

                    Map<String, Field<?>> columnFieldMap = schemaCache.getIfPresent(schemaCacheKey);
                    if (columnFieldMap == null) {
                        org.jooq.Table<?> resolvedTable = ctx.meta().getTables().stream()
                                .filter(t -> t.getName().equalsIgnoreCase(request.getTable()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + request.getTable()));

                        columnFieldMap = new HashMap<>();
                        for (Field<?> field : resolvedTable.fields()) {
                            columnFieldMap.put(field.getName(), field);
                        }
                        schemaCache.put(schemaCacheKey, columnFieldMap);
                    }

                    Condition condition = JooqFilterUtils.buildConditionFromSearchList(request.getFilters(), columnFieldMap);

                    // Table alias support
                    Table<?> baseTable = request.getAlias() != null
                            ? DSL.table(DSL.name(request.getTable())).as(request.getAlias())
                            : DSL.table(DSL.name(request.getTable()));

                    String effectiveAlias = request.getAlias() != null ? request.getAlias() : request.getTable();

                    // SELECT fields
                    SelectSelectStep<Record> selectStep;
                    if (request.getSelectFields() != null && !request.getSelectFields().isEmpty()) {
                        Field<?>[] fields = request.getSelectFields().stream()
                                .map(field -> {
                                    if (!field.contains(".")) {
                                        return DSL.field(DSL.name(effectiveAlias, field));
                                    }
                                    return DSL.field(DSL.name(field));
                                })
                                .toArray(Field[]::new);
                        selectStep = request.isDistinct() ? ctx.selectDistinct(fields) : ctx.select(fields);
                    } else {
                        // default: select all from aliased table
                        selectStep = ctx.select(baseTable.fields());
                    }

                    SelectJoinStep<Record> joinStep = selectStep.from(baseTable);

                    if (request.getJoins() != null) {
                        for (JoinRequest join : request.getJoins()) {
                            Table<?> joinTable = join.getAlias() != null
                                    ? DSL.table(DSL.name(join.getTable())).as(join.getAlias())
                                    : DSL.table(DSL.name(join.getTable()));

                            if (join.getOnLeft().size() != join.getOnRight().size()) {
                                throw new IllegalArgumentException("Mismatched join fields in JoinRequest for table: " + join.getTable());
                            }

                            Condition onCondition = null;
                            for (int i = 0; i < join.getOnLeft().size(); i++) {
                                Field<Object> leftField = resolveFieldFromPath(join.getOnLeft().get(i));
                                Field<Object> rightField = resolveFieldFromPath(join.getOnRight().get(i));
                                Condition c = leftField.eq(rightField);
                                onCondition = (onCondition == null) ? c : onCondition.and(c);
                            }

                            switch (join.getJoinType().toUpperCase()) {
                                case "INNER" -> joinStep = joinStep.join(joinTable).on(onCondition);
                                case "LEFT" -> joinStep = joinStep.leftJoin(joinTable).on(onCondition);
                                case "RIGHT" -> joinStep = joinStep.rightJoin(joinTable).on(onCondition);
                                default -> throw new IllegalArgumentException("Unsupported join type: " + join.getJoinType());
                            }
                        }
                    }

                    var whereStep = joinStep.where(condition);

                    var orderedStep = request.getOrderBy() != null
                            ? (request.getOrderDirection() != null && request.getOrderDirection().isDesc()
                            ? whereStep.orderBy(DSL.field(DSL.name(request.getOrderBy().split("\\."))).desc())
                            : whereStep.orderBy(DSL.field(DSL.name(request.getOrderBy().split("\\."))).asc()))
                            : whereStep;

                    var finalStep = orderedStep;
                    if (request.getLimit() != null && request.getLimit() > 0) {
                        finalStep = (SelectLimitStep<Record>) finalStep.limit(request.getLimit());

                        if (request.getOffset() != null && request.getOffset() > 0) {
                            finalStep = (SelectLimitStep<Record>) finalStep.offset(request.getOffset());
                        }
                    }

                    return Flux.fromIterable(finalStep.fetch())
                            .map(record -> {
                                Map<String, Object> map = new LinkedHashMap<>();
                                Set<String> usedKeys = new HashSet<>();

                                for (Field<?> field : record.fields()) {
                                    String baseColumn = field.getName(); // e.g., "id"
                                    String tableAlias = field.getQualifiedName().first(); // e.g., "user_table", "order_table"

                                    String key;

                                    // If it's already used, then prefix with table alias
                                    if (usedKeys.contains(baseColumn)) {
                                        key = tableAlias + "_" + baseColumn;
                                    } else {
                                        key = baseColumn;
                                        usedKeys.add(baseColumn);
                                    }

                                    Object value = record.get(field);

                                    // Handle JSONB
                                    if (value instanceof org.jooq.JSONB jsonb) {
                                        String json = jsonb.data();
                                        try {
                                            value = mapper.readTree(json);
                                        } catch (Exception e) {
                                            value = json;
                                        }
                                    }

                                    map.put(key, value);
                                }

                                try {
                                    ObjectWriter writer = request.isPretty()
                                            ? mapper.writerWithDefaultPrettyPrinter()
                                            : mapper.writer();
                                    return mapper.readTree(writer.writeValueAsString(map));
                                } catch (Exception e) {
                                    return mapper.convertValue(map, JsonNode.class);
                                }
                            });
                });
    }

    private Field<Object> resolveFieldFromPath(String path) {
        String[] parts = path.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid field path: " + path + " (expected format: alias.column)");
        }
        return DSL.field(DSL.name(parts[0], parts[1]));
    }


    public Mono<Long> fetchCount(QueryRequest request) {
        return getDslContext(request)
                .map(ctx -> {
                    String schemaCacheKey = request.getConfigId() + ":" + request.getTable();

                    // Check schema cache
                    Map<String, Field<?>> columnFieldMap = schemaCache.getIfPresent(schemaCacheKey);
                    if (columnFieldMap == null) {
                        org.jooq.Table<?> resolvedTable = ctx.meta().getTables().stream()
                                .filter(t -> t.getName().equalsIgnoreCase(request.getTable()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + request.getTable()));

                        columnFieldMap = new HashMap<>();
                        for (Field<?> field : resolvedTable.fields()) {
                            columnFieldMap.put(field.getName(), field);
                        }

                        schemaCache.put(schemaCacheKey, columnFieldMap);
                    }

                    // Alias support
                    String effectiveAlias = request.getAlias() != null ? request.getAlias() : request.getTable();
                    Table<?> baseTable = request.getAlias() != null
                            ? DSL.table(DSL.name(request.getTable())).as(request.getAlias())
                            : DSL.table(DSL.name(request.getTable()));

                    // Build base select step
                    SelectJoinStep<Record1<Integer>> joinStep = ctx.selectCount().from(baseTable);

                    // Handle joins
                    if (request.getJoins() != null) {
                        for (JoinRequest join : request.getJoins()) {
                            Table<?> joinTable = join.getAlias() != null
                                    ? DSL.table(DSL.name(join.getTable())).as(join.getAlias())
                                    : DSL.table(DSL.name(join.getTable()));

                            if (join.getOnLeft().size() != join.getOnRight().size()) {
                                throw new IllegalArgumentException("Mismatched join fields in JoinRequest for table: " + join.getTable());
                            }

                            Condition onCondition = null;
                            for (int i = 0; i < join.getOnLeft().size(); i++) {
                                Field<Object> leftField = resolveFieldFromPath(join.getOnLeft().get(i));
                                Field<Object> rightField = resolveFieldFromPath(join.getOnRight().get(i));
                                Condition c = leftField.eq(rightField);
                                onCondition = (onCondition == null) ? c : onCondition.and(c);
                            }

                            switch (join.getJoinType().toUpperCase()) {
                                case "INNER" -> joinStep = joinStep.join(joinTable).on(onCondition);
                                case "LEFT" -> joinStep = joinStep.leftJoin(joinTable).on(onCondition);
                                case "RIGHT" -> joinStep = joinStep.rightJoin(joinTable).on(onCondition);
                                default -> throw new IllegalArgumentException("Unsupported join type: " + join.getJoinType());
                            }
                        }
                    }

                    // WHERE clause
                    Condition condition = JooqFilterUtils.buildConditionFromSearchList(request.getFilters(), columnFieldMap);
                    return joinStep.where(condition).fetchOne(0, Long.class);
                });
    }


    public Connection createConnection(DatabaseConfig config) throws Exception {
        String jdbcUrl = switch (config.getDbType().toUpperCase()) {
            case "POSTGRES" -> {
                Class.forName("org.postgresql.Driver");
                yield String.format("jdbc:postgresql://%s:%d/%s",
                        config.getHost(), config.getPort(), config.getDatabase());
            }
            case "MYSQL" -> {
                Class.forName("com.mysql.cj.jdbc.Driver");
                yield String.format("jdbc:mysql://%s:%d/%s",
                        config.getHost(), config.getPort(), config.getDatabase());
            }
            default -> throw new IllegalArgumentException("Unsupported DB type: " + config.getDbType());
        };

        return DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword());
    }

    public Mono<Long> deleteData(QueryRequest request) {
        return getDslContext(request)
                .map(ctx -> {
                    Table<?> baseTable = request.getAlias() != null
                            ? DSL.table(DSL.name(request.getTable())).as(request.getAlias())
                            : DSL.table(DSL.name(request.getTable()));
                    Condition condition = JooqFilterUtils.buildConditionFromSearchList(request.getFilters(), getCachedSchema(request));

                    // Always add validation to avoid accidental DELETE without WHERE
                    if (condition == null || condition == DSL.noCondition()) {
                        throw new IllegalArgumentException("❌ Deletion without filter is not allowed.");
                    }

                    return (long) ctx.deleteFrom(baseTable)
                            .where(condition)
                            .execute();
                });
    }

    public Map<String, Field<?>> getCachedSchema(QueryRequest request) {
        String cacheKey = request.getConfigId() + ":" + request.getTable();

        // Try from cache
        Map<String, Field<?>> cached = schemaCache.getIfPresent(cacheKey);
        if (cached != null) return cached;

        // Get context
        DSLContext ctx = dslContextCache.getIfPresent(request.getConfigId());
        if (ctx == null) throw new IllegalStateException("DSLContext not available for configId: " + request.getConfigId());

        // Resolve table using schema-aware meta inspection
        Table<?> table = DSL.table(DSL.name(request.getTable()));
        Table<?> resolvedTable = ctx.meta().getTables().stream()
                .filter(t -> t.getName().equalsIgnoreCase(request.getTable()))
                .findFirst()
                .orElse(table); // fallback to raw table if not found in meta

        // Collect fields into a map
        Map<String, Field<?>> columnFieldMap = Arrays.stream(resolvedTable.fields())
                .collect(Collectors.toMap(
                        f -> f.getName().toLowerCase(),
                        Function.identity()
                ));

        schemaCache.put(cacheKey, columnFieldMap);
        return columnFieldMap;
    }

    private String generateDirectCacheKey(DirectDatabaseConfig config) {
        if (config.getDbType() == null || config.getHost() == null || config.getPort() == null
                || config.getDatabase() == null || config.getUsername() == null || config.getPassword() == null) {
            throw new IllegalArgumentException("❌ Missing required fields in DirectDatabaseConfig for cache key generation");
        }

        return String.join("::",
                config.getDbType(),
                config.getHost(),
                String.valueOf(config.getPort()),
                config.getDatabase(),
                config.getUsername(),
                config.getPassword(),
                config.getSchema() != null ? config.getSchema() : ""
        );
    }

    private String generateDirectCacheKeySafe(DirectDatabaseConfig config) {
        try {
            return generateDirectCacheKey(config);
        } catch (Exception e) {
            log.warn("⚠️ Skipping cache key generation due to missing fields: {}", e.getMessage());
            return null;
        }
    }

    public Mono<JsonNode> getTableSchema(QueryRequest request) {
        return getDslContext(request)
                .map(ctx -> {
                    Table<?> table = DSL.table(DSL.name(request.getTable()));
                    Table<?> resolvedTable = ctx.meta().getTables().stream()
                            .filter(t -> t.getName().equalsIgnoreCase(request.getTable()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Table not found: " + request.getTable()));

                    ObjectMapper mapper = new ObjectMapper();
                    ObjectNode schemaNode = mapper.createObjectNode();

                    for (Field<?> field : resolvedTable.fields()) {
                        schemaNode.put(field.getName(), field.getDataType().getTypeName());
                    }

                    return schemaNode;
                });
    }


}
