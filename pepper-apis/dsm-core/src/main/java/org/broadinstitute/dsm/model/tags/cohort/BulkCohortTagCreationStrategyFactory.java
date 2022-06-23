package org.broadinstitute.dsm.model.tags.cohort;

import java.util.Objects;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.model.filter.participant.BaseFilterParticipantList;
import spark.QueryParamsMap;

public class BulkCohortTagCreationStrategyFactory {
    private BulkCohortTagPayload bulkCohortTagPayload;
    private QueryParamsMap queryMap;
    private DDPInstanceDto ddpInstanceDto;
    private BaseFilterParticipantList filter;

    public BulkCohortTagCreationStrategyFactory(QueryParamsMap queryMap, DDPInstanceDto ddpInstanceDto,
                                                BulkCohortTagPayload bulkCohortTagPayload, BaseFilterParticipantList filter) {
        this.bulkCohortTagPayload = bulkCohortTagPayload;
        this.queryMap = queryMap;
        this.ddpInstanceDto = ddpInstanceDto;
        this.filter = filter;
    }

    public CohortStrategy instance() {
        CohortStrategy strategy = new FilteredOrAllPatientsCohortStrategy(queryMap, ddpInstanceDto, bulkCohortTagPayload, filter);
        if (isSelectedPatients()) {
            strategy = new SelectedPatientsCohortStrategy(queryMap, ddpInstanceDto, bulkCohortTagPayload, filter);
        }
        return strategy;
    }

    private boolean isSelectedPatients() {
        return Objects.nonNull(bulkCohortTagPayload.getSelectedPatients()) && bulkCohortTagPayload.getSelectedPatients().size() > 0;
    }
}
