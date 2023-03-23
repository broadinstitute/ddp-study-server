package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.statics.ESObjectConstants;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;

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
        addOldOsteoTagsToNewOsteo(cohortTags, realm);
        return (Map) cohortTags;
    }

    private void addOldOsteoTagsToNewOsteo(Map<String, List<CohortTag>> cohortTags, String realm) {
        if (realm.equalsIgnoreCase(NEW_OSTEO_INSTANCE_NAME)) {
            OldOsteoCohortTagsRetriever oldOsteoRetriever = new OldOsteoCohortTagsRetriever(new CohortTagDaoImpl());
            Map<String, List<CohortTag>> oldOsteoTags = oldOsteoRetriever.retrieve();
            oldOsteoTags.forEach((guid, records) -> {
                if (cohortTags.containsKey(guid)) {
                    List<CohortTag> mergedCohorts = Stream.concat(cohortTags.get(guid).stream(), records.stream()).collect(Collectors.toList());
                    cohortTags.put(guid, mergedCohorts);
                }
            });
        }
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
