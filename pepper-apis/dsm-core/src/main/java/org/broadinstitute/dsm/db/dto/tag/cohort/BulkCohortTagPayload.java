package org.broadinstitute.dsm.db.dto.tag.cohort;

import lombok.Data;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.model.tags.cohort.BulkCohortTag;
import org.broadinstitute.dsm.model.tags.cohort.CreateOption;

@Data
public class BulkCohortTagPayload extends BulkCohortTag {
    private String manualFilter;
    private ViewFilter savedFilter;
    private CreateOption selectedOption;
}
