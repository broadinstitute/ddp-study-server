package org.broadinstitute.dsm.model;

import lombok.Getter;

@Getter
public class ParticipantColumn {

    public String name;
    public String tableAlias;

    public ParticipantColumn(String name, String tableAlias) {
        this.name = name;
        this.tableAlias = tableAlias;
    }
}
