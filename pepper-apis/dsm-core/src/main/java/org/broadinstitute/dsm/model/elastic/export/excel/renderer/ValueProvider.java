package org.broadinstitute.dsm.model.elastic.export.excel.renderer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public interface ValueProvider {
    Collection<String> getValue(String esPath, Map<String, Object> esDataAsMap, Alias key, Filter column);

    default Collection<?> getNestedValue(String fieldName, Map<String, Object> esDataAsMap,
                                         Alias alias, ParticipantColumn participantColumn) {
        Collection<?> nestedValueWrapper = getNestedValueWrapper(fieldName, esDataAsMap, alias, participantColumn);
        if (alias.isJson()) {
            nestedValueWrapper = getJsonValue(nestedValueWrapper, participantColumn);
        }
        if (alias == Alias.ACTIVITIES) {
            nestedValueWrapper = getQuestionAnswerValue(nestedValueWrapper, participantColumn);
        }
        return nestedValueWrapper;
    }
    private Collection<?> getNestedValueWrapper(String fieldName, Map<String, Object> esDataAsMap,
                                                Alias alias, ParticipantColumn participantColumn) {
        int dotIndex = fieldName.indexOf('.');
        if (dotIndex != -1) {
            Object o = esDataAsMap.get(fieldName.substring(0, dotIndex));
            if (o == null) {
                return Collections.singletonList(StringUtils.EMPTY);
            }
            if (o instanceof Collection) {
                List<Object> collect = ((Collection<?>) o).stream().map(singleDataMap -> getNestedValueWrapper(fieldName.substring(dotIndex + 1),
                        (Map<String, Object>) singleDataMap, alias, participantColumn)).flatMap(Collection::stream).collect(Collectors.toList());
                if (collect.isEmpty()) {
                    return Collections.singletonList(StringUtils.EMPTY);
                }
                return collect;
            } else {
                return getNestedValueWrapper(fieldName.substring(dotIndex + 1), (Map<String, Object>) o, alias, participantColumn);
            }
        }
        Object value = esDataAsMap.getOrDefault(fieldName, StringUtils.EMPTY);
        if (!(value instanceof Collection )) {
            return Collections.singletonList(value);
        }
        return (Collection<?>) value;
    }
    private Collection<?> getJsonValue(Collection<?> nestedValue, ParticipantColumn column) {
        if (nestedValue.isEmpty()) {
            return Collections.singletonList(StringUtils.EMPTY);
        }
        String jsonString = nestedValue.stream().findFirst().get().toString();
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
                                        .map(this::mapToCollection)
                                        .flatMap(Collection::stream)
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
            removeOptionsFromAnswer(answer, ((List<Map<String, String>>)optionDetails));
            return Stream.of(answer, getOptionDetails((List<?>) optionDetails)).flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }

        return answer;
    }

    private void removeOptionsFromAnswer(Collection<?> answer, List<Map<String,String>> optionDetails) {
        List<String> details = optionDetails.stream().map(options -> options.get(ESObjectConstants.OPTION)).collect(Collectors.toList());
        answer.removeAll(details);
    }

    private List<String> getOptionDetails(List<?> optionDetails) {
        return optionDetails.stream().map(optionDetail ->
                        new StringBuilder(((Map)optionDetail).get(ESObjectConstants.OPTION).toString())
                                .append(':')
                                .append(((Map)optionDetail).get(ESObjectConstants.DETAIL)).toString())
                .collect(Collectors.toList());
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

}
