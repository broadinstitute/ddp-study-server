package org.broadinstitute.dsm.model.tags.cohort;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.model.filter.participant.BaseFilterParticipantList;
import spark.QueryParamsMap;

public abstract class BaseCohortStrategy implements CohortStrategy {

    protected QueryParamsMap queryMap;
    protected DDPInstanceDto ddpInstanceDto;
    protected BulkCohortTagPayload bulkCohortTagPayload;
    protected BaseFilterParticipantList filter;

    public BaseCohortStrategy(QueryParamsMap queryMap, DDPInstanceDto ddpInstanceDto,
                              BulkCohortTagPayload bulkCohortTagPayload, BaseFilterParticipantList filter) {
        this.queryMap = queryMap;
        this.ddpInstanceDto = ddpInstanceDto;
        this.bulkCohortTagPayload = bulkCohortTagPayload;
        this.filter = filter;
    }
}
