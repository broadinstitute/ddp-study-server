package org.broadinstitute.dsm.db.structure;

import lombok.Data;

@Data
public class DBElement {

    public String tableName;
    public String tableAlias;
    public String primaryKey;
    public String columnName;

    public DBElement(String tableName, String tableAlias, String primaryKey, String columnName) {
        this.tableName = tableName;
        this.tableAlias = tableAlias;
        this.primaryKey = primaryKey;
        this.columnName = columnName;
    }
}
