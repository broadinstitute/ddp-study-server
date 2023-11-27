package org.broadinstitute.dsm.model.tags.cohort;

import java.util.List;

import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;

public interface CohortStrategy {

    List<CohortTag> create();

    void setBulkCohortTagPayload(BulkCohortTagPayload bulkCohortTagPayload);

}
