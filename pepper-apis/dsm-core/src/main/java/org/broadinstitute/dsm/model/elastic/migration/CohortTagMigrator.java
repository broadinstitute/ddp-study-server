package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Slf4j
public class CohortTagMigrator extends BaseCollectionMigrator {
    private final CohortTagDao cohortTagDao;

    public CohortTagMigrator(String index, String realm, CohortTagDao cohortTagDao) {
        super(index, realm, ESObjectConstants.COHORT_TAG);
        this.cohortTagDao = cohortTagDao;
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<CohortTag>> cohortTags = cohortTagDao.getCohortTagsByInstanceName(realm);
        int tagsFromRealm = cohortTags.size();
        AdditionalCohortTagsRetriever.fromRealm(realm)
                .ifPresent(retriever -> retriever.mergeRecords(cohortTags));
        log.info("Migrator retrieved {} cohort tags from realm {}, and {} additional tags",
                tagsFromRealm, realm, cohortTags.size() - tagsFromRealm);
        return (Map) cohortTags;
    }
}
