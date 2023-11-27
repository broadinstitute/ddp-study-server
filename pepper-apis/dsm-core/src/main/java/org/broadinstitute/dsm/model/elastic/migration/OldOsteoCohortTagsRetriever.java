package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;

import java.util.List;
import java.util.Map;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;


public class OldOsteoCohortTagsRetriever implements AdditionalCohortTagsRetriever {

    private CohortTagDao cohortTagDao;

    public OldOsteoCohortTagsRetriever(CohortTagDao cohortTagDao) {
        this.cohortTagDao = cohortTagDao;
    }

    @Override
    public Map<String, List<CohortTag>> retrieve() {
        return cohortTagDao.getCohortTagsByInstanceName(OLD_OSTEO_INSTANCE_NAME);
    }
}
