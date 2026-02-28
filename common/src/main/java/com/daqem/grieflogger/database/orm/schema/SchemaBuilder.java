package com.daqem.grieflogger.database.orm.schema;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.Dialect;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for CREATE TABLE statements with MySQL/SQLite dialect support.
 *
 * Usage:
 * SchemaBuilder.create("users")
 *     .id("id")
 *     .string("name", 16, false)
 *     .string("uuid", 36, true, true)  // unique
 *     .build(database);
 */
public class SchemaBuilder {
    private final String table;
    private final List<ColumnDef> columns = new ArrayList<>();
    private final List<String> foreignKeys = new ArrayList<>();
    private final List<String> indices = new ArrayList<>();

    private SchemaBuilder(String table) {
        this.table = table;
    }

    public static SchemaBuilder create(String table) {
        return new SchemaBuilder(table);
    }

    public SchemaBuilder id(String name) {
        columns.add(new ColumnDef(name, ColumnType.ID, 0, false, false));
        return this;
    }

    public SchemaBuilder integer(String name, boolean nullable) {
        columns.add(new ColumnDef(name, ColumnType.INT, 0, nullable, false));
        return this;
    }

    public SchemaBuilder integer(String name) {
        return integer(name, false);
    }

    public SchemaBuilder bigint(String name, boolean nullable) {
        columns.add(new ColumnDef(name, ColumnType.BIGINT, 0, nullable, false));
        return this;
    }

    public SchemaBuilder bigint(String name) {
        return bigint(name, false);
    }

    public SchemaBuilder string(String name, int length, boolean nullable) {
        columns.add(new ColumnDef(name, ColumnType.STRING, length, nullable, false));
        return this;
    }

    public SchemaBuilder string(String name, int length, boolean nullable, boolean unique) {
        columns.add(new ColumnDef(name, ColumnType.STRING, length, nullable, unique));
        return this;
    }

    public SchemaBuilder string(String name, int length) {
        return string(name, length, true);
    }

    public SchemaBuilder text(String name, boolean nullable) {
        columns.add(new ColumnDef(name, ColumnType.TEXT, 0, nullable, false));
        return this;
    }

    public SchemaBuilder text(String name) {
        return text(name, false);
    }

    public SchemaBuilder blob(String name, boolean nullable) {
        columns.add(new ColumnDef(name, ColumnType.BLOB, 0, nullable, false));
        return this;
    }

    public SchemaBuilder blob(String name) {
        return blob(name, true);
    }

    public SchemaBuilder foreignKey(String column, String refTable, String refColumn) {
        foreignKeys.add("FOREIGN KEY(" + column + ") REFERENCES " + refTable + "(" + refColumn + ")");
        return this;
    }

    public SchemaBuilder index(String name, String... columns) {
        indices.add("CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + String.join(", ", columns) + ")");
        return this;
    }

    public String toSql() {
        Dialect dialect = Dialect.current();
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(table).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        for (ColumnDef col : columns) {
            columnDefs.add("    " + col.toSql(dialect));
        }
        columnDefs.addAll(foreignKeys.stream().map(fk -> "    " + fk).toList());

        sql.append(String.join(",\n", columnDefs));
        sql.append("\n)");

        String opts = dialect.tableOptions();
        if (!opts.isEmpty()) {
            sql.append("\n").append(opts);
        }

        return sql.toString();
    }

    public void build(Database database) {
        database.createTable(toSql());
        for (String idx : indices) {
            database.execute(indexSqlForDialect(idx), false);
        }
    }

    private String indexSqlForDialect(String sqliteIndex) {
        Dialect dialect = Dialect.current();
        if (dialect == Dialect.MYSQL) {
            if (sqliteIndex.startsWith("CREATE INDEX IF NOT EXISTS ")) {
                String rest = sqliteIndex.substring("CREATE INDEX IF NOT EXISTS ".length());
                int onIdx = rest.indexOf(" ON ");
                if (onIdx > 0) {
                    String indexName = rest.substring(0, onIdx);
                    String tableAndCols = rest.substring(onIdx + 4);
                    int parenIdx = tableAndCols.indexOf(" (");
                    if (parenIdx > 0) {
                        String cols = tableAndCols.substring(parenIdx + 2, tableAndCols.length() - 1);
                        return "ALTER TABLE " + table + " ADD INDEX " + indexName + " (" + cols + ")";
                    }
                }
            }
        }
        return sqliteIndex;
    }

    private enum ColumnType {
        ID, INT, BIGINT, STRING, TEXT, BLOB
    }

    private record ColumnDef(String name, ColumnType type, int length, boolean nullable, boolean unique) {
        String toSql(Dialect dialect) {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" ");

            switch (type) {
                case ID -> {
                    if (dialect == Dialect.MYSQL) {
                        sb.append("int NOT NULL AUTO_INCREMENT PRIMARY KEY");
                    } else {
                        sb.append("integer PRIMARY KEY AUTOINCREMENT");
                    }
                    return sb.toString();
                }
                case INT -> sb.append(dialect.intType());
                case BIGINT -> sb.append(dialect.bigintType());
                case STRING -> sb.append(dialect.textType(length > 0 ? length : 255));
                case TEXT -> sb.append(dialect == Dialect.MYSQL ? "text" : "text");
                case BLOB -> sb.append(dialect.blobType());
            }

            if (!nullable && type != ColumnType.ID) {
                sb.append(" NOT NULL");
            }
            if (nullable && type != ColumnType.ID) {
                sb.append(" DEFAULT NULL");
            }
            if (unique) {
                sb.append(" UNIQUE");
            }

            return sb.toString();
        }
    }
}
