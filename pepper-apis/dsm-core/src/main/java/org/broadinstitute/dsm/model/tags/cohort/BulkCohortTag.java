package org.broadinstitute.dsm.model.tags.cohort;

import java.util.List;

import lombok.Data;

@Data
public class BulkCohortTag {
    protected List<String> cohortTags;
    protected List<String> selectedPatients;
    protected String createdBy;

    public BulkCohortTag(List<String> cohortTags, List<String> selectedPatients) {
        this.cohortTags = cohortTags;
        this.selectedPatients = selectedPatients;
    }

    public BulkCohortTag() {}
}
