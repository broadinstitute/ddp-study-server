package org.broadinstitute.dsm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParticipantColumn {

    public String name;
    public String tableAlias;
    public String object;

    public ParticipantColumn(String name, String tableAlias) {
        this.name = name;
        this.tableAlias = tableAlias;
    }

    public ParticipantColumn() {

    }
}
