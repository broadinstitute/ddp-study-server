package org.broadinstitute.dsm.model.filter;

import java.util.Objects;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import spark.QueryParamsMap;

public class BaseFilter {

    public static final String PARENT_PARTICIPANT_LIST = "participantList";
    public static final String TISSUE_LIST_PARENT = "tissueList";
    public static final String LIST_RANGE_FROM = "from";
    public static final String LIST_RANGE_TO = "to";
    protected Filter[] filters;
    protected String quickFilterName;
    protected String filterQuery;
    protected String jsonBody;
    protected String parent;
    protected String realm;
    protected DDPInstance ddpInstance;
    protected int from;
    protected int to;

    public BaseFilter(String jsonBody) {
        this.jsonBody = jsonBody;
    }

    protected void prepareNeccesaryData(QueryParamsMap queryParamsMap) {
        parent = Objects.requireNonNull(queryParamsMap).get(DBConstants.FILTER_PARENT).value();
        if (StringUtils.isBlank(parent)) throw new RuntimeException("parent is necessary");
        realm = queryParamsMap.get(RoutePath.REALM).value();
        if (StringUtils.isBlank(realm)) throw new RuntimeException("realm is necessary");
        ddpInstance = DDPInstance.getDDPInstance(realm);
        filterQuery = "";
        quickFilterName = "";
        Filter[] savedFilters = new Gson().fromJson(queryParamsMap.get(RequestParameter.FILTERS).value(), Filter[].class);
        if (!Objects.isNull(jsonBody)) {
            ViewFilter requestForFiltering = new Gson().fromJson(jsonBody, ViewFilter.class);
            if (requestForFiltering != null) {
                if (requestForFiltering.getFilters() == null && StringUtils.isNotBlank(requestForFiltering.getFilterQuery())) {
                    filterQuery = ViewFilter.changeFieldsInQuery(requestForFiltering.getFilterQuery(), false);
                    requestForFiltering = ViewFilter.parseFilteringQuery(filterQuery, requestForFiltering);
                }
                filters = requestForFiltering.getFilters();
            }
            quickFilterName = requestForFiltering == null ? null : requestForFiltering.getQuickFilterName();
        } else if (savedFilters != null) {
            filters = savedFilters;
        }
        this.from = Integer.parseInt(queryParamsMap.get(LIST_RANGE_FROM).value());
        this.to = Integer.parseInt(queryParamsMap.get(LIST_RANGE_TO).value());
    }

}
