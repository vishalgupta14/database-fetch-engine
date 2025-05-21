## 🔗 Entity JOIN Query Examples (via `/api/entity-query/data`)

Assume the following entity classes:

* `com.platform.engine.model.UserTable`
* `com.platform.engine.model.OrderTable`
* `com.platform.engine.model.PaymentTable`

### 1. INNER JOIN: Orders with User info

```bash
curl -X POST http://localhost:8080/api/entity-query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "com.platform.engine.model.Order",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "joins": [
    {
      "table": "user",
      "joinType": "INNER"
    }
  ]
}'
```

### 2. LEFT JOIN: Orders with optional Payments

```bash
curl -X POST http://localhost:8080/api/entity-query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "com.platform.engine.model.OrderTable",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "joins": [
    {
      "table": "paymentTable",
      "joinType": "LEFT"
    }
  ],
  "selectFields": ["productName", "paymentTable.paymentMethod", "paymentTable.amount"]
}'
```

### 3. JOIN with filters on joined table

```bash
curl -X POST http://localhost:8080/api/entity-query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "com.platform.engine.model.OrderTable",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "joins": [
    {
      "table": "userTable",
      "joinType": "INNER"
    }
  ],
  "filters": [
    {
      "column": "userTable.name",
      "value": "Alice",
      "filterOperator": "EQUALS"
    }
  ],
  "selectFields": ["productName", "userTable.name"]
}'
```

### 4. Multiple JOINs: Orders + Users + Payments

```bash
curl -X POST http://localhost:8080/api/entity-query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "com.platform.engine.model.OrderTable",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "joins": [
    {
      "table": "userTable",
      "joinType": "INNER"
    },
    {
      "table": "paymentTable",
      "joinType": "LEFT"
    }
  ],
  "selectFields": ["productName", "userTable.email", "paymentTable.amount"]
}'
```

### 5. Filter on multiple joins

```bash
curl -X POST http://localhost:8080/api/entity-query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "com.platform.engine.model.OrderTable",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "joins": [
    {
      "table": "userTable",
      "joinType": "INNER"
    },
    {
      "table": "paymentTable",
      "joinType": "LEFT"
    }
  ],
  "filters": [
    {"column": "userTable.name", "value": "Bob", "filterOperator": "EQUALS"},
    {"column": "paymentTable.amount", "value": 50, "filterOperator": "GREATER_THAN"}
  ],
  "selectFields": ["userTable.name", "productName", "paymentTable.amount"]
}'
```

### 6. Nested JOIN: Payments → Order → User

```bash
curl -X POST http://localhost:8080/api/entity-query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "com.platform.engine.model.PaymentTable",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "joins": [
    { "table": "orderTable", "joinType": "LEFT" },
    { "table": "orderTable.userTable", "joinType": "LEFT" }
  ],
  "selectFields": ["amount", "orderTable.productName", "orderTable.userTable.name", "orderTable.userTable.email"]
}'
```
