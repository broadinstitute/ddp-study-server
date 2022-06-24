package org.broadinstitute.dsm.model.tags.cohort;

import java.util.List;
import java.util.Objects;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.painless.AddListToNestedByGuidScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.NestedUpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;

public class SelectedPatientsCohortStrategy extends BaseCohortStrategy {
    public SelectedPatientsCohortStrategy() {
    }

    @Override
    public List<CohortTag> create() {
        Objects.requireNonNull(bulkCohortTagPayload);
        BulkCohortTag bulkCohortTag =
                new BulkCohortTag(bulkCohortTagPayload.getCohortTags(), bulkCohortTagPayload.getSelectedPatients());
        CohortTagUseCase cohortTagUseCase =
                new CohortTagUseCase(bulkCohortTag, getDDPInstanceDto(), new CohortTagDaoImpl(), new ElasticSearch(),
                        new NestedUpsertPainlessFacade(), new AddListToNestedByGuidScriptBuilder());
        return cohortTagUseCase.bulkInsert();
    }
}
