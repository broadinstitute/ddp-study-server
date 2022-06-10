package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;

import java.util.Optional;

import static org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter.OLD_OSTEO_INSTANCE_NAME;

public interface AdditionalCohortTagsRetriever extends AdditionalRecordsRetriever<CohortTag> {

    static Optional<AdditionalCohortTagsRetriever> fromRealm(String realm) {
        return OLD_OSTEO_INSTANCE_NAME.equals(realm)
                ? Optional.of(new NewOsteoCohortTagsRetriever(new CohortTagDaoImpl()))
                : Optional.empty();
    }

}
