package org.broadinstitute.dsm.model.filter.participant;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.PatchUtil;
import spark.QueryParamsMap;

public class SavedFilterParticipantList extends BaseFilterParticipantList {

    @Override
    public ParticipantWrapperResult filter(QueryParamsMap queryParamsMap) {
        prepareNecessaryData(queryParamsMap);
        ParticipantWrapperResult participantWrapperResult = new ParticipantWrapperResult();
        if (StringUtils.isBlank(queryParamsMap.get(RequestParameter.FILTERS).value())) {
            return participantWrapperResult;
        }
        Filter[] filters = GSON.fromJson(queryParamsMap.get(RequestParameter.FILTERS).value(), Filter[].class);
        return filterParticipantList(filters, PatchUtil.getColumnNameMap());
    }
}
