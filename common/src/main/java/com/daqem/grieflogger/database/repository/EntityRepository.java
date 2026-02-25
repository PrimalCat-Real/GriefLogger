package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.query.Query;
import com.daqem.grieflogger.database.orm.schema.SchemaBuilder;

public class EntityRepository extends Repository {

    private final Database database;

    public EntityRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        SchemaBuilder.create("entities")
                .id("id")
                .string("name", 256, false, true)
                .build(database);
    }

    public void insert(String name) {
        Query.insert("entities")
                .value("name", name)
                .ignore()
                .queue(database);
    }
}
