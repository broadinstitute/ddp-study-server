package org.broadinstitute.dsm.db.dao.tag.cohort;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;

public interface CohortTagDao extends Dao<CohortTag> {
    Map<String, List<CohortTag>> getCohortTagsByInstanceName(String instanceName);

    List<Integer> bulkCohortCreate(List<CohortTag> cohortTags);
}
