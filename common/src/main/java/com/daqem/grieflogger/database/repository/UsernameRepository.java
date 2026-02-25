package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.orm.Dialect;
import com.daqem.grieflogger.database.orm.query.Query;

public class UsernameRepository extends Repository {

    private final Database database;

    public UsernameRepository(Database database) {
        this.database = database;
    }

    public void createTable() {
        // Custom SQL needed for compound UNIQUE constraint
        Dialect dialect = Dialect.current();
        String sql;
        if (dialect == Dialect.MYSQL) {
            sql = """
                    CREATE TABLE IF NOT EXISTS usernames (
                        id int PRIMARY KEY AUTO_INCREMENT,
                        time bigint NOT NULL,
                        uuid varchar(36) NOT NULL,
                        name varchar(16) NOT NULL,
                        UNIQUE(uuid, name)
                    )
                    ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
                    """;
        } else {
            sql = """
                    CREATE TABLE IF NOT EXISTS usernames (
                        id integer PRIMARY KEY AUTOINCREMENT,
                        time integer NOT NULL,
                        uuid text NOT NULL,
                        name text NOT NULL,
                        UNIQUE(uuid, name)
                    );
                    """;
        }
        database.createTable(sql);
    }

    public void insert(long time, String uuid, String name) {
        Query.insert("usernames")
                .value("time", time)
                .value("uuid", uuid)
                .value("name", name)
                .ignore()
                .queue(database);
    }
}
