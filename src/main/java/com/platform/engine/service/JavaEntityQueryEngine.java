package com.platform.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.platform.engine.dto.DirectDatabaseConfig;
import com.platform.engine.dto.EntityMetadata;
import com.platform.engine.dto.JoinRequest;
import com.platform.engine.dto.QueryRequest;
import com.platform.engine.filter.CriteriaFilterUtils;
import com.platform.engine.model.DatabaseConfig;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JavaEntityQueryEngine {

    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final DatabaseConfigService configService;
    private final DatabaseFetchEngine databaseFetchEngine;

    private final Cache<String, Connection> connectionCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    public Flux<JsonNode> fetchData(QueryRequest request) {
        return Flux.defer(() -> {
            try {
                Class<?> entityClass = Class.forName(request.getTable());
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();

                if (request.getSelectFields() != null && !request.getSelectFields().isEmpty()) {
                    // Case 1: Partial select using Tuple
                    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
                    Root<?> root = cq.from(entityClass);

                    Map<String, Join<?, ?>> joins = applyJoins(cb, root, request.getJoins());

                    List<Selection<?>> selections = request.getSelectFields().stream()
                            .map(f -> f.contains(".")
                                    ? joins.get(f.split("\\.")[0]).get(f.split("\\.")[1]).alias(f)
                                    : root.get(f).alias(f))
                            .collect(Collectors.toList());
                    cq.multiselect(selections);

                    Map<String, Field> columnFieldMap = Arrays.stream(entityClass.getDeclaredFields())
                            .collect(Collectors.toMap(Field::getName, f -> f));

                    Predicate predicate = CriteriaFilterUtils.buildCombinedPredicate(cb, root, joins, request.getFilters(), columnFieldMap);
                    if (predicate != null) cq.where(predicate);

                    if (request.getOrderBy() != null) {
                        Path<?> orderPath = request.getOrderBy().contains(".")
                                ? joins.get(request.getOrderBy().split("\\.")[0]).get(request.getOrderBy().split("\\.")[1])
                                : root.get(request.getOrderBy());
                        cq.orderBy("DESC".equalsIgnoreCase(String.valueOf(request.getOrderDirection())) ? cb.desc(orderPath) : cb.asc(orderPath));
                    }

                    TypedQuery<Tuple> query = entityManager.createQuery(cq);
                    if (request.getOffset() != null) query.setFirstResult(request.getOffset());
                    if (request.getLimit() != null) query.setMaxResults(request.getLimit());

                    List<Tuple> tuples = query.getResultList();
                    return Flux.fromIterable(tuples).map(tuple -> {
                        ObjectNode node = mapper.createObjectNode();
                        for (TupleElement<?> el : tuple.getElements()) {
                            node.putPOJO(el.getAlias(), tuple.get(el.getAlias()));
                        }
                        return node;
                    });

                } else {
                    // Case 2: Full entity
                    CriteriaQuery<Object> cq = (CriteriaQuery<Object>) cb.createQuery(entityClass);
                    Root<Object> root = cq.from((Class<Object>) entityClass);

                    Map<String, Join<?, ?>> joins = applyJoins(cb, root, request.getJoins());
                    Map<String, Field> columnFieldMap = Arrays.stream(entityClass.getDeclaredFields())
                            .collect(Collectors.toMap(Field::getName, f -> f));
                    Predicate predicate = CriteriaFilterUtils.buildCombinedPredicate(cb, root, joins, request.getFilters(), columnFieldMap);
                    if (predicate != null) cq.where(predicate);

                    if (request.getOrderBy() != null) {
                        Path<?> orderPath = request.getOrderBy().contains(".")
                                ? joins.get(request.getOrderBy().split("\\.")[0]).get(request.getOrderBy().split("\\.")[1])
                                : root.get(request.getOrderBy());
                        cq.orderBy("DESC".equalsIgnoreCase(String.valueOf(request.getOrderDirection())) ? cb.desc(orderPath) : cb.asc(orderPath));
                    }

                    cq.select(root);
                    TypedQuery<Object> query = entityManager.createQuery(cq);
                    if (request.getOffset() != null) query.setFirstResult(request.getOffset());
                    if (request.getLimit() != null) query.setMaxResults(request.getLimit());

                    List<Object> results = query.getResultList();
                    return Flux.fromIterable(results)
                            .map(this::buildSafeJson);
                }

            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }

    private JsonNode buildSafeJson(Object entity) {
        return buildSafeJson(entity, new IdentityHashMap<>());
    }

    private JsonNode buildSafeJson(Object entity, Map<Object, Boolean> visited) {
        ObjectNode node = mapper.createObjectNode();
        if (entity == null) return node;

        if (visited.containsKey(entity)) {
            node.put("ref", "already_serialized");
            return node;
        }
        visited.put(entity, true);

        for (Field field : entity.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(entity);
                if (value == null) {
                    node.putNull(field.getName());
                } else if (isPrimitiveOrWrapper(value.getClass())) {
                    node.putPOJO(field.getName(), value);
                } else if (value instanceof Collection<?> col) {
                    ArrayNode array = mapper.createArrayNode();
                    for (Object item : col) {
                        array.add(buildSafeJson(item, visited));
                    }
                    node.set(field.getName(), array);
                } else if (value.getClass().getName().startsWith("java.lang.reflect") ||
                        value.getClass().getName().startsWith("sun.reflect") ||
                        value instanceof Class<?> ||
                        value instanceof java.lang.reflect.AnnotatedType) {
                    continue; // skip non-serializable
                } else {
                    node.set(field.getName(), buildSafeJson(value, visited));
                }
            } catch (Exception ex) {
                node.put(field.getName(), "error: " + ex.getClass().getSimpleName());
            }
        }
        return node;
    }


    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == String.class ||
                Number.class.isAssignableFrom(clazz) ||
                Boolean.class.isAssignableFrom(clazz) ||
                Character.class.isAssignableFrom(clazz) ||
                java.util.Date.class.isAssignableFrom(clazz) ||
                java.time.temporal.Temporal.class.isAssignableFrom(clazz) ||
                java.util.UUID.class.isAssignableFrom(clazz);
    }




    public List<EntityMetadata> getMetadata(QueryRequest request) {
        validateConnection(request);

        try {
            Class<?> entityClass = Class.forName(request.getTable());
            jakarta.persistence.Table tableAnnotation = entityClass.getAnnotation(jakarta.persistence.Table.class);
            if (tableAnnotation == null || tableAnnotation.name().isBlank()) {
                throw new IllegalArgumentException("Missing or invalid @Table annotation on entity: " + request.getTable());
            }
            String tableName = tableAnnotation.name();

            DSLContext ctx = databaseFetchEngine.createDslContext(resolveConfig(request));
            Optional<Table<?>> dbTableOpt = ctx.meta().getTables().stream()
                    .filter(t -> t.getName().equalsIgnoreCase(tableName))
                    .findFirst();

            Table<?> dbTable = dbTableOpt.orElseThrow(() ->
                    new IllegalArgumentException("Table not found in database: " + tableName));

            List<EntityMetadata> metadataList = new ArrayList<>();
            for (org.jooq.Field<?> dbField : dbTable.fields()) {
                String columnName = dbField.getName();
                String columnType = dbField.getDataType().getSQLDataType().getTypeName();
                String fieldName = findFieldName(entityClass, columnName);
                String fieldType = findFieldType(entityClass, fieldName);
                metadataList.add(new EntityMetadata(fieldName, fieldType, columnName, columnType));
            }

            return metadataList;

        } catch (Exception e) {
            throw new RuntimeException("Failed to extract metadata", e);
        }
    }

    public Mono<Long> fetchCount(QueryRequest request) {
        return Mono.fromCallable(() -> {
            validateConnection(request);

            Class<?> entityClass = Class.forName(request.getTable());
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<?> root = cq.from(entityClass);

            Map<String, Join<?, ?>> joins = applyJoins(cb, root, request.getJoins());
            Map<String, Field> columnFieldMap = Arrays.stream(entityClass.getDeclaredFields())
                    .collect(Collectors.toMap(Field::getName, f -> f));

            Predicate predicate = CriteriaFilterUtils.buildCombinedPredicate(cb, root, joins, request.getFilters(), columnFieldMap);
            if (predicate != null) cq.where(predicate);

            cq.select(cb.count(root));
            return entityManager.createQuery(cq).getSingleResult();
        });
    }

    public Mono<Long> deleteData(QueryRequest request) {
        return Mono.fromCallable(() -> {
            validateConnection(request);

            Class<?> entityClass = Class.forName(request.getTable());
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            @SuppressWarnings("unchecked")
            CriteriaDelete<Object> delete = (CriteriaDelete<Object>) cb.createCriteriaDelete(entityClass);
            Root<Object> root = delete.from((Class<Object>) entityClass);

            Map<String, Join<?, ?>> joins = applyJoins(cb, root, request.getJoins());
            Map<String, Field> columnFieldMap = Arrays.stream(entityClass.getDeclaredFields())
                    .collect(Collectors.toMap(Field::getName, f -> f));

            Predicate predicate = CriteriaFilterUtils.buildCombinedPredicate(cb, root, joins, request.getFilters(), columnFieldMap);
            if (predicate == null) throw new IllegalArgumentException("❌ Deletion without filter is not allowed.");

            delete.where(predicate);
            return Long.valueOf(entityManager.createQuery(delete).executeUpdate());
        });
    }


    private String generateCacheKey(DatabaseConfig config) {
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

    private void validateConnection(QueryRequest request) {
        try {
            DatabaseConfig config = resolveConfig(request);
            String key = generateCacheKey(config);
            connectionCache.get(key, k -> {
                try {
                    return createConnection(config);
                } catch (Exception e) {
                    throw new RuntimeException("❌ Failed to cache DB connection: " + e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
        }
    }

    private DatabaseConfig resolveConfig(QueryRequest request) {
        DirectDatabaseConfig direct = request.getDirectConfig();
        String configId = request.getConfigId();

        if (direct != null) {
            return convert(direct);
        } else if (configId != null) {
            DatabaseConfig dbConfig = configService.findById(configId).block();
            if (dbConfig == null) throw new IllegalArgumentException("Database config not found for ID: " + configId);
            return dbConfig;
        } else {
            throw new IllegalArgumentException("No database configuration provided.");
        }
    }

    private DatabaseConfig convert(DirectDatabaseConfig directConfig) {
        DatabaseConfig config = new DatabaseConfig();
        config.setDbType(directConfig.getDbType());
        config.setHost(directConfig.getHost());
        config.setPort(directConfig.getPort());
        config.setDatabase(directConfig.getDatabase());
        config.setUsername(directConfig.getUsername());
        config.setPassword(directConfig.getPassword());
        config.setSchema(directConfig.getSchema());
        config.setName("temporary-direct-config");
        return config;
    }

    public Connection createConnection(DatabaseConfig config) throws Exception {
        String jdbcUrl = switch (config.getDbType().toUpperCase()) {
            case "POSTGRES" -> {
                Class.forName("org.postgresql.Driver");
                yield String.format("jdbc:postgresql://%s:%d/%s", config.getHost(), config.getPort(), config.getDatabase());
            }
            case "MYSQL" -> {
                Class.forName("com.mysql.cj.jdbc.Driver");
                yield String.format("jdbc:mysql://%s:%d/%s", config.getHost(), config.getPort(), config.getDatabase());
            }
            default -> throw new IllegalArgumentException("Unsupported DB type: " + config.getDbType());
        };

        return DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword());
    }

    private Map<String, Join<?, ?>> applyJoins(CriteriaBuilder cb, Root<?> root, List<JoinRequest> joins) {
        Map<String, Join<?, ?>> joinMap = new HashMap<>();
        if (joins != null) {
            for (JoinRequest joinRequest : joins) {
                JoinType joinType = switch (joinRequest.getJoinType().toUpperCase()) {
                    case "LEFT" -> JoinType.LEFT;
                    case "RIGHT" -> JoinType.RIGHT;
                    case "INNER" -> JoinType.INNER;
                    default -> throw new IllegalArgumentException("Unsupported join type: " + joinRequest.getJoinType());
                };
                Join<?, ?> join = root.join(joinRequest.getTable(), joinType);
                joinMap.put(joinRequest.getTable(), join);
            }
        }
        return joinMap;
    }

    private String findFieldName(Class<?> clazz, String columnName) {
        for (Field field : clazz.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null && column.name().equalsIgnoreCase(columnName)) {
                return field.getName();
            }
            if (field.getName().equalsIgnoreCase(columnName)) {
                return field.getName();
            }
        }
        return columnName;
    }

    private String findFieldType(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return field.getType().getSimpleName();
        } catch (NoSuchFieldException e) {
            return "Unknown";
        }
    }




}
