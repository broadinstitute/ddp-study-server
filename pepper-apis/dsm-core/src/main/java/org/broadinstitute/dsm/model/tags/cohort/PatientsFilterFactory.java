package org.broadinstitute.dsm.model.tags.cohort;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.filter.participant.BaseFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.ManualFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.QuickFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.SavedFilterParticipantList;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;

class PatientsFilterFactory {

    private BulkCohortTagPayload bulkCohortTagPayload;

    PatientsFilterFactory(BulkCohortTagPayload bulkCohortTagPayload) {
        this.bulkCohortTagPayload = bulkCohortTagPayload;
    }

    public Filterable<ParticipantWrapperResult> instance() {
        BaseFilterParticipantList filter = new ManualFilterParticipantList(StringUtils.EMPTY);
        if (Objects.nonNull(bulkCohortTagPayload.getManualFilter())) {
            filter = new ManualFilterParticipantList(bulkCohortTagPayload.getManualFilter());
        } else if (Objects.nonNull(bulkCohortTagPayload.getSavedFilter())) {
            if (StringUtils.isNotBlank(bulkCohortTagPayload.getSavedFilter().getFilterName())) {
                filter = new QuickFilterParticipantList();
            } else if (Objects.nonNull(bulkCohortTagPayload.getSavedFilter().getFilters())
                    && bulkCohortTagPayload.getSavedFilter().getFilters().length > 0) {
                filter = new SavedFilterParticipantList();
            }
        }
        return filter;
    }

}
