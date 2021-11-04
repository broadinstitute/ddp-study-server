package org.broadinstitute.dsm.model.filter.participant;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDataDto;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.participant.ParticipantWrapper;
import org.broadinstitute.dsm.model.filter.BaseFilter;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseFilterParticipantList extends BaseFilter implements Filterable<ParticipantWrapperResult> {

    private static final Logger logger = LoggerFactory.getLogger(BaseFilterParticipantList.class);
    public static final String PARTICIPANT_DATA = "participantData";
    public static final String OPTIONS = "OPTIONS";
    public static final String RADIO = "RADIO";
    public static final String PARTICIPANTS = "PARTICIPANTS";
    protected static final Gson GSON = new Gson();
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}$");
    private final ParticipantDataDao participantDataDao;

    {
        participantDataDao = new ParticipantDataDao();
    }

    public BaseFilterParticipantList() {
        super(null);
    }

    public BaseFilterParticipantList(String jsonBody) {
        super(jsonBody);
    }


    protected ParticipantWrapperResult filterParticipantList(Filter[] filters, Map<String, DBElement> columnNameMap, @NonNull DDPInstance instance) {
        Map<String, String> queryConditions = new HashMap<>();
        List<ParticipantDataDto> allParticipantData = null;
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        ParticipantWrapperPayload.Builder participantWrapperPayload = new ParticipantWrapperPayload.Builder()
                .withDdpInstanceDto(ddpInstanceDto)
                .withFrom(from)
                .withTo(to);
        ElasticSearch elasticSearch = new ElasticSearch();
        if (filters != null && columnNameMap != null && !columnNameMap.isEmpty()) {
            Map<String, Integer> allIdsForParticipantDataFiltering = new HashMap<>();
            int numberOfParticipantDataFilters = 0;
            for (Filter filter : filters) {
                if (filter != null) {
                    String tmp = null;
                    if (filter.getParticipantColumn() != null) {
                        tmp = StringUtils.isNotBlank(filter.getParentName()) ? filter.getParentName() : filter.getParticipantColumn().getTableAlias();
                    }
                    String tmpName = null;
                    DBElement dbElement = null;
                    if (filter.getFilter1() != null && StringUtils.isNotBlank(filter.getFilter1().getName())) {
                        tmpName = filter.getFilter1().getName();
                    } else if (filter.getFilter2() != null && StringUtils.isNotBlank(filter.getFilter2().getName())) {
                        tmpName = filter.getFilter2().getName();
                    }
                    if (filter.getParticipantColumn() != null && (PARTICIPANT_DATA.equals(filter.getParticipantColumn().tableAlias) || BaseFilter.PARENT_PARTICIPANT_LIST.equals(filter.getParentName()))) {
                        if (allParticipantData == null) {
                            allParticipantData = participantDataDao
                                    .getParticipantDataByInstanceId(Integer.parseInt(instance.getDdpInstanceId()));
                        }
                        numberOfParticipantDataFilters++;
                        addParticipantDataIdsForFilters(filter, tmpName, allParticipantData, allIdsForParticipantDataFiltering);
                    } else {
                        if (StringUtils.isNotBlank(tmpName)) {
                            dbElement = columnNameMap.get(tmp + "." + tmpName);
                        }
                        ViewFilter.addQueryCondition(queryConditions, dbElement, filter);
                    }
                }
            }
            if (numberOfParticipantDataFilters != 0) {
                addParticipantDataConditionsToQuery(allIdsForParticipantDataFiltering, queryConditions, numberOfParticipantDataFilters);
            }
        }

        if (!queryConditions.isEmpty()) {
            //combine queries
            Map<String, String> mergeConditions = new HashMap<>();
            for (String filter : queryConditions.keySet()) {
                if (DBConstants.DDP_PARTICIPANT_RECORD_ALIAS.equals(filter)
                        || DBConstants.DDP_PARTICIPANT_EXIT_ALIAS.equals(filter) || DBConstants.DDP_ONC_HISTORY_ALIAS.equals(filter)) {
                    mergeConditions.merge(DBConstants.DDP_PARTICIPANT_ALIAS, queryConditions.get(filter), String::concat);
                } else if (DBConstants.DDP_INSTITUTION_ALIAS.equals(filter)) {
                    mergeConditions.merge(DBConstants.DDP_MEDICAL_RECORD_ALIAS, queryConditions.get(filter), String::concat);
                } else if (DBConstants.DDP_TISSUE_ALIAS.equals(filter)) {
                    mergeConditions.merge(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS, queryConditions.get(filter), String::concat);
                } else {
                    mergeConditions.merge(filter, queryConditions.get(filter), String::concat);
                }
            }

            logger.info("Found query conditions for " + mergeConditions.size() + " tables");
            //search bar ptL

            return new ParticipantWrapper(participantWrapperPayload.withFilter(mergeConditions).build(), elasticSearch).getFilteredList();
        } else {
            return new ParticipantWrapper(participantWrapperPayload.build(), elasticSearch).getFilteredList();
        }
    }

    public void addParticipantDataConditionsToQuery(Map<String, Integer> allIdsForParticipantDataFiltering, Map<String, String> queryConditions, int filtersLength) {
        String newCondition = createNewConditionByIds(allIdsForParticipantDataFiltering, filtersLength);
        queryConditions.merge(ElasticSearchUtil.ES, newCondition, (prev, next) -> prev + next);
    }

    public String createNewConditionByIds(Map<String, Integer> allIdsForParticipantDataFiltering, int filtersLength) {
        StringBuilder newCondition = new StringBuilder(ElasticSearchUtil.AND);
        int i = 0;
        for (Map.Entry<String, Integer> entry: allIdsForParticipantDataFiltering.entrySet()) {
            if (entry.getValue() != filtersLength) {
                continue;
            }
            if (i == 0) {
                newCondition.append(ParticipantUtil.isGuid(entry.getKey()) ? ElasticSearchUtil.BY_PROFILE_GUID + entry.getKey() : ElasticSearchUtil.BY_PROFILE_LEGACY_ALTPID + entry.getKey());
            } else {
                newCondition.append(ParticipantUtil.isGuid(entry.getKey()) ? ElasticSearchUtil.BY_GUIDS + entry.getKey() : ElasticSearchUtil.BY_LEGACY_ALTPIDS + entry.getKey());
            }
            i++;
        }
        if (i == 0) {
            newCondition.append(ElasticSearchUtil.BY_PROFILE_GUID + ElasticSearchUtil.EMPTY);
        }
        newCondition.append(ElasticSearchUtil.CLOSING_PARENTHESIS);
        return newCondition.toString();
    }

    public void addParticipantDataIdsForFilters(Filter filter, String fieldName, List<ParticipantDataDto> allParticipantData,
                                                Map<String, Integer> allIdsForParticipantDataFiltering) {
        Map<String, String> participantIdsForQuery = new HashMap();
        Map<String, String> participantsNotToAdd = new HashMap();
        for (ParticipantDataDto participantData : allParticipantData) {
            String data = participantData.getData().orElse(null);
            String fieldTypeId = participantData.getFieldTypeId().orElse(null);
            if (data == null || fieldTypeId == null) {
                continue;
            }
            String ddpParticipantId = participantData.getDdpParticipantId().orElse(null);
            Map<String, String> dataMap = GSON.fromJson(data, Map.class);
            boolean questionWithOptions = (OPTIONS.equals(filter.getType()) || RADIO.equals(filter.getType())) && filter.getSelectedOptions() != null;
            boolean notEmptyCheck = filter.isNotEmpty() && dataMap.get(fieldName) != null && !dataMap.get(fieldName).isEmpty();
            boolean emptyCheck = filter.isEmpty() && (dataMap.get(fieldName) == null || dataMap.get(fieldName).isEmpty());
            if (notEmptyCheck || emptyCheck && !participantsNotToAdd.containsKey(ddpParticipantId)) {
                participantIdsForQuery.put(ddpParticipantId, fieldName);
                continue;
            }
            //For the participants, which are saved in several rows (for example AT participants)
            boolean shouldNotHaveBeenAdded = filter.isEmpty() && !(dataMap.get(fieldName) == null || dataMap.get(fieldName).isEmpty())
                    && !fieldTypeId.contains(PARTICIPANTS);
            if (shouldNotHaveBeenAdded) {
                participantIdsForQuery.remove(ddpParticipantId);
                participantsNotToAdd.put(ddpParticipantId, fieldName);
                continue;
            }

            if (questionWithOptions) {
                for (String option : filter.getSelectedOptions()) {
                    if (dataMap.get(fieldName) != null && dataMap.get(fieldName)
                            .equals(option)) {
                        participantIdsForQuery.put(ddpParticipantId, fieldName);
                        break;
                    }
                }
            } else if (filter.getFilter1() != null && filter.getFilter1().getValue() != null) {
                boolean singleValueMatches;
                if (filter.isExactMatch()) {
                    singleValueMatches = dataMap.get(fieldName) != null && dataMap.get(fieldName)
                            .equals(filter.getFilter1().getValue());
                } else {
                    singleValueMatches = dataMap.get(fieldName) != null && dataMap.get(fieldName).toLowerCase()
                            .contains(filter.getFilter1().getValue().toString().toLowerCase());
                }
                if (singleValueMatches) {
                    participantIdsForQuery.put(ddpParticipantId, fieldName);
                } else if (filter.getFilter2() != null) {
                    addConditionForRange(filter, fieldName, dataMap, participantIdsForQuery, ddpParticipantId);
                }
            }
        }
        participantIdsForQuery.forEach((key, value) -> allIdsForParticipantDataFiltering
                .put(key, allIdsForParticipantDataFiltering.getOrDefault(key, 0) + 1));
    }

    private StringBuilder getNewCondition(Map<String, String> participantIdsForQuery) {
        StringBuilder newCondition = new StringBuilder(ElasticSearchUtil.AND);
        int i = 0;
        for (String id : participantIdsForQuery.keySet()) {
            if (i == 0) {
                newCondition.append(ParticipantUtil.isGuid(id) ? ElasticSearchUtil.BY_PROFILE_GUID + id : ElasticSearchUtil.BY_PROFILE_LEGACY_ALTPID + id);
            } else {
                newCondition.append(ParticipantUtil.isGuid(id) ? ElasticSearchUtil.BY_GUIDS + id : ElasticSearchUtil.BY_LEGACY_ALTPIDS + id);
            }
            i++;
        }
        return newCondition;
    }

    private void addConditionForRange(Filter filter, String fieldName, Map<String, String> dataMap, Map<String, String> participantIdsForQuery, String ddpParticipantId) {
        Object rangeValue1 = filter.getFilter1().getValue();
        Object rangeValue2 = filter.getFilter2().getValue();
        if (rangeValue1 == null || rangeValue2 == null) {
            return;
        }

        boolean inNumberRange = isInNumberRange(fieldName, dataMap, rangeValue1, rangeValue2);

        boolean inDateRange = isInDateRange(fieldName, dataMap, rangeValue1, rangeValue2);

        if (inNumberRange || inDateRange) {
            participantIdsForQuery.put(ddpParticipantId, fieldName);
        }
    }

    private boolean isInDateRange(String fieldName, Map<String, String> dataMap, Object rangeValue1, Object rangeValue2) {
        return (StringUtils.isNotBlank(dataMap.get(fieldName)))
                && rangeValue1 instanceof String && rangeValue2 instanceof String
                && DATE_PATTERN.matcher((String) rangeValue1).matches() && DATE_PATTERN.matcher((String) rangeValue2).matches()
                && LocalDate.parse(dataMap.get(fieldName)).compareTo(LocalDate.parse((String) rangeValue1)) >= 0
                && ((LocalDate.parse(dataMap.get(fieldName)).compareTo(LocalDate.parse((String) rangeValue1)) >= 0
                && LocalDate.parse(dataMap.get(fieldName)).compareTo(LocalDate.parse((String) rangeValue2)) < 0)
                || (LocalDate.parse(dataMap.get(fieldName)).compareTo(LocalDate.parse((String) rangeValue2)) >= 0
                && LocalDate.parse(dataMap.get(fieldName)).compareTo(LocalDate.parse((String) rangeValue1)) < 0));
    }

    private boolean isInNumberRange(String fieldName, Map<String, String> dataMap, Object rangeValue1, Object rangeValue2) {
        boolean dataIsNumber = dataMap.get(fieldName) != null && NumberUtils.isNumber(dataMap.get(fieldName));
        boolean moreThanFirstNumber = dataIsNumber && rangeValue1 instanceof Double && Double.compare(Double.parseDouble(dataMap.get(fieldName)), (Double) rangeValue1) >= 0;
        boolean moreThanSecondNumber = dataIsNumber && rangeValue2 instanceof Double && Double.compare(Double.parseDouble(dataMap.get(fieldName)), (Double) rangeValue2) >= 0;
        //range will be starting from the lower number up until the higher number
        return (moreThanFirstNumber && !moreThanSecondNumber) || (moreThanSecondNumber && !moreThanFirstNumber);
    }

}
