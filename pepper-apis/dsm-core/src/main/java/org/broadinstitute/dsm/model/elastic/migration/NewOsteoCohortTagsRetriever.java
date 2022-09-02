package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;


public class NewOsteoCohortTagsRetriever implements AdditionalCohortTagsRetriever {

    private CohortTagDao cohortTagDao;

    public NewOsteoCohortTagsRetriever(CohortTagDao cohortTagDao) {
        this.cohortTagDao = cohortTagDao;
    }

    @Override
    public Map<String, List<CohortTag>> retrieve() {
        return cohortTagDao.getCohortTagsByInstanceName(NEW_OSTEO_INSTANCE_NAME);
    }
}
