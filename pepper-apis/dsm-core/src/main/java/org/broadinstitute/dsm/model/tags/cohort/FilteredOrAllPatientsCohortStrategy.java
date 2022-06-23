package org.broadinstitute.dsm.model.tags.cohort;

import java.util.List;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.filter.participant.BaseFilterParticipantList;
import spark.QueryParamsMap;

public class FilteredOrAllPatientsCohortStrategy extends BaseCohortStrategy {
    public FilteredOrAllPatientsCohortStrategy(QueryParamsMap queryMap,
                                               DDPInstanceDto ddpInstanceDto,
                                               BulkCohortTagPayload bulkCohortTagPayload,
                                               BaseFilterParticipantList filter) {
        super(queryMap, ddpInstanceDto, bulkCohortTagPayload, filter);
    }

    @Override
    public List<CohortTag> create() {
        return null;
    }
}
