package com.platform.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.platform.engine.dto.DirectDatabaseConfig;
import com.platform.engine.dto.EntityMetadata;
import com.platform.engine.dto.JoinRequest;
import com.platform.engine.dto.QueryRequest;
import com.platform.engine.filter.CriteriaFilterUtils;
import com.platform.engine.model.DatabaseConfig;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.persistence.Table as JpaTable;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JavaEntityQueryEngine {

    private final DslContextProvider dslContextProvider;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final DatabaseConfigService configService;

    public Flux<JsonNode> fetchData(QueryRequest request) {
        return Flux.defer(() -> {
            try {
                DirectDatabaseConfig config = request.getDirectConfig();
                String configId = request.getConfigId();

                if (config != null || configId != null) {
                    DatabaseConfig dbConfig = config != null
                            ? convert(config)
                            : configService.findById(configId).block();
                    if (dbConfig == null) throw new IllegalArgumentException("Database config not found for ID: " + configId);
                    DirectDatabaseConfig resolvedConfig = convert(dbConfig);
                    Connection connection = createConnection(resolvedConfig);
                }

                Class<?> entityClass = Class.forName(request.getTable());
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaQuery<?> cq = cb.createQuery(entityClass);
                Root<?> root = cq.from(entityClass);

                Map<String, Join<?, ?>> joins = new HashMap<>();
                if (request.getJoins() != null) {
                    for (JoinRequest joinRequest : request.getJoins()) {
                        JoinType joinType = switch (joinRequest.getJoinType().toUpperCase()) {
                            case "LEFT" -> JoinType.LEFT;
                            case "RIGHT" -> JoinType.RIGHT;
                            case "INNER" -> JoinType.INNER;
                            default -> throw new IllegalArgumentException("Unsupported join type: " + joinRequest.getJoinType());
                        };
                        String joinField = joinRequest.getTable();
                        Join<?, ?> join = root.join(joinField, joinType);
                        joins.put(joinField, join);
                    }
                }

                Map<String, Field> columnFieldMap = Arrays.stream(entityClass.getDeclaredFields())
                        .collect(Collectors.toMap(Field::getName, f -> f));

                Predicate predicate = CriteriaFilterUtils.buildCombinedPredicate(cb, root, joins, request.getFilters(), columnFieldMap);
                if (predicate != null) {
                    cq.where(predicate);
                }

                if (request.getSelectFields() != null && !request.getSelectFields().isEmpty()) {
                    List<Selection<?>> selections = request.getSelectFields().stream()
                            .map(field -> field.contains(".") ? joins.get(field.split("\\.")[0]).get(field.split("\\.")[1]) : root.get(field))
                            .collect(Collectors.toList());
                    cq.multiselect(selections);
                } else {
                    cq.select(root);
                }

                if (request.getOrderBy() != null) {
                    Path<?> orderPath = request.getOrderBy().contains(".")
                            ? joins.get(request.getOrderBy().split("\\.")[0]).get(request.getOrderBy().split("\\.")[1])
                            : root.get(request.getOrderBy());
                    cq.orderBy("DESC".equalsIgnoreCase(String.valueOf(request.getOrderDirection()))
                            ? cb.desc(orderPath)
                            : cb.asc(orderPath));
                }

                TypedQuery<?> query = entityManager.createQuery(cq);
                if (request.getOffset() != null) query.setFirstResult(request.getOffset());
                if (request.getLimit() != null) query.setMaxResults(request.getLimit());

                List<?> resultList = query.getResultList();
                ObjectWriter writer = request.isPretty() ? mapper.writerWithDefaultPrettyPrinter() : mapper.writer();

                return Flux.fromIterable(resultList).map(entity -> {
                    try {
                        return mapper.readTree(writer.writeValueAsString(entity));
                    } catch (Exception e) {
                        return mapper.convertValue(entity, JsonNode.class);
                    }
                });

            } catch (ClassNotFoundException e) {
                return Flux.error(new IllegalArgumentException("Invalid entity class: " + request.getTable(), e));
            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }

    public List<EntityMetadata> getMetadata(QueryRequest request) {
        DirectDatabaseConfig config = request.getDirectConfig();
        String configId = request.getConfigId();

        if (config != null || configId != null) {
            DatabaseConfig dbConfig = config != null
                    ? convert(config)
                    : configService.findById(configId).block();
            if (dbConfig == null) throw new IllegalArgumentException("Database config not found for ID: " + configId);
            DirectDatabaseConfig resolvedConfig = convert(dbConfig);
            try (Connection connection = createConnection(resolvedConfig)) {
                // Just testing the connection, actual DSLContext will be fetched from provider
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
            }
        }

        String entityClassName = request.getTable();
        try {
            Class<?> entityClass = Class.forName(entityClassName);
            JpaTable tableAnnotation = entityClass.getAnnotation(JpaTable.class);
            if (tableAnnotation == null || tableAnnotation.name().isBlank()) {
                throw new IllegalArgumentException("Missing or invalid @Table annotation on entity: " + entityClassName);
            }
            String tableName = tableAnnotation.name();

            DSLContext ctx = dslContextProvider.getDslContext(request);
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

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Invalid class: " + entityClassName, e);
        }
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

    public Connection createConnection(DirectDatabaseConfig config) throws Exception {
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

    private DatabaseConfig convert(DirectDatabaseConfig directConfig) {
        DatabaseConfig config = new DatabaseConfig();
        config.setDbType(directConfig.getDbType());
        config.setHost(directConfig.getHost());
        config.setPort(directConfig.getPort());
        config.setDatabase(directConfig.getDatabase());
        config.setUsername(directConfig.getUsername());
        config.setPassword(directConfig.getPassword());
        config.setSchema(directConfig.getSchema());
        config.setName("temporary-direct-config"); // Optional placeholder name
        return config;
    }

    // ... existing imports and annotations remain unchanged

    public Mono<Long> fetchCount(QueryRequest request) {
        return Mono.fromCallable(() -> {
            DirectDatabaseConfig config = request.getDirectConfig();
            String configId = request.getConfigId();

            if (config != null || configId != null) {
                DatabaseConfig dbConfig = config != null
                        ? convert(config)
                        : configService.findById(configId).block();
                if (dbConfig == null) throw new IllegalArgumentException("Database config not found for ID: " + configId);
                DirectDatabaseConfig resolvedConfig = convert(dbConfig);
                try (Connection connection = createConnection(resolvedConfig)) {
                    // Just testing the connection
                } catch (Exception e) {
                    throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
                }
            }

            Class<?> entityClass = Class.forName(request.getTable());
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            Root<?> root = cq.from(entityClass);

            Map<String, Join<?, ?>> joins = new HashMap<>();
            if (request.getJoins() != null) {
                for (JoinRequest joinRequest : request.getJoins()) {
                    JoinType joinType = switch (joinRequest.getJoinType().toUpperCase()) {
                        case "LEFT" -> JoinType.LEFT;
                        case "RIGHT" -> JoinType.RIGHT;
                        case "INNER" -> JoinType.INNER;
                        default -> throw new IllegalArgumentException("Unsupported join type: " + joinRequest.getJoinType());
                    };
                    String joinField = joinRequest.getTable();
                    Join<?, ?> join = root.join(joinField, joinType);
                    joins.put(joinField, join);
                }
            }

            Map<String, Field> columnFieldMap = Arrays.stream(entityClass.getDeclaredFields())
                    .collect(Collectors.toMap(Field::getName, f -> f));

            Predicate predicate = CriteriaFilterUtils.buildCombinedPredicate(cb, root, joins, request.getFilters(), columnFieldMap);
            if (predicate != null) {
                cq.where(predicate);
            }

            cq.select(cb.count(root));
            return entityManager.createQuery(cq).getSingleResult();
        });
    }

    public Mono<Long> deleteData(QueryRequest request) {
        return Mono.fromCallable(() -> {
            DirectDatabaseConfig config = request.getDirectConfig();
            String configId = request.getConfigId();

            if (config != null || configId != null) {
                DatabaseConfig dbConfig = config != null
                        ? convert(config)
                        : configService.findById(configId).block();
                if (dbConfig == null) throw new IllegalArgumentException("Database config not found for ID: " + configId);
                DirectDatabaseConfig resolvedConfig = convert(dbConfig);
                try (Connection connection = createConnection(resolvedConfig)) {
                    // Just testing the connection
                } catch (Exception e) {
                    throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
                }
            }

            Class<?> entityClass = Class.forName(request.getTable());
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaDelete<?> delete = cb.createCriteriaDelete(entityClass);
            Root<?> root = delete.from(entityClass);

            Map<String, Join<?, ?>> joins = new HashMap<>();
            if (request.getJoins() != null) {
                for (JoinRequest joinRequest : request.getJoins()) {
                    JoinType joinType = switch (joinRequest.getJoinType().toUpperCase()) {
                        case "LEFT" -> JoinType.LEFT;
                        case "RIGHT" -> JoinType.RIGHT;
                        case "INNER" -> JoinType.INNER;
                        default -> throw new IllegalArgumentException("Unsupported join type: " + joinRequest.getJoinType());
                    };
                    String joinField = joinRequest.getTable();
                    Join<?, ?> join = root.join(joinField, joinType);
                    joins.put(joinField, join);
                }
            }

            Map<String, Field> columnFieldMap = Arrays.stream(entityClass.getDeclaredFields())
                    .collect(Collectors.toMap(Field::getName, f -> f));

            Predicate predicate = CriteriaFilterUtils.buildCombinedPredicate(cb, root, joins, request.getFilters(), columnFieldMap);

            if (predicate == null) {
                throw new IllegalArgumentException("❌ Deletion without filter is not allowed.");
            }

            delete.where(predicate);
            return (long) entityManager.createQuery(delete).executeUpdate();
        });
    }

}
