package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

public class TextValueProvider {
    public Collection<?> getRawValues(FilterExportConfig filterConfig, Map<String, Object> moduleMap) {
        Object value = StringUtils.EMPTY;
        if (moduleMap == null) {
            value = StringUtils.EMPTY;
        } else if (ElasticSearchUtil.QUESTIONS_ANSWER.equals(filterConfig.getColumn().getObject()) ) {
            value = getRawAnswerValues(moduleMap, filterConfig);
        } else {
            value = getValueFromMap(moduleMap, filterConfig);
        }
        if (!(value instanceof Collection)) {
            return Collections.singletonList(value);
        }
        return (Collection<?>) value;
    }

    public Collection<String> getFormattedValues(FilterExportConfig filterConfig, Map<String, Object> formMap) {
        return formatRawValues(getRawValues(filterConfig, formMap), filterConfig, formMap);
    }

    public Collection<String> formatRawValues(Collection<?> rawValues, FilterExportConfig filterConfig, Map<String, Object> formMap) {
        return rawValues.stream().map(val -> val != null ? val.toString() : StringUtils.EMPTY).collect(Collectors.toList());
    }


    protected Object getValueFromMap(Map<String, Object> moduleMap, FilterExportConfig filterConfig) {
        Map<String, Object> targetMap = moduleMap;
        String objectName = filterConfig.getColumn().getObject();
        String fieldName = filterConfig.getColumn().getName();
        if (objectName != null) {
            Object formObject = moduleMap.get(objectName);
            if (formObject instanceof List) {
                // this is a list, like "testResult" off of kitRequestShipping
                // transform an array of maps into a map of arrays
                Map<String, Object> answerMap = new HashMap();
                final String finalFieldName = fieldName;
                List<Object> answersArray = ((List<?>) formObject).stream().map(arrValue -> {
                    if (arrValue instanceof Map) {
                        return ((Map<String, Object>) arrValue).get(finalFieldName);
                    }
                    return StringUtils.EMPTY;
                }).collect(Collectors.toList());
                answerMap.put(fieldName, answersArray);
                targetMap = answerMap;

            } else if (formObject instanceof Map) {
                targetMap = (Map<String, Object>) formObject;

            } else {
                // try dynamic fields
                Map<String, Object> dynamicFieldMap = (Map<String, Object>) moduleMap.get(ESObjectConstants.DYNAMIC_FIELDS);
                if (dynamicFieldMap != null) {
                    String camelCasedFieldName = Arrays.stream(fieldName.split("_"))
                            .map(word -> StringUtils.capitalize(StringUtils.toRootLowerCase(word))).collect(Collectors.joining());
                    camelCasedFieldName = StringUtils.uncapitalize(camelCasedFieldName);
                    if (dynamicFieldMap.get(camelCasedFieldName) != null) {
                        targetMap = dynamicFieldMap;
                        fieldName = camelCasedFieldName;
                    }
                }
            }
        }
        return targetMap.getOrDefault(fieldName, StringUtils.EMPTY);
    }

    protected Object getRawAnswerValues(Map<String, Object> moduleMap, FilterExportConfig filterConfig) {
        if (moduleMap == null) {
            return StringUtils.EMPTY;
        }
        List<Map<String, Object>> allAnswers =
                (List<Map<String, Object>>) moduleMap.get(ElasticSearchUtil.QUESTIONS_ANSWER);
        List<Map<String, Object>> targetAnswers = allAnswers.stream()
                .filter(ans -> filterConfig.getColumn().getName().equals(ans.get(ESObjectConstants.STABLE_ID)))
                .collect(Collectors.toList());
        if (targetAnswers.isEmpty()) {
            return StringUtils.EMPTY;
        }
        Map<String, Object> firstAnswer = targetAnswers.get(0);
        List<Object> rawAnswers = targetAnswers.stream().map(ans -> {
                return ans.getOrDefault(ESObjectConstants.ANSWER, firstAnswer.get(filterConfig.getColumn().getName()));
        }).collect(Collectors.toList());

        Collection<?> answer = mapToCollection(rawAnswers);
        Object optionDetails = firstAnswer.get(ESObjectConstants.OPTIONDETAILS);
        if (optionDetails != null && !((List<?>) optionDetails).isEmpty()) {
            removeOptionsFromAnswer(answer, ((List<Map<String, String>>) optionDetails));
            return Stream.of(answer, getOptionDetails((List<?>) optionDetails)).flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }

        return answer;
    }

    protected void removeOptionsFromAnswer(Collection<?> answer, List<Map<String, String>> optionDetails) {
        List<String> details = optionDetails.stream().map(options -> options.get(ESObjectConstants.OPTION)).collect(Collectors.toList());
        answer.removeAll(details);
    }

    protected List<String> getOptionDetails(List<?> optionDetails) {
        return optionDetails.stream().map(optionDetail ->
                        new StringBuilder(((Map) optionDetail).get(ESObjectConstants.OPTION).toString())
                                .append(':')
                                .append(((Map) optionDetail).get(ESObjectConstants.DETAIL)).toString())
                .collect(Collectors.toList());
    }

    protected Collection<?> mapToCollection(Object o) {
        if (o == null) {
            return Collections.singletonList(StringUtils.EMPTY);
        }
        if (o instanceof Collection) {
            Collection<?> objList = (Collection<?>) o;
            if (objList.isEmpty()) {
                return Collections.singletonList(StringUtils.EMPTY);
            }
            List<Object> allValues = new ArrayList<>();
            // flatten any nested lists
            for (Object item : objList) {
                if (item instanceof Collection) {
                    allValues.addAll(flatten((Collection) item));
                } else {
                    allValues.add(item);
                }
            }
            // replace any nulls with empty string
            return allValues.stream().map(val -> val == null ? StringUtils.EMPTY : val).collect(Collectors.toList());
        } else {
            return Collections.singletonList(o);
        }
    }

    /** flatten an arbitrarily nested collection */
    private Collection<?> flatten(Collection<?> collection) {
        List<Object> allValues = new ArrayList<>();

        for (Object item : collection) {
            if (item instanceof Collection) {
                allValues.addAll(flatten((Collection) item));
            } else {
                allValues.add(item);
            }
        }
        return allValues;
    }
}
