package com.daqem.grieflogger.database.orm.query;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.Dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

public class SelectBuilder {
    private final String table;
    private final List<String> columns = new ArrayList<>();
    private final List<JoinClause> joins = new ArrayList<>();
    private final WhereClause where = new WhereClause();
    private final List<String> orderBy = new ArrayList<>();
    private final List<String> groupBy = new ArrayList<>();
    private int limit = 0;
    private int offset = 0;
    private boolean distinct = false;

    public SelectBuilder(String table) {
        this.table = table;
    }

    public SelectBuilder columns(String... cols) {
        columns.addAll(Arrays.asList(cols));
        return this;
    }

    public SelectBuilder column(String col) {
        columns.add(col);
        return this;
    }

    public SelectBuilder distinct() {
        this.distinct = true;
        return this;
    }

    public SelectBuilder join(String table, String onClause) {
        joins.add(new JoinClause("INNER JOIN", table, onClause));
        return this;
    }

    public SelectBuilder leftJoin(String table, String onClause) {
        joins.add(new JoinClause("LEFT JOIN", table, onClause));
        return this;
    }

    public SelectBuilder where(String column, String operator, Object value) {
        switch (operator) {
            case "=" -> where.eq(column, value);
            case "!=" -> where.neq(column, value);
            case ">" -> where.gt(column, value);
            case ">=" -> where.gte(column, value);
            case "<" -> where.lt(column, value);
            case "<=" -> where.lte(column, value);
            case "LIKE" -> where.like(column, (String) value);
            default -> throw new IllegalArgumentException("Unknown operator: " + operator);
        }
        return this;
    }

    public SelectBuilder whereEq(String column, Object value) {
        where.eq(column, value);
        return this;
    }

    public SelectBuilder whereGt(String column, Object value) {
        where.gt(column, value);
        return this;
    }

    public SelectBuilder whereLt(String column, Object value) {
        where.lt(column, value);
        return this;
    }

    public SelectBuilder whereBetween(String column, Object min, Object max) {
        where.between(column, min, max);
        return this;
    }

    public SelectBuilder whereIn(String column, Collection<?> values) {
        where.in(column, values);
        return this;
    }

    public SelectBuilder whereNotIn(String column, Collection<?> values) {
        where.notIn(column, values);
        return this;
    }

    public SelectBuilder whereRaw(String sql, Object... params) {
        where.raw(sql, params);
        return this;
    }

    public SelectBuilder orderBy(String column, String direction) {
        orderBy.add(column + " " + direction);
        return this;
    }

    public SelectBuilder orderByDesc(String column) {
        return orderBy(column, "DESC");
    }

    public SelectBuilder orderByAsc(String column) {
        return orderBy(column, "ASC");
    }

    public SelectBuilder groupBy(String... cols) {
        groupBy.addAll(Arrays.asList(cols));
        return this;
    }

    public SelectBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public SelectBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    public String toSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");

        if (distinct) {
            sql.append("DISTINCT ");
        }

        if (columns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", columns));
        }

        sql.append(" FROM ").append(table);

        for (JoinClause join : joins) {
            sql.append(" ").append(join.type()).append(" ").append(join.table())
               .append(" ON ").append(join.onClause());
        }

        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(where.toSql());
        }

        if (!groupBy.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupBy));
        }

        if (!orderBy.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", orderBy));
        }

        if (limit > 0) {
            sql.append(" ").append(Dialect.current().limitOffset(limit, offset));
        }

        return sql.toString();
    }

    public List<Object> getParameters() {
        return where.getParameters();
    }

    public <T> List<T> execute(Database database, Function<ResultSet, T> mapper) {
        List<T> results = new ArrayList<>();
        String sql = toSql();
        List<Object> params = getParameters();

        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.apply(rs));
                }
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to execute SELECT query: {}", sql, e);
        }

        return results;
    }

    public <T> Optional<T> executeOne(Database database, Function<ResultSet, T> mapper) {
        limit(1);
        List<T> results = execute(database, mapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public long count(Database database) {
        columns.clear();
        columns.add("COUNT(*)");
        String sql = toSql();
        List<Object> params = getParameters();

        try (PreparedStatement stmt = database.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            GriefLogger.LOGGER.error("Failed to execute COUNT query: {}", sql, e);
        }

        return 0;
    }

    private record JoinClause(String type, String table, String onClause) {}
}
