package org.broadinstitute.dsm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class ParticipantColumn {
    private String display;
    private String name;
    private String tableAlias;
    private String object;

    public ParticipantColumn(String name, String tableAlias) {
        this.name = name;
        this.tableAlias = tableAlias;
    }

}
