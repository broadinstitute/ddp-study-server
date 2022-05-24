package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class CohortTagMigrator extends BaseCollectionMigrator {

    private CohortTagDao cohortTagDao;

    public CohortTagMigrator(String index, String realm, CohortTagDao cohortTagDao) {
        super(index, realm, ESObjectConstants.COHORT_TAG);
        this.cohortTagDao = cohortTagDao;
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return (Map) cohortTagDao.getCohortTagsByInstanceName(realm);
    }
}
