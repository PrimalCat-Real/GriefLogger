package com.daqem.grieflogger.database.orm.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WhereClause {
    private final List<Condition> conditions = new ArrayList<>();
    private final List<Object> parameters = new ArrayList<>();

    public WhereClause eq(String column, Object value) {
        conditions.add(new Condition(column, "=", ConditionType.AND));
        parameters.add(value);
        return this;
    }

    public WhereClause neq(String column, Object value) {
        conditions.add(new Condition(column, "!=", ConditionType.AND));
        parameters.add(value);
        return this;
    }

    public WhereClause gt(String column, Object value) {
        conditions.add(new Condition(column, ">", ConditionType.AND));
        parameters.add(value);
        return this;
    }

    public WhereClause gte(String column, Object value) {
        conditions.add(new Condition(column, ">=", ConditionType.AND));
        parameters.add(value);
        return this;
    }

    public WhereClause lt(String column, Object value) {
        conditions.add(new Condition(column, "<", ConditionType.AND));
        parameters.add(value);
        return this;
    }

    public WhereClause lte(String column, Object value) {
        conditions.add(new Condition(column, "<=", ConditionType.AND));
        parameters.add(value);
        return this;
    }

    public WhereClause between(String column, Object min, Object max) {
        conditions.add(new Condition(column, "BETWEEN", ConditionType.AND, true));
        parameters.add(min);
        parameters.add(max);
        return this;
    }

    public WhereClause in(String column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        conditions.add(new Condition(column, "IN", ConditionType.AND, false, values.size()));
        parameters.addAll(values);
        return this;
    }

    public WhereClause notIn(String column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        conditions.add(new Condition(column, "NOT IN", ConditionType.AND, false, values.size()));
        parameters.addAll(values);
        return this;
    }

    public WhereClause isNull(String column) {
        conditions.add(new Condition(column, "IS NULL", ConditionType.AND, false, 0));
        return this;
    }

    public WhereClause isNotNull(String column) {
        conditions.add(new Condition(column, "IS NOT NULL", ConditionType.AND, false, 0));
        return this;
    }

    public WhereClause like(String column, String pattern) {
        conditions.add(new Condition(column, "LIKE", ConditionType.AND));
        parameters.add(pattern);
        return this;
    }

    public WhereClause orEq(String column, Object value) {
        conditions.add(new Condition(column, "=", ConditionType.OR));
        parameters.add(value);
        return this;
    }

    public WhereClause raw(String sql, Object... params) {
        conditions.add(new Condition(sql, "", ConditionType.RAW));
        for (Object param : params) {
            parameters.add(param);
        }
        return this;
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    public String toSql() {
        if (conditions.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conditions.size(); i++) {
            Condition cond = conditions.get(i);
            if (i > 0) {
                sb.append(cond.type == ConditionType.OR ? " OR " : " AND ");
            }

            if (cond.type == ConditionType.RAW) {
                sb.append(cond.column);
            } else if (cond.operator.equals("BETWEEN")) {
                sb.append(cond.column).append(" BETWEEN ? AND ?");
            } else if (cond.operator.equals("IN") || cond.operator.equals("NOT IN")) {
                sb.append(cond.column).append(" ").append(cond.operator).append(" (");
                sb.append(String.join(", ", java.util.Collections.nCopies(cond.inCount, "?")));
                sb.append(")");
            } else if (cond.operator.equals("IS NULL") || cond.operator.equals("IS NOT NULL")) {
                sb.append(cond.column).append(" ").append(cond.operator);
            } else {
                sb.append(cond.column).append(" ").append(cond.operator).append(" ?");
            }
        }
        return sb.toString();
    }

    public List<Object> getParameters() {
        return new ArrayList<>(parameters);
    }

    private enum ConditionType {
        AND, OR, RAW
    }

    private record Condition(String column, String operator, ConditionType type, boolean isBetween, int inCount) {
        Condition(String column, String operator, ConditionType type) {
            this(column, operator, type, false, 1);
        }

        Condition(String column, String operator, ConditionType type, boolean isBetween) {
            this(column, operator, type, isBetween, 1);
        }
    }
}
