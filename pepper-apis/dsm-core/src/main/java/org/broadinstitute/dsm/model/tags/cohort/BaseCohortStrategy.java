package org.broadinstitute.dsm.model.tags.cohort;

import java.util.List;
import java.util.Objects;

import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.painless.AddListToNestedByGuidScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.NestedUpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import spark.QueryParamsMap;

public class BaseCohortStrategy implements CohortStrategy {

    protected BulkCohortTagPayload bulkCohortTagPayload;

    public BaseCohortStrategy() {}

    @Override
    public void setBulkCohortTagPayload(BulkCohortTagPayload bulkCohortTagPayload) {
        this.bulkCohortTagPayload = bulkCohortTagPayload;
    }

    protected DDPInstanceDto getDDPInstanceDto() {
        return bulkCohortTagPayload.getDdpInstanceDto();
    }

    protected QueryParamsMap getQueryMap() {
        return bulkCohortTagPayload.getQueryMap();
    }

    @Override
    public List<CohortTag> create() {
        Objects.requireNonNull(bulkCohortTagPayload);
        BulkCohortTag bulkCohortTag =
                new BulkCohortTag(bulkCohortTagPayload.getCohortTags(), getSelectedPatients());
        bulkCohortTag.setCreatedBy(bulkCohortTagPayload.getCreatedBy());
        CohortTagUseCase cohortTagUseCase =
                new CohortTagUseCase(bulkCohortTag, getDDPInstanceDto(), new CohortTagDaoImpl(), new ElasticSearch(),
                        new NestedUpsertPainlessFacade(), new AddListToNestedByGuidScriptBuilder());
        return cohortTagUseCase.bulkInsert();
    }

    protected List<String> getSelectedPatients() {
        return bulkCohortTagPayload.getSelectedPatients();
    }
}
