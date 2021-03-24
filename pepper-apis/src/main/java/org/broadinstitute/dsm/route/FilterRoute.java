package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantWrapper;
import org.broadinstitute.dsm.model.TissueList;
import org.broadinstitute.dsm.model.TissueListWrapper;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

public class FilterRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(FilterRoute.class);

    public static final String PARENT_PARTICIPANT_LIST = "participantList";
    public static final String TISSUE_LIST_PARENT = "tissueList";

    private PatchUtil patchUtil;

    public FilterRoute(@NonNull PatchUtil patchUtil) {
        this.patchUtil = patchUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (patchUtil.getColumnNameMap() == null) {
            throw new RuntimeException("ColumnNameMap is null!");
        }
        QueryParamsMap queryParams = request.queryMap();
        String parent = null;
        if (queryParams.value(DBConstants.FILTER_PARENT) != null) {
            parent = queryParams.get(DBConstants.FILTER_PARENT).value();
        }
        String realm = null;
        DDPInstance instance = null;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
            instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);
        }
        else {
            throw new RuntimeException("No realm is sent!");
        }
        if (UserUtil.checkUserAccess(realm, userId, "mr_view") || UserUtil.checkUserAccess(realm, userId, "pt_list_view")) {
            String json = request.body();
            String userIdRequest = null;
            if (queryParams.value(UserUtil.USER_ID) != null) {
                userIdRequest = queryParams.get(UserUtil.USER_ID).value();
                if (!userId.equals(userIdRequest)) {
                    throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
                }
            }
            if (request.url().contains(RoutePath.APPLY_FILTER)) {
                String filterQuery = queryParams.get("filterQuery").value();
                if (filterQuery != null) {
                    filterQuery = " " + ViewFilter.changeFieldsInQuery(filterQuery, false);
                    if (StringUtils.isBlank(parent)) {
                        throw new RuntimeException("No parent was sent in the request.");
                    }
                    // return pt list as stream // return getListBasedOnFilterName(null, realm, parent, filterQuery, null);
                    streamResponse(response, getListBasedOnFilterName(null, realm, parent, filterQuery, null));
                    return "";
                }
                else {
                    String filterName = queryParams.get(RequestParameter.FILTER_NAME).value();

                    if (StringUtils.isBlank(parent)) {
                        throw new RuntimeException("No parent was sent in the request.");
                    }
                    if (PARENT_PARTICIPANT_LIST.equals(parent)) {
                        Filter[] filters = null;
                        if (request.url().contains(RoutePath.FILTER_LIST)) {
                            String defaultFilter = null;
                            if (queryParams.value("defaultFilter") != null) {
                                defaultFilter = queryParams.get("defaultFilter").value();
                            }
                            if (StringUtils.isNotBlank(defaultFilter)) {
                                if ("1".equals(defaultFilter)) {
                                    String userEmail = queryParams.value("userMail");
                                    String defaultFilterName = ViewFilter.getDefaultFilterForUser(userEmail, "tissueList");
                                    if (StringUtils.isNotBlank(defaultFilterName)) {
                                        // return pt list as stream // return doFiltering(json, instance, defaultFilterName, parent, null);
                                        streamResponse(response, doFiltering(json, instance, defaultFilterName, parent, null));
                                        return "";
                                    }
                                }
                            }
                        }
                        if (StringUtils.isNotBlank(filterName)) {
                            //quick filter name ptL
                            ViewFilter requestForFiltering = new ViewFilter(filterName, parent);
                            requestForFiltering.setFilterQuery(ViewFilter.getFilterQuery(filterName, parent));
                            if (requestForFiltering.getFilters() == null && StringUtils.isNotBlank(requestForFiltering.getFilterQuery())) {
                                requestForFiltering = ViewFilter.parseFilteringQuery(requestForFiltering.getFilterQuery(), requestForFiltering);
                            }
                            filters = requestForFiltering.getFilters();
                        }
                        else {
                            //saved filter ptL
                            if (StringUtils.isNotBlank(queryParams.get(RequestParameter.FILTERS).value())) {
                                filters = new Gson().fromJson(queryParams.get(RequestParameter.FILTERS).value(), Filter[].class);
                            }
                        }
                        if (filters != null) {
                            //quick and saved filter ptL
                            // return pt list as stream // return filterParticipantList(filters, patchUtil.getColumnNameMap(), instance);
                            streamResponse(response, filterParticipantList(filters, patchUtil.getColumnNameMap(), instance));
                            return "";
                        }
                        //empty manual search
                        // return pt list as stream // return ParticipantWrapper.getFilteredList(instance, null);
                        streamResponse(response, ParticipantWrapper.getFilteredList(instance, null));
                        return "";
                    }
                    else {
                        Filter[] filters = null;
                        if (StringUtils.isBlank(filterName)) {
                            filters = new Gson().fromJson(queryParams.get(RequestParameter.FILTERS).value(), Filter[].class);
                        }
                        // return list as stream // return doFiltering(null, instance, filterName, parent, filters);
                        streamResponse(response, doFiltering(null, instance, filterName, parent, filters));
                        return "";
                    }
                }
            }
            else if (request.url().contains(RoutePath.GET_PARTICIPANT)) {
                String ddpParticipantId = "";
                if (queryParams.value(RoutePath.ddpParticipantId) != null) {
                    ddpParticipantId = queryParams.get(RoutePath.ddpParticipantId).value();
                }
                else {
                    throw new RuntimeException("No Participant Id was sent");
                }
                // get pt for selected tissue (@ tissue list)
                Map<String, String> queryConditions = new HashMap<>();
                queryConditions.put("p", " AND p.ddp_participant_id = '" + ddpParticipantId + "'");
                queryConditions.put("ES", ElasticSearchUtil.BY_GUID + ddpParticipantId);
                List<ParticipantWrapper> ptList = ParticipantWrapper.getFilteredList(instance, queryConditions);
                if (!ptList.isEmpty()) {
                    return ptList;
                }
                else {
                    queryConditions = new HashMap<>();
                    queryConditions.put("p", " AND p.ddp_participant_id = '" + ddpParticipantId + "'");
                    queryConditions.put("ES", ElasticSearchUtil.BY_LEGACY_ALTPID + ddpParticipantId);
                    return ParticipantWrapper.getFilteredList(instance, queryConditions);
                }
            }
            else if (request.url().contains(RoutePath.FILTER_LIST)) {
                String defaultFilter = null;
                if (queryParams.value(RoutePath.FILTER_DEFAULT) != null) {
                    defaultFilter = queryParams.get(RoutePath.FILTER_DEFAULT).value();
                }
                if (TISSUE_LIST_PARENT.equals(parent) && StringUtils.isNotBlank(defaultFilter)) {
                    List<TissueList> tissueListList = new ArrayList<>();
                    List<TissueListWrapper> wrapperList = null;
                    if ("0".equals(defaultFilter)) {
                        tissueListList = TissueList.getAllTissueListsForRealmNoFilter(realm);
                        wrapperList = TissueListWrapper.getTissueListData(instance, null, tissueListList);
                    }
                    else if ("1".equals(defaultFilter)) {
                        String userEmail = queryParams.value(RequestParameter.USER_MAIL);
                        String defaultFilterName = ViewFilter.getDefaultFilterForUser(userEmail, TISSUE_LIST_PARENT);
                        if (StringUtils.isNotBlank(defaultFilterName)) {
                            wrapperList = (List<TissueListWrapper>) FilterRoute.getListBasedOnFilterName(defaultFilterName, realm, TISSUE_LIST_PARENT, null, null);
                        }
                        else {
                            tissueListList = TissueList.getAllTissueListsForRealmNoFilter(realm);
                            wrapperList = TissueListWrapper.getTissueListData(instance, null, tissueListList);
                        }
                    }
                    logger.info("Found " + wrapperList.size() + " tissues for Tissue View");
                    return wrapperList;
                }
                //manual search
                // return pt list as stream // return doFiltering(json, instance, null, parent, null);
                streamResponse(response, doFiltering(json, instance, null, parent, null));
                return "";
            }
            throw new RuntimeException("Path was not known");
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    public List<?> doFiltering(String json, DDPInstance instance, String filterName, String parent, Filter[] savedFilters) {
        String filterQuery = "";
        Filter[] filters = null;
        String quickFilterName = "";
        if (json != null) {
            ViewFilter requestForFiltering = new Gson().fromJson(json, ViewFilter.class);
            if (requestForFiltering != null) {
                if (requestForFiltering.getFilters() == null && StringUtils.isNotBlank(requestForFiltering.getFilterQuery())) {
                    filterQuery = ViewFilter.changeFieldsInQuery(requestForFiltering.getFilterQuery(), false);
                    requestForFiltering = ViewFilter.parseFilteringQuery(filterQuery, requestForFiltering);
                }
                filters = requestForFiltering.getFilters();
            }
            quickFilterName = requestForFiltering == null ? null : requestForFiltering.getQuickFilterName();
        }
        else if (savedFilters != null) {
            filters = savedFilters;
        }

        List<?> data;
        if (PARENT_PARTICIPANT_LIST.equals(parent)) {
            //manual search and search bar ptL
            data = filterParticipantList(filters, patchUtil.getColumnNameMap(), instance);
        }
        else {
            data = filterTissueList(filters, patchUtil.getColumnNameMap(), filterName == null ? quickFilterName : filterName, instance, filterQuery);
        }
        logger.info("Returning a filtered list with size " + data.size());
        return data;
    }

    public static List<?> filterParticipantList(Filter[] filters, Map<String, DBElement> columnNameMap, @NonNull DDPInstance instance) {
        Map<String, String> queryConditions = new HashMap<>();
        if (filters != null && columnNameMap != null && !columnNameMap.isEmpty()) {
            for (Filter filter : filters) {
                if (filter != null) {
                    String tmp = StringUtils.isNotBlank(filter.getParentName()) ? filter.getParentName() : filter.getParticipantColumn().getTableAlias();
                    String tmpName = null;
                    DBElement dbElement = null;
                    if (filter.getFilter1() != null && StringUtils.isNotBlank(filter.getFilter1().getName())) {
                        tmpName = filter.getFilter1().getName();
                    }
                    else if (filter.getFilter2() != null && StringUtils.isNotBlank(filter.getFilter2().getName())) {
                        tmpName = filter.getFilter2().getName();
                    }
                    if (StringUtils.isNotBlank(tmpName)) {
                        dbElement = columnNameMap.get(tmp + "." + tmpName);
                    }
                    ViewFilter.addQueryCondition(queryConditions, dbElement, filter);
                }
            }
        }

        if (!queryConditions.isEmpty()) {
            //combine queries
            Map<String, String> mergeConditions = new HashMap<>();
            for (String filter : queryConditions.keySet()) {
                if (DBConstants.DDP_PARTICIPANT_RECORD_ALIAS.equals(filter)
                        || DBConstants.DDP_PARTICIPANT_EXIT_ALIAS.equals(filter) || DBConstants.DDP_ONC_HISTORY_ALIAS.equals(filter)) {
                    mergeConditions.merge(DBConstants.DDP_PARTICIPANT_ALIAS, queryConditions.get(filter), String::concat);
                }
                else if (DBConstants.DDP_INSTITUTION_ALIAS.equals(filter)) {
                    mergeConditions.merge(DBConstants.DDP_MEDICAL_RECORD_ALIAS, queryConditions.get(filter), String::concat);
                }
                else if (DBConstants.DDP_TISSUE_ALIAS.equals(filter)) {
                    mergeConditions.merge(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS, queryConditions.get(filter), String::concat);
                }
                else {
                    mergeConditions.merge(filter, queryConditions.get(filter), String::concat);
                }
            }

            logger.info("Found query conditions for " + mergeConditions.size() + " tables");
            //search bar ptL
            return ParticipantWrapper.getFilteredList(instance, mergeConditions);
        }
        else {
            return ParticipantWrapper.getFilteredList(instance, null);
        }
    }

    public static List<?> filterTissueList(Filter[] filters, Map<String, DBElement> columnNameMap, String filterName,
                                           @NonNull DDPInstance instance, String filterQuery) {
        Map<String, String> queryConditions = new HashMap<>();
        String subQueryForFiltering = "";
        if (filters != null && !columnNameMap.isEmpty()) {
            for (Filter filter : filters) {
                if (filter != null) {
                    DBElement dbElement = columnNameMap.get(filter.participantColumn.tableAlias + "." + filter.getFilter1().getName());
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
                }
                else if (queryCondition.indexOf(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER) > 0) {
                    queryConditions.put(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS, queryCondition);
                }
                else if (queryCondition.indexOf(DBConstants.DDP_TISSUE_ALIAS + DBConstants.ALIAS_DELIMITER) > 0) {
                    queryConditions.put(DBConstants.DDP_TISSUE_ALIAS, queryCondition);
                }
                else {
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

    public static List<?>   getListBasedOnFilterName(String filterName, String realm, String parent, String queryString, Map<String, String> filters) {
        if (TISSUE_LIST_PARENT.equals(parent)) {
            DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);
            String subQueryForFiltering = "";
            if (StringUtils.isNotBlank(filterName)) {
                if (filterName.equals(ViewFilter.DESTROYING_FILTERS)) {
                    List<TissueList> tissueLists = ViewFilter.getDestroyingSamples(instance.getName());
                    List<TissueListWrapper> wrapperList = TissueListWrapper.getTissueListData(instance, filters, tissueLists);
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
                    }
                    else {
                        if (filters == null) {
                            filters = new HashMap<>();
                        }
                        String q = filters.getOrDefault("ES", " ");
                        filters.put("ES", q + " " + filter);
                    }
                }
            }
            List<TissueList> tissueLists = TissueList.getAllTissueListsForRealm(realm, TissueList.SQL_SELECT_ALL_ONC_HISTORY_TISSUE_FOR_REALM + (queryString != null ? queryString : "") + query);
            List<TissueListWrapper> wrapperList = TissueListWrapper.getTissueListData(instance, filters, tissueLists);
            return wrapperList;
        }
        return null;
    }

    private void streamResponse(@NonNull Response response, List<?> result) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(response.raw().getOutputStream());
        new Gson().toJson(result, writer);
        writer.flush();
        writer.close();
    }
}
