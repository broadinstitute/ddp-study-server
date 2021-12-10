package org.broadinstitute.dsm.model.filter.participant;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.participant.ParticipantWrapper;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.PatchUtil;
import spark.QueryParamsMap;

public class QuickFilterParticipantList extends BaseFilterParticipantList {


    @Override
    public ParticipantWrapperResult filter(QueryParamsMap queryParamsMap) {
        prepareNeccesaryData(queryParamsMap);
        ParticipantWrapperResult participantWrapperResult = new ParticipantWrapperResult();
        String filterName = queryParamsMap.get(RequestParameter.FILTER_NAME).value();
        if (StringUtils.isBlank(filterName)) return participantWrapperResult;
        ViewFilter requestForFiltering = new ViewFilter(filterName, parent);
        requestForFiltering.setFilterQuery(ViewFilter.getFilterQuery(filterName, parent));
        if (requestForFiltering.getFilters() == null && StringUtils.isNotBlank(requestForFiltering.getFilterQuery())) {
            requestForFiltering = ViewFilter.parseFilteringQuery(requestForFiltering.getFilterQuery(), requestForFiltering);
        }
        Filter[] filters = requestForFiltering.getFilters();
        if (filters != null) {
            participantWrapperResult = filterParticipantList(filters, PatchUtil.getColumnNameMap(), ddpInstance);
        }
        return participantWrapperResult;
    }
}
