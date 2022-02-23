package org.broadinstitute.dsm.model.filter.participant;

import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.util.PatchUtil;
import spark.QueryParamsMap;

public class EmptyFilterParticipantList extends BaseFilterParticipantList {

    @Override
    public ParticipantWrapperResult filter(QueryParamsMap queryParamsMap) {
        prepareNecessaryData(queryParamsMap);
        return filterParticipantList(filters, PatchUtil.getColumnNameMap());
    }
}
