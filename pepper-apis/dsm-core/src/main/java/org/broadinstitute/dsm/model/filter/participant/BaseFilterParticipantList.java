package org.broadinstitute.dsm.model.filter.participant;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.query.AbstractQueryBuilderFactory;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseAbstractQueryBuilder;
import org.broadinstitute.dsm.model.elastic.search.Deserializer;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.filter.BaseFilter;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.filter.prefilter.BasicPreFilterQueryProcessor;
import org.broadinstitute.dsm.model.participant.ParticipantWrapper;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;

public abstract class BaseFilterParticipantList extends BaseFilter implements Filterable<ParticipantWrapperResult> {

    private static final Logger logger = LoggerFactory.getLogger(BaseFilterParticipantList.class);
    public static final String PARTICIPANT_DATA = "participantData";
    protected static final Gson GSON = new Gson();
    public Deserializer deserializer = null;

    public BaseFilterParticipantList() {
        super(null);
    }

    public BaseFilterParticipantList(String jsonBody) {
        super(jsonBody);
    }

    @Override
    public ParticipantWrapperResult filter(QueryParamsMap queryParamsMap, Deserializer deserializer) {
        this.deserializer = deserializer;

        return filter(queryParamsMap);
    }


    protected ParticipantWrapperResult filterParticipantList(Filter[] filters, Map<String, DBElement> columnNameMap) {
        Map<String, String> queryConditions = new HashMap<>();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        ParticipantWrapperPayload.Builder participantWrapperPayload =
                new ParticipantWrapperPayload.Builder().withDdpInstanceDto(ddpInstanceDto).withFrom(from).withTo(to).withSortBy(sortBy);
        ElasticSearch elasticSearch = new ElasticSearch();
        if (deserializer != null) {
            elasticSearch.setDeserializer(deserializer);
        }
        if (filters != null && columnNameMap != null && !columnNameMap.isEmpty()) {
            for (Filter filter : filters) {
                if (filter != null) {
                    String tableAlias = null;
                    if (filter.getParticipantColumn() != null) { // profile.firstName
                        if (PARTICIPANT_DATA.equals(filter.getParticipantColumn().getTableAlias()) || PARENT_PARTICIPANT_LIST.equals(
                                filter.getParentName())) {
                            tableAlias = StringUtils.isNotBlank(filter.getParticipantColumn().getTableAlias())
                                    ? filter.getParticipantColumn().getTableAlias() : filter.getParentName();
                            filter.setParentName(tableAlias);
                        } else {
                            tableAlias = StringUtils.isNotBlank(filter.getParentName()) ? filter.getParentName() :
                                    filter.getParticipantColumn().getTableAlias();
                        }
                    }
                    String tmpName = null;
                    DBElement dbElement = null;
                    if (filter.getFilter1() != null && StringUtils.isNotBlank(filter.getFilter1().getName())) {
                        tmpName = filter.getFilter1().getName();
                    } else if (filter.getFilter2() != null && StringUtils.isNotBlank(filter.getFilter2().getName())) {
                        tmpName = filter.getFilter2().getName();
                    }
                    if (StringUtils.isNotBlank(tmpName)) {
                        if (PARTICIPANT_DATA.equals(filter.getParticipantColumn().getTableAlias())) {
                            addParticipantDataQueryToQueryConditions(queryConditions, filter, tmpName);
                        } else {
                            dbElement = columnNameMap.get(tableAlias + "." + tmpName);
                            ViewFilter.addQueryCondition(queryConditions, dbElement, filter);
                        }
                    }
                }
            }
        }

        if (isStudyPreFilterPresent(ddpInstanceDto)) {
            queryConditions = updateQueryConditions(queryConditions, ddpInstanceDto);
        }

        if (!queryConditions.isEmpty()) {
            // combine queries
            Map<String, String> mergeConditions = new HashMap<>();
            for (String filter : queryConditions.keySet()) {
                if (DBConstants.DDP_PARTICIPANT_EXIT_ALIAS.equals(filter)) {
                    String exitFilter = queryConditions.get(filter).replace("ex.", "p.");
                    mergeConditions.merge(DBConstants.DDP_PARTICIPANT_ALIAS, exitFilter, String::concat);
                } else {
                    mergeConditions.merge(filter, queryConditions.get(filter), String::concat);
                }
            }
            logger.info("Found query conditions for " + mergeConditions.size() + " tables");
            return new ParticipantWrapper(participantWrapperPayload.withFilter(mergeConditions).build(), elasticSearch).getFilteredList();
        } else {
            return new ParticipantWrapper(participantWrapperPayload.build(), elasticSearch).getFilteredList();
        }
    }

    private static Map<String, String> updateQueryConditions(Map<String, String> queryConditions, DDPInstanceDto ddpInstanceDto) {
        return new BasicPreFilterQueryProcessor(queryConditions)
                .update(ddpInstanceDto.getQueryItems());
    }

    private static boolean isStudyPreFilterPresent(DDPInstanceDto ddpInstanceDto) {
        return StringUtils.isNotBlank(ddpInstanceDto.getQueryItems());
    }

    private void addParticipantDataQueryToQueryConditions(Map<String, String> queryConditions, Filter filter, String tmpName) {
        DBElement dbElement = new DBElement(DBConstants.DDP_PARTICIPANT_DATA, DBConstants.DDP_PARTICIPANT_DATA_ALIAS, null,
                DBConstants.ADDITIONAL_VALUES_JSON);
        if (isDateRange(filter)) {
            filter.getFilter1().setName(ESObjectConstants.ADDITIONAL_VALUES_JSON);
            filter.getFilter2().setName(CamelCaseConverter.of(tmpName).convert());
            filter.setNotEmpty(false);
            filter.setParentName(DBConstants.DDP_PARTICIPANT_DATA_ALIAS);
            filter.setType(Filter.ADDITIONAL_VALUES);
            Filter.getQueryStringForFiltering(filter, dbElement);
        } else {
            filter.setFilter1(new NameValue(ESObjectConstants.ADDITIONAL_VALUES_JSON, filter.getFilter1().getValue()));
            filter.setFilter2(new NameValue(CamelCaseConverter.of(tmpName).convert(), null));
            filter.setParentName(DBConstants.DDP_PARTICIPANT_DATA_ALIAS);
            filter.setType(Filter.ADDITIONAL_VALUES);
        }
        if (Objects.nonNull(filter.getSelectedOptions()) && filter.getSelectedOptions().length > 0) {
            for (String selectedOption : filter.getSelectedOptions()) {
                filter.getFilter1().setValue(selectedOption);
                filter.getFilter2().setName(CamelCaseConverter.of(tmpName).convert());
                String filterQuery = Filter.OR_TRIMMED + Filter.getQueryStringForFiltering(filter, dbElement).trim()
                        .substring(AndOrFilterSeparator.MINIMUM_STEP_FROM_OPERATOR);
                queryConditions.merge(DBConstants.DDP_PARTICIPANT_DATA_ALIAS, filterQuery,
                        (prev, curr) -> String.join(Filter.SPACE, prev, curr));
            }
        } else {
            queryConditions.put(DBConstants.DDP_PARTICIPANT_DATA_ALIAS, Filter.getQueryStringForFiltering(filter, dbElement));
        }
    }

    public static boolean isDateRange(Filter filter) {
        try {
            LocalDate.parse(String.valueOf(filter.getFilter1().getValue()));
            LocalDate.parse(String.valueOf(filter.getFilter2().getValue()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static AbstractQueryBuilder<?> createMixedSourceBaseAbstractQueryBuilder(String filterQuery, Integer ddpInstanceId) {
        BaseAbstractQueryBuilder abstractQueryBuilder = AbstractQueryBuilderFactory.create(filterQuery);
        abstractQueryBuilder.setDdpInstanceId(ddpInstanceId);
        abstractQueryBuilder.setParser(new FilterParser());
        return abstractQueryBuilder.build();
    }

}
