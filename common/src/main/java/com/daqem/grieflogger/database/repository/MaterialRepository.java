package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;

public class MaterialRepository extends Repository {

    private final Database database;

    public MaterialRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("materials")
                .id("id")
                .string("name", 256, false, true)
                .build(database);
    }

    public void insert(String material) {
        Query.insert("materials")
                .value("name", material)
                .ignore()
                .queue(database);
    }
}
