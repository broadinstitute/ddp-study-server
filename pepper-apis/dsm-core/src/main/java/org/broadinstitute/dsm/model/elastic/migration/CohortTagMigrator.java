package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class CohortTagMigrator extends BaseCollectionMigrator {

    private CohortTagDao cohortTagDao;

    public CohortTagMigrator(String index, String realm, CohortTagDao cohortTagDao) {
        super(index, realm, ESObjectConstants.COHORT_TAG);
        this.cohortTagDao = cohortTagDao;
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<CohortTag>> cohortTags = cohortTagDao.getCohortTagsByInstanceName(realm);
        updateCohortTagsIfRequired(cohortTags);
        return (Map) cohortTags;
    }

    private void updateCohortTagsIfRequired(Map<String, List<CohortTag>> cohortTags) {
        AdditionalCohortTagsRetriever.fromRealm(realm)
                .ifPresent(retriever -> concatenateCohortTags(cohortTags, retriever));
    }

    private void concatenateCohortTags(Map<String, List<CohortTag>> cohortTags, AdditionalCohortTagsRetriever retriever) {
        Map<String, List<CohortTag>> additionalCohortTags = retriever.retrieve();
        additionalCohortTags.forEach((guid, records) -> {
            if (cohortTags.containsKey(guid)) {
                List<CohortTag> mergedCohorts = Stream.concat(cohortTags.get(guid).stream(), records.stream()).collect(Collectors.toList());
                cohortTags.put(guid, mergedCohorts);
            } else {
                cohortTags.put(guid, records);
            }
        });
    }
}
