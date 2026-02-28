---
description: How to use the GriefLogger mini ORM for all database operations
---

# Database ORM Usage Guide

**RULE: Never use raw SQL strings or `database.prepareStatement()` directly in repositories. Always use the `Query.*` builders.**

## Entry Point

All queries start from `com.daqem.grieflogger.database.orm.query.Query`:

```java
import com.daqem.grieflogger.database.orm.query.Query;
```

## Table Creation — `SchemaBuilder`

```java
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;

SchemaBuilder.create("my_table")
    .id("id")                          // auto-increment primary key
    .string("name", 36, false)         // varchar(36) NOT NULL
    .string("uuid", 36, true, true)    // varchar(36) NULLABLE UNIQUE
    .integer("count")                  // int NOT NULL
    .integer("count", true)            // int NULLABLE
    .bigint("time")                    // bigint NOT NULL
    .text("data")                      // text NOT NULL
    .blob("binary_data")              // blob NULLABLE
    .blob("binary_data", false)       // blob NOT NULL
    .foreignKey("user_id", "users", "id")
    .index("idx_name", "col1", "col2")
    .build(database);
```

## INSERT — `Query.insert()`

```java
// Queue for async batch execution (preferred for logging/writes):
Query.insert("chunk_backups")
    .value("backup_uuid", backupUuid)
    .value("time", time)
    .value("world", world)
    .value("chunk_x", chunkX)
    .value("chunk_z", chunkZ)
    .value("chunk_data", chunkData)   // byte[] for blobs
    .queue(database);                  // adds to Consumer queue

// Or execute immediately (for critical inserts):
Query.insert("users")
    .value("uuid", uuid)
    .value("name", name)
    .ignore()                          // INSERT OR IGNORE
    .execute(database);

// Conflict handling:
Query.insert("usernames")
    .value("uuid", uuid)
    .value("name", name)
    .onConflictDoNothing()             // ON CONFLICT DO NOTHING
    .queue(database);

Query.insert("usernames")
    .value("uuid", uuid)
    .value("name", name)
    .onConflictUpdate("name")          // ON CONFLICT UPDATE name
    .queue(database);

// Subquery value:
Query.insert("blocks")
    .value("time", time)
    .valueSubquery("user_id", "SELECT id FROM users WHERE uuid = ?", uuid)
    .queue(database);
```

## SELECT — `Query.select()`

```java
// Basic select with mapper:
List<MyObject> results = Query.select("chunk_backups")
    .columns("backup_uuid", "MIN(time) as time", "COUNT(*) as chunks")
    .groupBy("backup_uuid")
    .orderByDesc("time")
    .execute(database, rs -> {
        // map each ResultSet row to your object
        return new MyObject(
            rs.getString("backup_uuid"),
            rs.getLong("time"),
            rs.getInt("chunks")
        );
    });

// With WHERE clauses:
Query.select("blocks")
    .columns("time", "x", "y", "z", "action")
    .join("users", "blocks.user = users.id")
    .leftJoin("levels", "blocks.level = levels.id")
    .whereEq("level", levelId)
    .whereGt("time", sinceTime)
    .whereBetween("x", minX, maxX)
    .whereIn("action", List.of(1, 2, 3))
    .whereRaw("(x BETWEEN ? AND ?)", minX, maxX) // for complex expressions
    .orderByDesc("time")
    .limit(1000)
    .offset(0)
    .execute(database, rs -> new BlockHistory(...));

// Get single result:
Optional<User> user = Query.select("users")
    .columns("id", "uuid", "name")
    .whereEq("uuid", uuid)
    .executeOne(database, rs -> new User(rs.getInt("id"), rs.getString("uuid")));

// Count:
long count = Query.select("blocks")
    .whereEq("level", levelId)
    .count(database);
```

## UPDATE — `Query.update()`

```java
Query.update("users")
    .set("name", newName)
    .whereEq("uuid", uuid)
    .queue(database);     // async

Query.update("blocks")
    .set("rolled_back", 1)
    .whereEq("id", blockId)
    .execute(database);   // sync
```

## DELETE — `Query.delete()`

```java
Query.delete("blocks")
    .whereEq("level", levelId)
    .whereLt("time", cutoffTime)
    .execute(database);
```

## Important Notes

1. **`.queue(database)`** — adds PreparedStatement to async Consumer queue. Use for writes that don't need immediate confirmation.
2. **`.execute(database)`** — executes immediately on current thread. Use for reads and critical writes.
3. **For nullable values**, use `null` directly — `InsertBuilder` handles it via `stmt.setObject()`.
4. **For byte[] (blobs)**, pass directly as value — `stmt.setObject()` handles it.
5. **Dialect support** is automatic — the builders use `Dialect.current()` internally.
6. **Never hardcode SQL dialect-specific syntax** — use `Dialect` methods if extending the ORM.
