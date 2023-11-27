package org.broadinstitute.ddp.model.address;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Higher level abstraction that covers states and provinces,etc.
 */
public class SubnationalDivision {
    //transient to exclucde from Gson marshalling
    private transient long id;
    private String name;
    private String code;

    public SubnationalDivision() {
        super();
    }

    public SubnationalDivision(String name, String code) {
        this.name = name;
        this.code = code;
    }

    @ColumnName("country_subnational_division_id")
    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }


    public void setId(long id) {
        this.id = id;
    }
}
