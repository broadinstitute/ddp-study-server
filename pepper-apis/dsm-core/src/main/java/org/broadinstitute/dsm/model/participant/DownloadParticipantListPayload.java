package org.broadinstitute.dsm.model.participant;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DownloadParticipantListPayload {
    private List<String> columnNames;
    private List<String> headerNames;
    private boolean byParticipant;
}
