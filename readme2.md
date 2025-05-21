### 🔄 JOIN Query Examples (via `/api/entity-query/data`)

Assuming entity classes:

```java
package com.platform.engine.model;

@Entity
@Table(name = "user_table", schema = "public")
public class UserTable { ... }

@Entity
@Table(name = "order_table", schema = "public")
public class OrderTable { ... }

@Entity
@Table(name = "payment_table", schema = "public")
public class PaymentTable { ... }
```

Use full class paths like:

```json
"table": "com.platform.engine.model.UserTable"
```

---

### 1️⃣ Fetch All Orders with User Info (INNER JOIN)

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
    { "table": "user", "joinType": "INNER" }
  ],
  "selectFields": ["productName", "user.name", "user.email"]
}'
```

### 2️⃣ Get Payments with Order & User Info (LEFT JOIN)

```bash
curl -X POST http://localhost:8080/api/entity-query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "com.platform.engine.model.Payment",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "joins": [
    { "table": "order", "joinType": "LEFT" },
    { "table": "order.user", "joinType": "LEFT" }
  ],
  "selectFields": ["amount", "order.productName", "order.user.name", "order.user.email"]
}'
```

### 3️⃣ Filter Orders by User Name

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
    { "table": "user", "joinType": "INNER" }
  ],
  "filters": [
    { "column": "user.name", "value": "Alice", "filterOperator": "EQUALS" }
  ],
  "selectFields": ["productName", "user.email"]
}'
```

### 4️⃣ Filter Payments > 500 AND User Email contains `example`

```bash
curl -X POST http://localhost:8080/api/entity-query/data \
-H "Content-Type: application/json" \
-H "Accept: application/x-ndjson" \
-d '{
  "table": "com.platform.engine.model.Payment",
  "directConfig": {
    "dbType": "POSTGRES",
    "host": "localhost",
    "port": 5432,
    "username": "postgres",
    "password": "postgres",
    "database": "postgres"
  },
  "joins": [
    { "table": "order", "joinType": "INNER" },
    { "table": "order.user", "joinType": "INNER" }
  ],
  "filters": [
    { "column": "amount", "value": 500, "filterOperator": "GREATER_THAN" },
    { "column": "order.user.email", "value": "example", "filterOperator": "LIKE" }
  ],
  "selectFields": ["order.user.name", "amount", "order.productName"]
}'
```

Let me know if you'd like COUNT or DELETE variants too. ✅
