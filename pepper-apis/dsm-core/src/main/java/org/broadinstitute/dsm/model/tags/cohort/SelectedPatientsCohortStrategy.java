package org.broadinstitute.dsm.model.tags.cohort;

import java.util.List;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.painless.AddListToNestedByGuidScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.NestedUpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import spark.QueryParamsMap;

public class SelectedPatientsCohortStrategy extends BaseCohortStrategy {
    public SelectedPatientsCohortStrategy(QueryParamsMap queryMap,
                                          DDPInstanceDto ddpInstanceDto,
                                          BulkCohortTagPayload bulkCohortTagPayload) {
        super(queryMap, ddpInstanceDto, bulkCohortTagPayload);
    }

    @Override
    public List<CohortTag> create() {
        BulkCohortTag bulkCohortTag =
                new BulkCohortTag(bulkCohortTagPayload.getCohortTags(), bulkCohortTagPayload.getSelectedPatients());
        CohortTagUseCase cohortTagUseCase =
                new CohortTagUseCase(bulkCohortTag, ddpInstanceDto, new CohortTagDaoImpl(), new ElasticSearch(),
                        new NestedUpsertPainlessFacade(), new AddListToNestedByGuidScriptBuilder());
        return cohortTagUseCase.bulkInsert();
    }
}
