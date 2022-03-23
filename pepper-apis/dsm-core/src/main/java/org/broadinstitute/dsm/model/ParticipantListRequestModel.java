package org.broadinstitute.dsm.model;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class ParticipantListRequestModel {
    private String realm;
    private List<String> fieldNames;
    private boolean byParticipant;
}
