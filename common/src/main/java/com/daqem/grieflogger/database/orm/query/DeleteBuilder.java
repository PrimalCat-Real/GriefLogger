package com.daqem.grieflogger.database.orm.query;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.Database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public class DeleteBuilder {
    private final String table;
    private final WhereClause where = new WhereClause();

    public DeleteBuilder(String table) {
        this.table = table;
    }

    public DeleteBuilder whereEq(String column, Object value) {
        where.eq(column, value);
        return this;
    }

    public DeleteBuilder whereGt(String column, Object value) {
        where.gt(column, value);
        return this;
    }

    public DeleteBuilder whereLt(String column, Object value) {
        where.lt(column, value);
        return this;
    }

    public DeleteBuilder whereIn(String column, Collection<?> values) {
        where.in(column, values);
        return this;
    }

    public DeleteBuilder whereRaw(String sql, Object... params) {
        where.raw(sql, params);
        return this;
    }

    public String toSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM ").append(table);

        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(where.toSql());
        }

        return sql.toString();
    }

    public List<Object> getParameters() {
        return where.getParameters();
    }

    public void queue(Database database) {
        String sql = toSql();
        List<Object> params = getParameters();

        try {
            PreparedStatement stmt = database.prepareStatement(sql);
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            database.queue.add(stmt);
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to queue DELETE: {}", sql, e);
        }
    }

    public int execute(Database database) {
        String sql = toSql();
        List<Object> params = getParameters();

        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            return stmt.executeUpdate();
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to execute DELETE: {}", sql, e);
            return 0;
        }
    }
}
