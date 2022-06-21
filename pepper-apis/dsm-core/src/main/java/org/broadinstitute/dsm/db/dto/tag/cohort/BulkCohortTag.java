package org.broadinstitute.dsm.db.dto.tag.cohort;

import java.util.List;

import lombok.Data;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.model.tags.cohort.CreateOption;

@Data
public class BulkCohortTag {

    private List<String> cohortTags;
    private String manualFilter;
    private ViewFilter savedFilter;
    private List<String> selectedPatients;
    private CreateOption selectedOption;

}
