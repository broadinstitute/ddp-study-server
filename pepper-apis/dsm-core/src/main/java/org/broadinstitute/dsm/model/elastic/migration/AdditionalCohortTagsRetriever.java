package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;

public interface AdditionalCohortTagsRetriever extends AdditionalRecordsRetriever<CohortTag> {

    static Optional<AdditionalCohortTagsRetriever> fromRealm(String realm) {
        return OLD_OSTEO_INSTANCE_NAME.equals(realm)
                ? Optional.of(new NewOsteoCohortTagsRetriever(new CohortTagDaoImpl()))
                : Optional.empty();
    }

}
