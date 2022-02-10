package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class UmbrellaDto implements Serializable {

    private long id;
    private String name;
    private String guid;

    @JdbiConstructor
    public UmbrellaDto(@ColumnName("umbrella_id") long id,
                       @ColumnName("umbrella_name") String name,
                       @ColumnName("umbrella_guid") String guid) {
        this.id = id;
        this.name = name;
        this.guid = guid;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getGuid() {
        return guid;
    }
}
