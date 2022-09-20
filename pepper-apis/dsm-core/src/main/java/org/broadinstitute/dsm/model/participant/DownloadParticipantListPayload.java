package org.broadinstitute.dsm.model.participant;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.model.Filter;

@Getter
@Setter
/** on retirement of feature-flag-export-new, this class should be promoted to DownloadParticipantListPayload */
public class DownloadParticipantListPayload {
    private List<Filter> columnNames;
}

