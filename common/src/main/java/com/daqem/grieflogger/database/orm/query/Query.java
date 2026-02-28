package com.daqem.grieflogger.database.orm.query;

/**
 * Entry point for building SQL queries.
 *
 * Usage examples:
 *
 * SELECT:
 * Query.select("blocks")
 *     .columns("time", "user", "x", "y", "z")
 *     .join("users", "blocks.user = users.id")
 *     .whereEq("level", levelId)
 *     .whereGt("time", sinceTime)
 *     .orderByDesc("time")
 *     .limit(1000)
 *     .execute(database, rs -> new BlockHistory(...));
 *
 * INSERT:
 * Query.insert("materials")
 *     .value("name", materialName)
 *     .ignore()
 *     .queue(database);
 *
 * UPDATE:
 * Query.update("users")
 *     .set("name", newName)
 *     .whereEq("uuid", uuid)
 *     .queue(database);
 *
 * DELETE:
 * Query.delete("blocks")
 *     .whereEq("level", levelId)
 *     .whereLt("time", cutoffTime)
 *     .execute(database);
 */
public final class Query {

    private Query() {}

    public static SelectBuilder select(String table) {
        return new SelectBuilder(table);
    }

    public static InsertBuilder insert(String table) {
        return new InsertBuilder(table);
    }

    public static UpdateBuilder update(String table) {
        return new UpdateBuilder(table);
    }

    public static DeleteBuilder delete(String table) {
        return new DeleteBuilder(table);
    }
}
