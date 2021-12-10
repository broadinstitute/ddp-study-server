package org.broadinstitute.dsm.model.filter.participant;

import java.util.List;

import org.broadinstitute.dsm.model.participant.ParticipantWrapper;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.util.PatchUtil;
import spark.QueryParamsMap;

public class ManualFilterParticipantList extends BaseFilterParticipantList {


    public ManualFilterParticipantList(String json) {
        super(json);
    }

    @Override
    public ParticipantWrapperResult filter(QueryParamsMap queryParamsMap) {
        prepareNeccesaryData(queryParamsMap);
        return filterParticipantList(filters, PatchUtil.getColumnNameMap(), ddpInstance);
    }

}
