# Dynamic Query Engine – Sample Playground with `test_data_all_types`

🎯 Purpose of This Library

In my professional journey, I've observed a recurring challenge in enterprise software development: the persistence layer often becomes bloated with repetitive, verbose, and complex boilerplate code. Developers frequently write and maintain custom query logic for even simple filter and join operations — leading to code that is not only redundant but also difficult to maintain, debug, and scale.

The core objective of building this library is to simplify and streamline query logic, empowering developers to express rich filtering and joining behavior through a single, consistent, and declarative API. This significantly reduces the amount of code written in both the persistence and service layers, allowing teams to focus more on business logic rather than the intricacies of query construction.

✅ Highlights:

🔍 You only need a filter method to perform dynamic filtering and data retrieval.

🔄 Works for both select and delete operations.

🧩 Entity-agnostic: supports both JPA entity-based and raw SQL table-based querying.

🧼 Eliminates verbose DAO methods — no more findByNameAndStatusAndType() nonsense.

📐 Clean separation of concerns: filters live in your request model, not your DAO implementation.

🧠 Built to be intuitive, composable, and easy to learn — yet powerful enough to support advanced scenarios like complex joins, nested conditions, and pagination.

This library is the result of real-world pain points — crafted not for academic interest but to serve real backend engineers who want a cleaner, more expressive way to build data access logic that just works.

This document showcases how to test all supported filter scenarios against a sample PostgreSQL table `test_data_all_types` using a Spring WebFlux + JOOQ-based query engine.

## 📂 Table Schema

```sql
CREATE TABLE public.test_data_all_types (
  id serial4 NOT NULL,
  int_col int4 NULL,
  bigint_col int8 NULL,
  decimal_col numeric(10, 2) NULL,
  double_col float8 NULL,
  bool_col bool NULL,
  date_col date NULL,
  time_col time NULL,
  timestamp_col timestamp NULL,
  char_col bpchar(10) NULL,
  varchar_col varchar(255) NULL,
  text_col text NULL,
  uuid_col uuid NULL,
  json_col jsonb NULL,
  string_int text NULL,
  string_bigint text NULL,
  string_decimal text NULL,
  string_double text NULL,
  string_boolean text NULL,
  string_date text NULL,
  string_time text NULL,
  string_timestamp text NULL,
  string_char text NULL,
  string_varchar text NULL,
  string_uuid text NULL,
  string_json text NULL,
  CONSTRAINT test_data_all_types_pkey PRIMARY KEY (id)
);
```
### 🧪 Seed Data Insert SQL
```sql
INSERT INTO public.test_data_all_types (
  int_col, bigint_col, decimal_col, double_col, bool_col, date_col, time_col,
  timestamp_col, char_col, varchar_col, text_col, uuid_col, json_col,
  string_int, string_bigint, string_decimal, string_double, string_boolean,
  string_date, string_time, string_timestamp, string_char, string_varchar,
  string_uuid, string_json
) VALUES
  (42,9999999999,123.45,67.89,true,'2024-01-01','14:00:00','2024-01-01 14:00:00','A         ','sample text','hello world','a8098c1a-f86e-11da-bd1a-00112444be1e','{"key": "value"}','42','9999999999','123.45','67.89','true','2024-01-01','14:00:00','2024-01-01T14:00:00','A','sample text','a8098c1a-f86e-11da-bd1a-00112444be1e','{"key": "value"}'),
  (-1,-999999,0.00,0.0,false,'2023-12-31','00:00:00','2023-12-31 00:00:00','B         ','data','another row','123e4567-e89b-12d3-a456-426614174000','{"foo": 42}','-1','-999999','0.00','0.0','false','2023-12-31','00:00:00','2023-12-31T00:00:00','B','data','123e4567-e89b-12d3-a456-426614174000','{"foo": 42}'),
  (NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),
  (0,0,-123.45,-0.01,false,'2000-01-01','12:34:56','2000-01-01 12:34:56','Z         ','edge','final test','00000000-0000-0000-0000-000000000000','{"null": null}','50','0','-123.45','-0.01','false','2000-01-01','12:34:56','2000-01-01T12:34:56','Z','edge','00000000-0000-0000-0000-000000000000','{"null": null}'),
  (100,1234567890,9999.99,179769313486231570000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000,true,'2025-05-16','23:59:59','2025-05-16 23:59:59','X         ','long text','line three','9b2e1c3e-8f5f-4d8d-b1e4-45c0bfbf70f3','{"active": true}','200','1234567890','9999.99','1.7976931348623157E+308','true','2025-05-16','23:59:59','2025-05-16T23:59:59','X','long text','9b2e1c3e-8f5f-4d8d-b1e4-45c0bfbf70f3','{"active": true}');
```

## 🌐 Base API Endpoint

```
POST http://localhost:8080/api/query/data
Accept: application/x-ndjson
Content-Type: application/json
```

Use either:

* `configId` for MongoDB-based config resolution
* OR `directConfig` for direct DB access inline

---

## ✅ Sample Filters for Native Columns

### Decimal Column

```bash
"column": "decimal_col", "value": 123.45, "filterOperator": "EQUALS"
```

### Boolean Column

```bash
"column": "bool_col", "value": true
```

### Date Column

```bash
"column": "date_col", "value": "2024-01-01"
```

### Timestamp Column

```bash
"column": "timestamp_col", "value": "2024-01-01T14:00:00"
```

### Char/Varchar/Text/UUID/JSONB

```bash
"char_col": "A         "
"varchar_col": "sample text"
"uuid_col": "a8098c1a-f86e-11da-bd1a-00112444be1e"
"json_col": "{\"key\":\"value\"}"
```

---

## 🤖 Filter Examples on String Columns with `castType`

### Numeric String Columns

```bash
"column": "string_int", "value": 42, "castType": "INTEGER"
"column": "string_decimal", "value": "123.45", "castType": "DECIMAL"
```

### Boolean String Columns

```bash
"column": "string_boolean", "value": "true", "castType": "BOOLEAN"
```

### Date/Time String Columns

```bash
"column": "string_date", "value": "16-05-2025", "castType": "DATE", "castFormat": "dd-MM-yyyy"
"column": "string_timestamp", "value": "2025-05-16T14:00:00", "castType": "TIMESTAMP"
```

---

## 🔢 Advanced Operators

* `EQUALS`
* `NOT_EQUALS`
* `GREATER_THAN`
* `LESS_THAN`
* `BETWEEN`
* `LIKE`
* `IN`
* `NOT_IN`

```json
{
  "column": "string_int",
  "value": 100,
  "filterOperator": "GREATER_THAN"
}
```

---

## 📅 Pagination, Sorting, and Projection

```json
{
  "limit": 10,
  "offset": 0,
  "orderBy": "int_col",
  "orderDirection": "DESC",
  "selectFields": ["int_col", "string_date", "string_boolean"]
}
```

---

## 🌐 Using Direct DB Configuration

```json
"directConfig": {
  "dbType": "POSTGRES",
  "host": "localhost",
  "port": 5432,
  "username": "postgres",
  "password": "postgres",
  "database": "postgres"
}
```

---

## 📘 QueryRequest JSON Schema

```json
{
  "configId": "<optional config ID from MongoDB>",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres",
    "schema": "<optional schema>"
  },
  "table": "test_data_all_types",
  "selectFields": ["col1", "col2"],
  "filters": [
    {
      "column": "string_decimal",
      "value": "123.45",
      "castType": "DECIMAL",
      "castFormat": "<optional pattern for DATE/TIMESTAMP>",
      "filterOperator": "EQUALS",
      "logicalOperator": "AND"
    }
  ],
  "limit": 50,
  "offset": 0,
  "orderBy": "int_col",
  "orderDirection": "DESC",
  "distinct": false,
  "pretty": true
}
```

---

## ✅ Example: Using `configId`

```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "configId": "68274da269b04171ef068375",
    "table": "test_data_all_types",
    "filters": [
      {
        "column": "string_decimal",
        "value": "123.45",
        "castType": "DECIMAL",
        "filterOperator": "EQUALS"
      },
      {
        "column": "string_date",
        "value": "2024-01-01",
        "castType": "DATE",
        "castFormat": "yyyy-MM-dd",
        "filterOperator": "EQUALS"
      }
    ],
    "selectFields": ["string_date", "string_decimal"],
    "pretty": true
  }'
```

## ✅ Example: Using `directConfig`

```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "table": "test_data_all_types",
    "filters": [
      {
        "column": "string_decimal",
        "value": "123.45",
        "castType": "DECIMAL",
        "filterOperator": "EQUALS"
      },
      {
        "column": "string_date",
        "value": "2024-01-01",
        "castType": "DATE",
        "castFormat": "yyyy-MM-dd",
        "filterOperator": "EQUALS"
      }
    ],
    "selectFields": ["string_date", "string_decimal"],
    "pretty": true
  }'
```

## 🔎 Supported `castType`

| Type        | Format Pattern Example    |
| ----------- | ------------------------- |
| `INTEGER`   | "42"                      |
| `BIGINT`    | "9999999999"              |
| `DECIMAL`   | "123.45"                  |
| `DOUBLE`    | "3.14"                    |
| `BOOLEAN`   | "true"                    |
| `DATE`      | "yyyy-MM-dd"              |
| `TIME`      | "HH\:mm\:ss"              |
| `TIMESTAMP` | "yyyy-MM-dd'T'HH\:mm\:ss" |
| `UUID`      | "uuid-string"             |
| `JSON`      | "{"key":"value"}"         |

---

## 🔍 Sample Filter Operators

* `EQUALS`
* `NOT_EQUALS`
* `GREATER_THAN`
* `LESS_THAN`
* `GREATER_THAN_EQUAL`
* `LESS_THAN_EQUAL`
* `LIKE`
* `IN`
* `NOT_IN`
* `BETWEEN`
* `AND`, `OR` (for chaining logical operators)

---

## 🧪 Testing Strategy

* ✅ Covers all PostgreSQL types
* ✅ Validates both typed and string-casted values
* ✅ Allows override via `castType` and `castFormat`
* ✅ Caffeine cache prevents repeated DB schema discovery

---

## 📌 Tips

* Use `pretty=true` to return formatted JSON
* Provide at least one filter for delete operations
* All results are streamed as **NDJSON** to handle large datasets

---

## 📦 Planned Enhancements

* Pagination support with cursor
* Export as CSV or Parquet
* Join support across tables
* Aggregation and grouping

---


## 🎉 Contributing Test Cases

To add more test cases, simply:

1. Copy a `curl` block
2. Change the `column`, `value`, `castType`, or `filterOperator`
3. Verify result via `pretty: true` output

---


## 🔍 50+ Query Examples for All Supported Scenarios

#### 1. `decimal_col = 123.45`
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "decimal_col", "value": 123.45, "filterOperator": "EQUALS" }
  ]
}'
```

#### 2. `bool_col = true`
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "bool_col", "value": true, "filterOperator": "EQUALS" }
  ]
}'
```

#### 3. `date_col = '2024-01-01'`
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "date_col", "value": "2024-01-01", "filterOperator": "EQUALS" }
  ]
}'
```

#### 4. `time_col = '14:00:00'`
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "time_col", "value": "14:00:00", "filterOperator": "EQUALS" }
  ]
}'
```

#### 5. `timestamp_col = '2024-01-01T14:00:00'`
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "timestamp_col", "value": "2024-01-01T14:00:00", "filterOperator": "EQUALS" }
  ]
}'
```

#### 6. `char_col = 'A         '` (CHAR padding)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "char_col", "value": "A         ", "filterOperator": "EQUALS" }
  ]
}'
```

#### 7. `varchar_col = 'sample text'`
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "varchar_col", "value": "sample text", "filterOperator": "EQUALS" }
  ]
}'
```

#### 8. `text_col = 'hello world'`
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "text_col", "value": "hello world", "filterOperator": "EQUALS" }
  ]
}'
```

#### 9. `uuid_col = 'a8098c1a-f86e-11da-bd1a-00112444be1e'`
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "uuid_col", "value": "a8098c1a-f86e-11da-bd1a-00112444be1e", "filterOperator": "EQUALS" }
  ]
}'
```

#### 10. JSONB column match
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    { "column": "json_col", "value": "{\"key\": \"value\"}", "filterOperator": "EQUALS" }
  ]
}'
```

### 11. Greater than filter: int_col > 40
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "int_col",
      "value": 40,
      "filterOperator": "GREATER_THAN"
    }
  ]
}'
```

### 12. Less than filter: bigint_col < 1234567890
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "bigint_col",
      "value": 1234567890,
      "filterOperator": "LESS_THAN"
    }
  ]
}'
```

### 13. LIKE filter: text_col LIKE '%test%'
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "text_col",
      "value": "test",
      "filterOperator": "LIKE"
    }
  ]
}'
```

### 14. IN filter: int_col IN [42, 100]
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "int_col",
      "value": [42, 100],
      "filterOperator": "IN"
    }
  ]
}'
```

### 15. NOT IN filter: int_col NOT IN [-1, 0]
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "int_col",
      "value": [-1, 0],
      "filterOperator": "NOT_IN"
    }
  ]
}'
```

### 16. BETWEEN filter: decimal_col BETWEEN 0.0 and 500.0
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "decimal_col",
      "value": [0.0, 500.0],
      "filterOperator": "BETWEEN"
    }
  ]
}'
```

### 17. Projection only (selectFields)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "selectFields": ["int_col", "decimal_col"],
  "limit": 10
}'
```

### 18. Offset and limit pagination
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "limit": 5,
  "offset": 5,
  "orderBy": "id",
  "orderDirection": "ASC"
}'
```

### 19. Logical operator: OR between filters
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "int_col",
      "value": 42,
      "filterOperator": "EQUALS",
      "logicalOperator": "OR"
    },
    {
      "column": "int_col",
      "value": 100,
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 20. Boolean false match
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "bool_col",
      "value": false,
      "filterOperator": "EQUALS"
    }
  ]
}'
```
### 21. Match string_int = 50 (string column, casted to int)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_int",
      "value": 50,
      "castType": "INTEGER",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 22. Match string_bigint = 1234567890
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_bigint",
      "value": 1234567890,
      "castType": "BIGINT",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 23. Match string_double > 67.0
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_double",
      "value": 67.0,
      "castType": "DOUBLE",
      "filterOperator": "GREATER_THAN"
    }
  ]
}'
```

### 24. Match string_boolean = false
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_boolean",
      "value": false,
      "castType": "BOOLEAN",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 25. Match string_date = 2000-01-01
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_date",
      "value": "2000-01-01",
      "castType": "DATE",
      "castFormat": "yyyy-MM-dd",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 26. Match string_time = '12:34:56'
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_time",
      "value": "12:34:56",
      "castType": "TIME",
      "castFormat": "HH:mm:ss",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 27. Match string_timestamp = '2025-05-16T23:59:59'
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_timestamp",
      "value": "2025-05-1623:59:59",
      "castType": "TIMESTAMP",
      "castFormat": "yyyy-MM-ddHH:mm:ss",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 28. Match string_uuid = '123e4567-e89b-12d3-a456-426614174000'
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_uuid",
      "value": "123e4567-e89b-12d3-a456-426614174000",
      "castType": "UUID",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 29. Match string_char = 'Z'
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_char",
      "value": "Z",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 30. Match string_json = '{"null": null}'
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_json",
      "value": "{\"null\": null}",
      "castType": "JSONB",
      "filterOperator": "EQUALS"
    }
  ]
}'
```
### ✅ 31. Filter: string_uuid = "a8098c1a-f86e-11da-bd1a-00112444be1e"
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "string_uuid",
        "value": "a8098c1a-f86e-11da-bd1a-00112444be1e",
        "filterOperator": "EQUALS"
      }
    ]
  }'
```

### ✅ 32. Filter: string_json = {"key":"value"} (as raw string)
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "string_json",
        "value": "{\"key\":\"value\"}",
        "filterOperator": "EQUALS"
      }
    ]
  }'
```

### ✅ 33. string_int GREATER_THAN = 25
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "string_int",
        "value": 25,
        "filterOperator": "GREATER_THAN"
      }
    ]
  }'
```

### ✅ 34. string_bigint LESS_THAN = 1234567890
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "string_bigint",
        "value": 1234567890,
        "filterOperator": "LESS_THAN"
      }
    ]
  }'
```

### ✅ 35. Filter: string_date = '16-05-2025' with castFormat
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "string_date",
        "value": "16-05-2025",
        "filterOperator": "EQUALS",
        "castType": "DATE",
        "castFormat": "dd-MM-yyyy"
      }
    ]
  }'
```

### ✅ 36. Filter: string_timestamp = "2024-01-01T14:00:00"
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "string_timestamp",
        "value": "2024-01-01T14:00:00",
        "filterOperator": "EQUALS",
        "castType": "TIMESTAMP"
      }
    ]
  }'
```

### ✅ 37. Filter: string_double GREATER_THAN 2.5
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "string_double",
        "value": 2.5,
        "filterOperator": "GREATER_THAN",
        "castType": "DOUBLE"
      }
    ]
  }'
```

### ✅ 38. Filter: string_boolean = true
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "string_boolean",
        "value": true,
        "filterOperator": "EQUALS",
        "castType": "BOOLEAN"
      }
    ]
  }'
```

### ✅ 39. Filter: string_time = "14:00:00"
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "string_time",
        "value": "14:00:00",
        "filterOperator": "EQUALS",
        "castType": "TIME"
      }
    ]
  }'
```

### ✅ 40. string_col with LIKE operator
```bash
curl -X POST http://localhost:8080/api/query/data \
  -H "Content-Type: application/json" \
  -H "Accept: application/x-ndjson" \
  -d '{
    "table": "test_data_all_types",
    "directConfig": {
      "dbType": "POSTGRES",
      "host": "localhost",
      "port": 5432,
      "username": "postgres",
      "password": "postgres",
      "database": "postgres"
    },
    "filters": [
      {
        "column": "varchar_col",
        "value": "text",
        "filterOperator": "LIKE"
      }
    ]
  }'
```
### 41. Match string_boolean = "true" (cast to BOOLEAN)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_boolean",
      "value": "true",
      "castType": "BOOLEAN",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 42. Match string_date = "2024-01-01" (cast to DATE)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_date",
      "value": "2024-01-01",
      "castType": "DATE",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 43. Match string_time = "14:00:00" (cast to TIME)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_time",
      "value": "14:00:00",
      "castType": "TIME",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 44. Match string_timestamp = "2024-01-01T14:00:00" (cast to TIMESTAMP)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_timestamp",
      "value": "2024-01-01T14:00:00",
      "castType": "TIMESTAMP",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 45. Match string_char = "A" (cast to CHAR)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_char",
      "value": "A",
      "castType": "CHAR",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 46. Match string_varchar = "sample text" (cast to VARCHAR)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_varchar",
      "value": "sample text",
      "castType": "VARCHAR",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 47. Match string_uuid = "a8098c1a-f86e-11da-bd1a-00112444be1e" (cast to UUID)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_uuid",
      "value": "a8098c1a-f86e-11da-bd1a-00112444be1e",
      "castType": "UUID",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 48. Match string_json = {"key":"value"} (cast to JSON)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_json",
      "value": "{\"key\":\"value\"}",
      "castType": "JSONB",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

### 49. Range filter: decimal_col > 100
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "decimal_col",
      "value": 100,
      "filterOperator": "GREATER_THAN"
    }
  ]
}'
```

### 50. Range filter: string_decimal < 500 (cast to DECIMAL)
```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "test_data_all_types",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "filters": [
    {
      "column": "string_decimal",
      "value": "500",
      "castType": "DECIMAL",
      "filterOperator": "LESS_THAN"
    }
  ]
}'
```
--------------------
## 📂 JOIN Support for Database Schema
---------------------

```sql
CREATE TABLE public.user_table (
  id SERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.order_table (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL,
  product_name VARCHAR(100),
  quantity INT,
  price DECIMAL(10, 2),
  order_date DATE,
  FOREIGN KEY (user_id) REFERENCES public.user_table(id)
);

CREATE TABLE public.payment_table (
  id SERIAL PRIMARY KEY,
  order_id INT REFERENCES order_table(id),
  amount DECIMAL(10, 2),
  payment_method VARCHAR(50),
  paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 🧪 Seed Data Insert SQL
```sql
-- 👤 Users
INSERT INTO public.user_table (name, email) VALUES
  ('Alice', 'alice@example.com'),
  ('Bob', 'bob@example.com'),
  ('Charlie', 'charlie@example.com');

-- 📦 Orders
INSERT INTO public.order_table (user_id, product_name, quantity, price, order_date) VALUES
  (1, 'Laptop', 1, 999.99, '2024-05-10'),
  (1, 'Mouse', 2, 25.50, '2024-05-11'),
  (2, 'Keyboard', 1, 45.00, '2024-05-12');
  
-- 💳 Payments
INSERT INTO payment_table (order_id, amount, payment_method) VALUES
(1, 999.99, 'Credit Card'),
(2, 51.00, 'UPI');
```

## 🔗 API Join Examples

All examples demonstrate how to use the `/api/query/data` endpoint with SQL-style joins using `table`, `alias`, and `joins` syntax.

> ✅ All examples include a `directConfig` block for PostgreSQL.

---

### 1. 🔹 INNER JOIN: All users with orders

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ]
}'
```

---

### 2. 🔹 LEFT JOIN: All users with optional orders

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "LEFT",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ]
}'
```

---

### 3. 🔹 RIGHT JOIN: All orders with optional users

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "order_table",
  "alias": "o",
  "joins": [
    {
      "joinType": "RIGHT",
      "table": "user_table",
      "alias": "u",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ]
}'
```

---

### 4. 🔹 INNER JOIN with filter on join table

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    { "column": "o.price", "value": 500, "filterOperator": "GREATER_THAN" }
  ]
}'
```

---

### 5. 🔹 Multiple JOINs: Orders and payment info

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    },
    {
      "joinType": "LEFT",
      "table": "payment_table",
      "alias": "p",
      "onLeft": ["o.id"],
      "onRight": ["p.order_id"]
    }
  ]
}'
```
### 6. 🔹 JOIN with sorting

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "orderBy": "o.price",
  "orderDirection": "DESC",
  "limit": 10
}'
```

---

### 7. 🔹 JOIN with pagination (limit + offset)

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "limit": 5,
  "offset": 10
}'
```

---

### 8. 🔹 JOIN + BETWEEN filter

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "o.price",
      "value": [100, 1000],
      "filterOperator": "BETWEEN"
    }
  ]
}'
```

---

### 9. 🔹 JOIN with LIKE search

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "o.product_name",
      "value": "Lap",
      "filterOperator": "LIKE"
    }
  ]
}'
```

---

### 10. 🔹 JOIN with multiple conditions

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    { "column": "u.name", "value": "Ali", "filterOperator": "LIKE" },
    { "column": "o.price", "value": 50, "filterOperator": "GREATER_THAN_EQUAL" }
  ]
}'
```

---

### 11. 🔹 JOIN with NOT IN filter

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "o.product_name",
      "value": ["Mouse", "Keyboard"],
      "filterOperator": "NOT_IN"
    }
  ]
}'
```

---

### 12. 🔹 JOIN with IS NULL filter

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "LEFT",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "o.id",
      "value": null,
      "filterOperator": "EQUALS"
    }
  ]
}'
```

---

### 13. 🔹 JOIN with NOT\_EQUALS condition

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "o.product_name",
      "value": "Mouse",
      "filterOperator": "NOT_EQUALS"
    }
  ]
}'
```

---

### 14. 🔹 JOIN with GREATER\_THAN on created\_at

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "u.created_at",
      "value": "2025-01-01T00:00:00",
      "castType": "TIMESTAMP",
      "castFormat": "yyyy-MM-dd'T'HH:mm:ss",
      "filterOperator": "GREATER_THAN"
    }
  ]
}'
```

---

### 15. 🔹 JOIN with specific quantity using EQUALS

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "o.quantity",
      "value": 2,
      "filterOperator": "EQUALS"
    }
  ]
}'
```

---

### 16. 🔹 JOIN and filter by date range using BETWEEN

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "o.order_date",
      "value": ["2024-05-10", "2024-05-12"],
      "castType": "DATE",
      "castFormat": "yyyy-MM-dd",
      "filterOperator": "BETWEEN"
    }
  ]
}'
```

---

### 17. 🔹 JOIN with IN filter on product\_name

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "o.product_name",
      "value": ["Mouse", "Laptop"],
      "filterOperator": "IN"
    }
  ]
}'
```

---

### 18. 🔹 JOIN with LIKE filter on user email

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    }
  ],
  "filters": [
    {
      "column": "u.email",
      "value": "@example.com",
      "filterOperator": "LIKE"
    }
  ]
}'
```

---

### 19. 🔹 JOIN with exact match on payment method

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "order_table",
  "alias": "o",
  "joins": [
    {
      "joinType": "LEFT",
      "table": "payment_table",
      "alias": "p",
      "onLeft": ["o.id"],
      "onRight": ["p.order_id"]
    }
  ],
  "filters": [
    {
      "column": "p.payment_method",
      "value": "Credit Card",
      "filterOperator": "EQUALS"
    }
  ]
}'
```

---

### 20. 🔹 JOIN with combined AND conditions on multiple joined tables

```bash
curl -X POST http://localhost:8080/api/query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "table": "user_table",
  "alias": "u",
  "joins": [
    {
      "joinType": "INNER",
      "table": "order_table",
      "alias": "o",
      "onLeft": ["u.id"],
      "onRight": ["o.user_id"]
    },
    {
      "joinType": "LEFT",
      "table": "payment_table",
      "alias": "p",
      "onLeft": ["o.id"],
      "onRight": ["p.order_id"]
    }
  ],
  "filters": [
    { "column": "u.name", "value": "Alice", "filterOperator": "EQUALS" },
    { "column": "p.payment_method", "value": "UPI", "filterOperator": "EQUALS" }
  ]
}'
```

---



## ⚠️ Notes

* `filterOperator` is **required**.
* `castType` and `castFormat` are **optional** unless dealing with string-encoded fields.
* The system auto-detects Java types from schema if `castType` is not provided.

---

For more insights or to raise issues, contact the backend developer team.

Happy querying! 🤖
