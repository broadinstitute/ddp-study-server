package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public interface ValueProvider {
   default Collection<?> getRawValues(FilterExportConfig qConfig, Map<String, Object> formMap) {
       Collection<?> nestedValueWrapper = getRawValueWrapper(qConfig, formMap);
       return nestedValueWrapper;
   };

    default Collection<String> getFormattedValues(FilterExportConfig qConfig, Map<String, Object> formMap) {
       return formatRawValues(getRawValues(qConfig, formMap), qConfig, formMap);
    };

    default Collection<String> formatRawValues(Collection<?> rawValues, FilterExportConfig qConfig, Map<String, Object> formMap) {
        return rawValues.stream().map(val -> val.toString()).collect(Collectors.toList());
    }

    private Collection<?> getRawValueWrapper(FilterExportConfig qConfig, Map<String, Object> formMap) {
        Object value = StringUtils.EMPTY;
        String fieldName = qConfig.getColumn().getName();
        if (formMap == null) {
            value = StringUtils.EMPTY;
        } else if (fieldName.equals(ESObjectConstants.COHORT_TAG_NAME)) {
            value = formMap.getOrDefault(ESObjectConstants.COHORT_TAG, StringUtils.EMPTY);
        } else if (ElasticSearchUtil.QUESTIONS_ANSWER.equals(qConfig.getColumn().getObject())) {
            if (formMap != null) {
                List<LinkedHashMap<String, Object>> allAnswers = (List<LinkedHashMap<String, Object>>) formMap.get(ElasticSearchUtil.QUESTIONS_ANSWER);
                List<LinkedHashMap<String, Object>> targetAnswers = allAnswers.stream()
                        .filter(ans -> qConfig.getColumn().getName().equals(ans.get("stableId"))).collect(Collectors.toList());
                if (!targetAnswers.isEmpty()) {
                    value = getRawAnswerValue(targetAnswers.get(0), qConfig.getColumn().getName());
                }
            }
        } else {
            Map<String, Object> targetMap = formMap;
            String objectName = qConfig.getColumn().getObject();
            if (objectName != null && formMap.get(objectName) != null) {
                targetMap = (Map<String, Object>) formMap.get(objectName);
            }
            value = targetMap.getOrDefault(fieldName, StringUtils.EMPTY);
        }

        if (!(value instanceof Collection)) {
            return Collections.singletonList(value);
        }
        return (Collection<?>) value;
    }

//    private Collection<?> getJsonValue(Collection<?> nestedValue, ParticipantColumn column) {
//        if (nestedValue.isEmpty()) {
//            return Collections.singletonList(StringUtils.EMPTY);
//        }
//
//        Collection<?> jsonValues = nestedValue.stream().map(value -> {
//            JsonNode jsonNode;
//            try {
//                jsonNode = ObjectMapperSingleton.instance().readTree(value.toString());
//                if (jsonNode.has(column.getName())) {
//                    return jsonNode.get(column.getName()).asText(StringUtils.EMPTY);
//                } else {
//                    return StringUtils.EMPTY;
//                }
//            } catch (JsonProcessingException e) {
//                return StringUtils.EMPTY;
//            }
//        }).collect(Collectors.toList());
//        return jsonValues;
//    }

    private Object getRawAnswerValue(LinkedHashMap<String, Object> fq, String columnName) {
        Object rawAnswer = fq.getOrDefault(ESObjectConstants.ANSWER, fq.get(columnName));

        Collection<?> answer = mapToCollection(rawAnswer);
        Object optionDetails = fq.get(ESObjectConstants.OPTIONDETAILS);
        if (optionDetails != null && !((List<?>) optionDetails).isEmpty()) {
            removeOptionsFromAnswer(answer, ((List<Map<String, String>>) optionDetails));
            return Stream.of(answer, getOptionDetails((List<?>) optionDetails)).flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }

        return answer;
    }

    private void removeOptionsFromAnswer(Collection<?> answer, List<Map<String, String>> optionDetails) {
        List<String> details = optionDetails.stream().map(options -> options.get(ESObjectConstants.OPTION)).collect(Collectors.toList());
        answer.removeAll(details);
    }

    private List<String> getOptionDetails(List<?> optionDetails) {
        return optionDetails.stream().map(optionDetail ->
                        new StringBuilder(((Map) optionDetail).get(ESObjectConstants.OPTION).toString())
                                .append(':')
                                .append(((Map) optionDetail).get(ESObjectConstants.DETAIL)).toString())
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
