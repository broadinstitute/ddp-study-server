package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;


public class AdditionalCohortTagsRetriever extends AdditionalRecordsRetriever<CohortTag> {

    private final CohortTagDao cohortTagDao;

    public AdditionalCohortTagsRetriever(String additionalRealm) {
        super(additionalRealm);
        this.cohortTagDao = new CohortTagDaoImpl();
    }

    static Optional<AdditionalCohortTagsRetriever> fromRealm(String realm) {
        if  (OLD_OSTEO_INSTANCE_NAME.equalsIgnoreCase(realm)) {
            return Optional.of(new AdditionalCohortTagsRetriever(NEW_OSTEO_INSTANCE_NAME));
        }
        if (NEW_OSTEO_INSTANCE_NAME.equalsIgnoreCase(realm)) {
            return Optional.of(new AdditionalCohortTagsRetriever(OLD_OSTEO_INSTANCE_NAME));
        }
        return Optional.empty();
    }

    @Override
    public Map<String, List<CohortTag>> retrieve() {
        return cohortTagDao.getCohortTagsByInstanceName(additionalRealm);
    }
}
