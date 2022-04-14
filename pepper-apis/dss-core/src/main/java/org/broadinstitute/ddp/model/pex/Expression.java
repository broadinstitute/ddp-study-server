package org.broadinstitute.ddp.model.pex;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class Expression {

    private long id;
    private String guid;
    private String text;

    @JdbiConstructor
    public Expression(@ColumnName("expression_id") long id,
                      @ColumnName("expression_guid") String guid,
                      @ColumnName("expression_text") String text) {
        this.id = id;
        this.guid = guid;
        this.text = text;
    }

    public Expression(String text) {
        this.text = text;
    }

    public long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public String getText() {
        return text;
    }
}
