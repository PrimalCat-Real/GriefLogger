package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;

public class LevelRepository extends Repository {

    private final Database database;

    public LevelRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("levels")
                .id("id")
                .string("name", 256, false, true)
                .build(database);
    }

    public void insert(String name) {
        Query.insert("levels")
                .value("name", name)
                .ignore()
                .queue(database);
    }
}
