package org.broadinstitute.dsm.model.filter;

import java.util.Collections;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.filter.participant.EmptyFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.ManualFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.QuickFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.SavedFilterParticipantList;
import org.broadinstitute.dsm.model.filter.tissue.ManualFilterTissueList;
import org.broadinstitute.dsm.model.filter.tissue.SavedFiltersTissueList;
import org.broadinstitute.dsm.model.filter.tissue.SearchBarFilterTissueList;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import spark.QueryParamsMap;
import spark.Request;

public class FilterFactory {


    public static Filterable of(Request request) {
        QueryParamsMap queryParams = Objects.requireNonNull(request).queryMap();
        String parent = queryParams.get(DBConstants.FILTER_PARENT).value();
        if (StringUtils.isBlank(parent)) throw new IllegalArgumentException("parent cannot be empty");
        String[] pathSegments = request.uri().split("/");
        String lastSegment = pathSegments.length > 0 ? pathSegments[pathSegments.length-1] : "";
        String jsonBody = request.body();
        Filterable filterable = queryParamsMap -> Collections.emptyList();
        switch (lastSegment) {
            case RoutePath.APPLY_FILTER:
                switch (parent) {
                    case BaseFilter
                            .PARENT_PARTICIPANT_LIST:
                        if (StringUtils.isNotBlank(queryParams.get(RequestParameter.FILTER_NAME).value())) {
                            filterable = new QuickFilterParticipantList();
                        } else if (StringUtils.isNotBlank(queryParams.get(RequestParameter.FILTERS).value())){
                            filterable = new SavedFilterParticipantList();
                        } else {
                            filterable = new EmptyFilterParticipantList();
                        }
                        break;
                    case BaseFilter.TISSUE_LIST_PARENT:
                        String filterQuery = queryParams.get("filterQuery").value();
                        if (StringUtils.isNotBlank(filterQuery)) {
                            filterable = new SearchBarFilterTissueList();
                        } else {
                            filterable = new SavedFiltersTissueList(jsonBody);
                        }
                        break;
                    default:
                        break;
                }
                break;
            case RoutePath.FILTER_LIST:
                switch (parent) {
                    case BaseFilter
                            .PARENT_PARTICIPANT_LIST:
                        filterable = new ManualFilterParticipantList(jsonBody);
                        break;
                    case BaseFilter.TISSUE_LIST_PARENT:
                        filterable = new ManualFilterTissueList(jsonBody);
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
        return filterable;
    }


}
