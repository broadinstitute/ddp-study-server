package org.broadinstitute.dsm.model.filter.tissue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.TissueList;
import org.broadinstitute.dsm.model.TissueListWrapper;
import org.broadinstitute.dsm.model.filter.BaseFilter;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseFilterTissueList extends BaseFilter implements Filterable<List<TissueListWrapper>> {

    private static final Logger logger = LoggerFactory.getLogger(BaseFilterTissueList.class);

    public BaseFilterTissueList(String jsonBody) {
        super(jsonBody);
    }



    protected List<TissueListWrapper> filterTissueList(Filter[] filters, Map<String, DBElement> columnNameMap, String filterName,
                                                     @NonNull DDPInstance instance, String filterQuery) {
        Map<String, String> queryConditions = new HashMap<>();
        String subQueryForFiltering = "";
        if (filters != null && !columnNameMap.isEmpty()) {
            for (Filter filter : filters) {
                if (filter != null) {
                    DBElement dbElement = columnNameMap.get(filter.getParticipantColumn().tableAlias + "." + filter.getFilter1().getName());
                    ViewFilter.addQueryCondition(queryConditions, dbElement, filter);
                }
            }
        }

        if (StringUtils.isNotBlank(filterQuery) && StringUtils.isBlank(subQueryForFiltering)) {
            subQueryForFiltering = filterQuery;
        }
        //filter now
        if (StringUtils.isNotBlank(subQueryForFiltering)) {
            String[] queryConditionsFilter = subQueryForFiltering.split("(AND)");
            for (String queryCondition : queryConditionsFilter) {
                if (queryCondition.indexOf(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER) > 0
                        || queryCondition.indexOf(DBConstants.DDP_ONC_HISTORY_ALIAS + DBConstants.ALIAS_DELIMITER) > 0
                        || queryCondition.indexOf(DBConstants.DDP_INSTITUTION_ALIAS + DBConstants.ALIAS_DELIMITER) > 0
                        || queryCondition.indexOf(DBConstants.DDP_PARTICIPANT_EXIT_ALIAS + DBConstants.ALIAS_DELIMITER) > 0
                        || queryCondition.indexOf(DBConstants.DDP_PARTICIPANT_ALIAS + DBConstants.ALIAS_DELIMITER) > 0
                        || queryCondition.indexOf(DBConstants.DDP_MEDICAL_RECORD_ALIAS + DBConstants.ALIAS_DELIMITER) > 0
                        || queryCondition.indexOf(DBConstants.DDP_KIT_REQUEST_ALIAS + DBConstants.ALIAS_DELIMITER) > 0
                        || queryCondition.indexOf(DBConstants.DDP_ABSTRACTION_ALIAS + DBConstants.ALIAS_DELIMITER) > 0) {
                    throw new RuntimeException(" Filtering for pt or medical record is not allowed in Tissue List");
                } else if (queryCondition.indexOf(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER) > 0) {
                    queryConditions.put(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS, queryCondition);
                } else if (queryCondition.indexOf(DBConstants.DDP_TISSUE_ALIAS + DBConstants.ALIAS_DELIMITER) > 0) {
                    queryConditions.put(DBConstants.DDP_TISSUE_ALIAS, queryCondition);
                } else {
                    queryConditions.put("ES", queryCondition);
                }
            }
        }

        if (!queryConditions.isEmpty()) {
            logger.info("Found query conditions for " + queryConditions.size() + " tables");
        }
        String queryString = "";
        for (String key : queryConditions.keySet()) {
            if (!"ES".equals(key)) {
                queryString += queryConditions.get(key);
            }
        }
        queryString += subQueryForFiltering;
        return getListBasedOnFilterName(filterName, instance.getName(), TISSUE_LIST_PARENT, queryString, queryConditions);
    }

    protected List<TissueListWrapper> getListBasedOnFilterName(String filterName, String realm, String parent, String queryString, Map<String, String> filters) {
        List<TissueListWrapper> wrapperList = new ArrayList<>();
        if ("tissueList".equals(parent)) {
            DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);
            String subQueryForFiltering = "";
            if (StringUtils.isNotBlank(filterName)) {
                if (filterName.equals(ViewFilter.DESTROYING_FILTERS)) {
                    List<TissueList> tissueLists = ViewFilter.getDestroyingSamples(instance.getName());
                    wrapperList = TissueListWrapper.getTissueListData(instance, filters, tissueLists);
                    return wrapperList;
                }
                subQueryForFiltering = ViewFilter.getFilterQuery(filterName, parent);
            }
            String[] tableFilters = subQueryForFiltering.split("(AND)");
            String query = " ";
            for (String filter : tableFilters) {
                if (StringUtils.isNotBlank(filter)) {
                    if (!filter.contains(ElasticSearchUtil.PROFILE + DBConstants.ALIAS_DELIMITER) && !filter.contains(ElasticSearchUtil.DATA + DBConstants.ALIAS_DELIMITER)) {
                        query += "AND " + filter + " ";
                    } else {
                        if (filters == null) {
                            filters = new HashMap<>();
                        }
                        String q = filters.getOrDefault("ES", " ");
                        filters.put("ES", q + " " + filter);
                    }
                }
            }
            //TODO (2021-07-29) -> if queryString is made by ElasticSearch fields `TissueList.getAllTissueListsForRealm` throws exception
            List<TissueList> tissueLists = TissueList.getAllTissueListsForRealm(realm, TissueList.SQL_SELECT_ALL_ONC_HISTORY_TISSUE_FOR_REALM + (queryString != null ? queryString : "") + query);
            wrapperList = TissueListWrapper.getTissueListData(instance, filters, tissueLists);
            return wrapperList;
        }
        return wrapperList;
    }

}
