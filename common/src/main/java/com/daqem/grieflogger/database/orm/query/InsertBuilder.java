package com.daqem.grieflogger.database.orm.query;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.Dialect;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class InsertBuilder {
    private final String table;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private boolean ignore = false;
    private String onConflictColumn = null;
    private String onConflictUpdate = null;
    private String subqueryColumn = null;
    private String subquery = null;
    private Object[] subqueryParams = null;

    public InsertBuilder(String table) {
        this.table = table;
    }

    public InsertBuilder value(String column, Object value) {
        values.put(column, value);
        return this;
    }

    public InsertBuilder values(Map<String, Object> map) {
        values.putAll(map);
        return this;
    }

    public InsertBuilder ignore() {
        this.ignore = true;
        return this;
    }

    public InsertBuilder onConflictDoNothing() {
        this.onConflictColumn = "";
        return this;
    }

    public InsertBuilder onConflictUpdate(String column) {
        this.onConflictColumn = column;
        this.onConflictUpdate = column;
        return this;
    }

    public InsertBuilder valueSubquery(String column, String subquery, Object... params) {
        this.subqueryColumn = column;
        this.subquery = subquery;
        this.subqueryParams = params;
        return this;
    }

    public String toSql() {
        Dialect dialect = Dialect.current();
        StringBuilder sql = new StringBuilder();

        if (ignore) {
            sql.append(dialect.insertIgnore());
        } else {
            sql.append("INSERT");
        }

        sql.append(" INTO ").append(table).append(" (");

        List<String> columns = new ArrayList<>(values.keySet());
        if (subqueryColumn != null) {
            columns.add(subqueryColumn);
        }
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");

        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            placeholders.add("?");
        }
        if (subquery != null) {
            placeholders.add("(" + subquery + ")");
        }
        sql.append(String.join(", ", placeholders));
        sql.append(")");

        if (onConflictColumn != null) {
            if (onConflictUpdate != null) {
                sql.append(" ").append(dialect.onConflictUpdate(onConflictUpdate));
            } else {
                sql.append(" ").append(dialect.onConflictDoNothing());
            }
        }

        return sql.toString();
    }

    public List<Object> getParameters() {
        List<Object> params = new ArrayList<>(values.values());
        if (subqueryParams != null) {
            params.addAll(Arrays.asList(subqueryParams));
        }
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
            GriefLogger.LOGGER.error("Failed to queue INSERT: {}", sql, e);
        }
    }

    public void execute(Database database) {
        String sql = toSql();
        List<Object> params = getParameters();

        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to execute INSERT: {}", sql, e);
        }
    }
}
