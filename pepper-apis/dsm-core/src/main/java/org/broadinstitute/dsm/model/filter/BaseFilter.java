package org.broadinstitute.dsm.model.filter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.sort.SortBy;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
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
    protected int from;
    protected int to;
    protected SortBy sortBy;

    public BaseFilter(String jsonBody) {
        this.jsonBody = jsonBody;
    }

    protected void prepareNecessaryData(QueryParamsMap queryParamsMap) {

        parent = Objects.requireNonNull(queryParamsMap).get(DBConstants.FILTER_PARENT).value();
        if (StringUtils.isBlank(parent)) {
            throw new RuntimeException("parent is necessary");
        }
        realm = queryParamsMap.get(RoutePath.REALM).value();
        if (StringUtils.isBlank(realm)) {
            throw new RuntimeException("realm is necessary");
        }
        filterQuery = "";
        quickFilterName = "";
        Filter[] savedFilters = new Gson().fromJson(queryParamsMap.get(RequestParameter.FILTERS).value(), Filter[].class);
        if (!Objects.isNull(jsonBody)) {

            ViewFilter requestForFiltering;
            try {
                requestForFiltering =
                        StringUtils.isNotBlank(jsonBody) ? ObjectMapperSingleton.instance().readValue(jsonBody, ViewFilter.class) : null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

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
        this.from = NumberUtils.toInt(queryParamsMap.get(LIST_RANGE_FROM).value(), 0);
        this.to = NumberUtils.toInt(queryParamsMap.get(LIST_RANGE_TO).value(), 10000);
        if (queryParamsMap.hasKey(SortBy.SORT_BY)) {
            this.sortBy = ObjectMapperSingleton.readValue(queryParamsMap.get(SortBy.SORT_BY).value(), new TypeReference<SortBy>() {
            });
        }
    }

}
