package com.daqem.grieflogger.database.orm.query;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.Database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class UpdateBuilder {
    private final String table;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final WhereClause where = new WhereClause();

    public UpdateBuilder(String table) {
        this.table = table;
    }

    public UpdateBuilder set(String column, Object value) {
        values.put(column, value);
        return this;
    }

    public UpdateBuilder sets(Map<String, Object> map) {
        values.putAll(map);
        return this;
    }

    public UpdateBuilder whereEq(String column, Object value) {
        where.eq(column, value);
        return this;
    }

    public UpdateBuilder whereGt(String column, Object value) {
        where.gt(column, value);
        return this;
    }

    public UpdateBuilder whereLt(String column, Object value) {
        where.lt(column, value);
        return this;
    }

    public UpdateBuilder whereIn(String column, Collection<?> values) {
        where.in(column, values);
        return this;
    }

    public UpdateBuilder whereRaw(String sql, Object... params) {
        where.raw(sql, params);
        return this;
    }

    public String toSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(table).append(" SET ");

        List<String> setClauses = new ArrayList<>();
        for (String column : values.keySet()) {
            setClauses.add(column + " = ?");
        }
        sql.append(String.join(", ", setClauses));

        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(where.toSql());
        }

        return sql.toString();
    }

    public List<Object> getParameters() {
        List<Object> params = new ArrayList<>(values.values());
        params.addAll(where.getParameters());
        return params;
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
            GriefLogger.LOGGER.error("Failed to queue UPDATE: {}", sql, e);
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
            GriefLogger.LOGGER.error("Failed to execute UPDATE: {}", sql, e);
            return 0;
        }
    }
}
