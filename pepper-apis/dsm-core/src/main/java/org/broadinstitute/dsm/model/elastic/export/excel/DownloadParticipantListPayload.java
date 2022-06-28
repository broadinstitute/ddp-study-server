package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.model.ParticipantColumn;

@Getter
@Setter
public class DownloadParticipantListPayload {
    private List<ParticipantColumn> columnNames;
}
