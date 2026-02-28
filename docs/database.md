# Database and Cache Documentation

This document explains the database layer and caching mechanisms of GriefLogger based on internal implementation details.

## Database Core (`Database.java`, `Dialect.java`, `SchemaBuilder.java`)
*   **WAL Mode**: The SQLite database uses Write-Ahead Logging (WAL) for better concurrency.
*   **Synchronous Mode**: Set to `NORMAL` to ensure a good balance between safety and speed (FULL is safer but slower, OFF is faster but risky).
*   **Performance Improvements**:
    *   Cache size is increased (10000 pages * 4KB = ~40MB cache).
    *   Memory-mapped I/O size is set to 256MB.
    *   Temporary tables are kept in memory for faster queries.
*   **Execution**: Operations can be executed as a batch or individually, with a 5-second timeout for lock acquisition.
*   **Schema**: Indexes are created during table creation via `SchemaBuilder` (e.g., converting `CREATE INDEX` to `ALTER TABLE` if necessary depending on Dialect). Sequential IDs (`SERIAL`) are handled appropriately per Dialect.

## Asynchronous Writers (`Consumer.java`, `ConsumerQueue.java`)
*   **Double-Buffer System**: The `ConsumerQueue` adds database write tasks to a double-buffer system.
*   **Consumer Thread**: A dedicated Consumer thread determines which buffer to process (opposite of current). It waits for the buffer to fill or for a timeout.
*   **Batch processing**: The consumer atomically gets and clears the active buffer and executes SQL statements in batches, committing periodically.
*   **Error Handling**: Connection keepalives and reconnections are handled automatically. If errors occur during execution, it logs only the first 5 errors to avoid spam, waits before retrying, and ensures a final commit.

## Repositories (`BlockRepository`, `ChatRepository`, `CommandRepository`, `ContainerRepository`, `ItemRepository`, `RollbackRepository`, `UserRepository`, `UsernameRepository`)
*   **Block Repository**: Manages `block_states` and `blocks` tables. Frequently uses subqueries in SQL for inserting relational data (material, state, phantom user). Raw SQL is needed for complex subqueries in the `WHERE` clause.
*   **Entities and Coordinates**: Coordinate indexes are added explicitly. Phantom users (e.g., `#water`) are ensured to exist in the `users` table and their names are used as UUIDs for easy identification.
*   **User Repository**: Requires special handling due to `ON CONFLICT / ON DUPLICATE KEY` constraints.
*   **Rollback Repository**: Tracks rollback jobs (0=rollback, 1=restore, 2=undo) and individual block changes (0=block_restore, 1=block_remove). It records states before and after rollbacks. Null user filters are handled by using empty strings.
*   **Compound Constraints**: In some repositories (like `UsernameRepository`), custom SQL is explicitly written for compound `UNIQUE` constraints.

## Caching System (`CacheHandler.java`, `SpreadCache.java`)
*   **TTL (Time To Live)**:
    *   Items generally have specific TTL values (e.g., 15 minutes, 60 minutes).
    *   Cache arrays always start with a timestamp (in seconds or millis) as their first element for lifecycle management.
*   **Cache Processing**: A background thread checks caches iteratively (usually 1 second between checks). Problematic entries are skipped. The thread continues to run despite minor errors and handles interruptions (shutting down).
*   **Spread Cache**: Dedicated to deduplicating natural events (e.g., block spread). If an entry exists and matches the same block type, it's considered a duplicate. The timestamp is updated, and the new event is ignored to prevent spamming the database.

## Service Layer
*   **Pending Queues**: Services repeatedly ensure that pending queues (like `ConsumerQueue`) are flushed before critical operations (like rollbacks) so that all recent changes are securely written to the database.
