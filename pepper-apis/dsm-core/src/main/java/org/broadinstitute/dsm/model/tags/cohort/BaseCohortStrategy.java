package org.broadinstitute.dsm.model.tags.cohort;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import spark.QueryParamsMap;

public abstract class BaseCohortStrategy implements CohortStrategy {

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
}
