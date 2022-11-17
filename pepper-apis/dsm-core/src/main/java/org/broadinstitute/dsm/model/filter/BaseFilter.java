package org.broadinstitute.dsm.model.filter;

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
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import spark.QueryParamsMap;

import java.io.IOException;
import java.util.Objects;

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
        if (!Objects.isNull(jsonBody) && StringUtils.isNotBlank(jsonBody)) {

            ViewFilter requestForFiltering;
            try {
                requestForFiltering =
                        StringUtils.isNotBlank(jsonBody) ? ObjectMapperSingleton.instance().readValue(jsonBody, ViewFilter.class) : null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (requestForFiltering != null) {
                if (requestForFiltering.getFilters() == null && StringUtils.isNotBlank(requestForFiltering.getFilterQuery())) {
                    throw new RuntimeException("Removed too much logic");
                }
                filters = requestForFiltering.getFilters();
            }
            quickFilterName = requestForFiltering == null ? null : requestForFiltering.getQuickFilterName();
        }
        if ((filters != null && filters.length < 1 && savedFilters != null) || (filters == null && savedFilters != null)) {
            filters = savedFilters;
        }
        if (queryParamsMap.hasKey(RequestParameter.FILTER_NAME)) {
            quickFilterName = queryParamsMap.get(RequestParameter.FILTER_NAME).value();
            if (StringUtils.isNotBlank(quickFilterName)) {
                filterQuery = ViewFilter.getFilterQuery(quickFilterName, parent);
                filters = ViewFilter.parseQueryToViewFilterObject(filterQuery, new ViewFilter()).getFilters();
            }
        }

        if (queryParamsMap.hasKey(LIST_RANGE_FROM)) {
            this.from = NumberUtils.toInt(queryParamsMap.get(LIST_RANGE_FROM).value(), ElasticSearchUtil.DEFAULT_FROM);
        }
        if (queryParamsMap.hasKey(LIST_RANGE_TO)) {
            this.to = NumberUtils.toInt(queryParamsMap.get(LIST_RANGE_TO).value(), ElasticSearchUtil.MAX_RESULT_SIZE);
        }
        if (queryParamsMap.hasKey(SortBy.SORT_BY)) {
            this.sortBy = ObjectMapperSingleton.readValue(queryParamsMap.get(SortBy.SORT_BY).value(), new TypeReference<SortBy>() {
            });
        }
    }

    public void setFrom(int from) {
        this.from = from;
    }

    public void setTo(int to) {
        this.to = to;
    }
}
