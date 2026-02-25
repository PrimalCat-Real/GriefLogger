package com.daqem.grieflogger.database.repository;

import com.daqem.grieflogger.database.orm.Dialect;

public abstract class Repository implements IRepository {

    @Override
    public boolean isMysql() {
        return Dialect.current().isMysql();
    }

    protected Dialect dialect() {
        return Dialect.current();
    }
}
