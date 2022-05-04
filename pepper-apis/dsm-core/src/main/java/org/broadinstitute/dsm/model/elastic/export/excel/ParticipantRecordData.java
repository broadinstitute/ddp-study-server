package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class ParticipantRecordData {
    private final Map<Alias, List<ParticipantColumn>> columnAliasEsPathMap;
    private final List<String> headerNames = new ArrayList<>();
    private List<Integer> columnSizes = new ArrayList<>();

    public ParticipantRecordData(Map<Alias, List<ParticipantColumn>> columnAliasEsPathMap) {
        this.columnAliasEsPathMap = columnAliasEsPathMap;
    }

    public List<List<String>> processData(ParticipantWrapperResult participantData, boolean isCountPhase) {
        List<ParticipantRecord> participantRecords = new ArrayList<>();
        for (ParticipantWrapperDto participant : participantData.getParticipants()) {
            ParticipantRecord participantRecord = new ParticipantRecord();
            Map<String, Object> esDataAsMap = participant.getEsDataAsMap();
            for (Map.Entry<Alias, List<ParticipantColumn>> aliasListEntry : columnAliasEsPathMap.entrySet()) {
                Alias key = aliasListEntry.getKey();
                for (ParticipantColumn column : aliasListEntry.getValue()) {
                    String esPath = getEsPath(key, column);
                    Collection<?> nestedValue = getNestedValue(esPath, esDataAsMap);
                    if (aliasListEntry.getKey().isJson()) {
                        nestedValue = getJsonValue(nestedValue, column, key);
                    }
                    if (aliasListEntry.getKey() == Alias.ACTIVITIES) {
                        nestedValue = getQuestionAnswerValue(nestedValue, column);
                    }
                    ColumnValue columnValue = new ColumnValue(key, nestedValue);
                    participantRecord.add(columnValue);
                }
            }
            if (isCountPhase) {
                initOrUpdateSizes(participantRecord);
            } else {
                participantRecords.add(participantRecord);
            }
        }
        return getRowData(participantRecords);

    }

    private Collection<?> getJsonValue(Collection<?> nestedValue, ParticipantColumn column,
                                       Alias alias) {
        if (nestedValue.isEmpty()) {
            return Collections.singletonList(StringUtils.EMPTY);
        }
        String jsonString;
        if (alias.isCollection()) {
            jsonString = nestedValue.stream().filter(val ->
                            column.getObject().equals(((Map<String, Object>) val).get(ESObjectConstants.FIELD_TYPE_ID)))
                    .findFirst().map(val -> ((Map<String, Object>) val).get(ESObjectConstants.DATA).toString()).orElse(StringUtils.EMPTY);
        } else {
            jsonString = nestedValue.stream().findFirst().get().toString();
        }
        JsonNode jsonNode;
        try {
            jsonNode = ObjectMapperSingleton.instance().readTree(jsonString);
            if (jsonNode.has(column.getName())) {
                return Collections.singletonList(jsonNode.get(column.getName()).asText(StringUtils.EMPTY));
            } else {
                return Collections.singletonList(StringUtils.EMPTY);
            }
        } catch (JsonProcessingException e) {
            return Collections.singletonList(StringUtils.EMPTY);
        }
    }

    public List<String> getHeader() {
        int i = 0;
        for (Map.Entry<Alias, List<ParticipantColumn>> aliasListEntry : columnAliasEsPathMap.entrySet()) {
            headerNames.addAll(getColumnNamesFor(aliasListEntry, columnSizes.subList(i, i + aliasListEntry.getValue().size())));
            i += aliasListEntry.getValue().size();
        }
        return headerNames;
    }

    private List<List<String>> getRowData(List<ParticipantRecord> participantRecords) {
        List<List<String>> rowValues = new ArrayList<>();
        participantRecords.forEach(record -> rowValues.add(record.transposeAndFlatten(columnSizes)));
        return rowValues;
    }

    private void initOrUpdateSizes(ParticipantRecord participantRecord) {
        List<ColumnValue> combinedValues = participantRecord.getValues();
        if (columnSizes.isEmpty()) {
            columnSizes = combinedValues.stream().map(ColumnValue::getColumnsSize).collect(Collectors.toList());
        } else {
            for (int i = 0; i < columnSizes.size(); i++) {
                columnSizes.set(i, Math.max(columnSizes.get(i), combinedValues.get(i).getColumnsSize()));
            }
        }
    }

    private Collection<String> getColumnNamesFor(Map.Entry<Alias, List<ParticipantColumn>> aliasColumns, List<Integer> sizes) {
        List<ParticipantColumn> columnsList = aliasColumns.getValue();
        List<String> columns = new ArrayList<>();
        int sameAliasSize = sizes.get(0);
        if (aliasColumns.getKey() != Alias.ACTIVITIES) {
            IntStream.rangeClosed(1, sizes.get(0)).forEach(value -> columns.addAll(columnsList.stream()
                    .map(column -> String.format("%s %s", column.getDisplay(),
                            aliasColumns.getKey().isCollection() && sameAliasSize > 1 ? value : StringUtils.EMPTY))
                    .collect(Collectors.toList())));
        } else {
            return IntStream.range(0, columnsList.size()).mapToObj(i -> Pair.of(columnsList.get(i), sizes.get(i)))
                    .map(entry -> IntStream.rangeClosed(1, entry.getValue()).mapToObj(
                            currentIndex -> String.format("%s %s", entry.getKey().getDisplay(),
                                            aliasColumns.getKey().isCollection() && entry.getValue() > 1 ? currentIndex : StringUtils.EMPTY))
                            .collect(Collectors.toList())).flatMap(Collection::stream).collect(Collectors.toList());
        }

        return columns;
    }

    private Collection<?> getQuestionAnswerValue(Object nestedValue, ParticipantColumn column) {
        List<LinkedHashMap<String, Object>> activities = (List<LinkedHashMap<String, Object>>) nestedValue;
        Collection<?> objects =
                activities.stream().filter(activity -> activity.get(ElasticSearchUtil.ACTIVITY_CODE).equals(column.getTableAlias()))
                        .map(foundActivity -> {
                            if (Objects.isNull(column.getObject())) {
                                Object o = foundActivity.get(column.getName());
                                return mapToCollection(o);
                            } else {
                                List<LinkedHashMap<String, Object>> questionAnswers =
                                        (List<LinkedHashMap<String, Object>>) foundActivity.get(ElasticSearchUtil.QUESTIONS_ANSWER);
                                return questionAnswers.stream().filter(qa -> qa.get(ESObjectConstants.STABLE_ID).equals(column.getName()))
                                        .map(fq -> getAnswerValue(fq, column.getName()))
                                        .map(this::mapToCollection).flatMap(Collection::stream)
                                        .collect(Collectors.toList());
                            }
                        }).flatMap(Collection::stream).collect(Collectors.toList());
        if (objects.isEmpty()) {
            return Collections.singletonList(StringUtils.EMPTY);
        }
        return objects;
    }

    private Object getAnswerValue(LinkedHashMap<String, Object> fq, String columnName) {
        Collection<?> answer = mapToCollection(fq.get(ESObjectConstants.ANSWER));
        if (answer.isEmpty()) {
            answer = mapToCollection(fq.get(columnName));
        }
        Object optionDetails = fq.get(ESObjectConstants.OPTIONDETAILS);
        if (optionDetails != null && !((List<?>) optionDetails).isEmpty()) {
            removeOptionsFromAnswer(answer, ((List<Map<String, String>>) optionDetails));
            return Stream.of(answer, getOptionDetails((List<?>) optionDetails)).flatMap(Collection::stream).collect(Collectors.toList());
        }

        return answer;
    }

    private void removeOptionsFromAnswer(Collection<?> answer, List<Map<String, String>> optionDetails) {
        List<String> details = optionDetails.stream().map(options -> options.get(ESObjectConstants.OPTION)).collect(Collectors.toList());
        answer.removeAll(details);
    }

    private List<String> getOptionDetails(List<?> optionDetails) {
        return optionDetails.stream()
                .map(optionDetail -> new StringBuilder(((Map) optionDetail).get(ESObjectConstants.OPTION).toString()).append(':')
                        .append(((Map) optionDetail).get(ESObjectConstants.DETAIL)).toString()).collect(Collectors.toList());
    }

    private Collection<?> mapToCollection(Object o) {
        if (o == null) {
            return Collections.singletonList(StringUtils.EMPTY);
        }
        if (o instanceof Collection) {
            if (((Collection<?>) o).isEmpty()) {
                return Collections.singletonList(StringUtils.EMPTY);
            }
            return (Collection<?>) o;
        } else {
            return Collections.singletonList(o);
        }
    }

    private String getEsPath(Alias alias, ParticipantColumn column) {
        if (alias == Alias.ACTIVITIES) {
            return alias.getValue();
        }
        if (alias.isJson()) {
            return alias.getValue();
        }
        return alias.getValue().isEmpty() ? column.getName() : alias.getValue() + DBConstants.ALIAS_DELIMITER + column.getName();
    }

    private Collection<?> getNestedValue(String fieldName, Map<String, Object> esDataAsMap) {
        int dotIndex = fieldName.indexOf('.');
        if (dotIndex != -1) {
            Object o = esDataAsMap.get(fieldName.substring(0, dotIndex));
            if (o == null) {
                return Collections.singletonList(StringUtils.EMPTY);
            }
            if (o instanceof Collection) {
                List<Object> collect = ((Collection<?>) o).stream()
                        .map(singleDataMap -> getNestedValue(fieldName.substring(dotIndex + 1), (Map<String, Object>) singleDataMap))
                        .flatMap(Collection::stream).collect(Collectors.toList());
                if (collect.isEmpty()) {
                    return Collections.singletonList(StringUtils.EMPTY);
                }
                return collect;
            } else {
                return getNestedValue(fieldName.substring(dotIndex + 1), (Map<String, Object>) o);
            }
        }
        Object value = esDataAsMap.getOrDefault(fieldName, StringUtils.EMPTY);
        if (!(value instanceof Collection)) {
            return Collections.singletonList(value);
        }
        return (Collection<?>) value;
    }
}
