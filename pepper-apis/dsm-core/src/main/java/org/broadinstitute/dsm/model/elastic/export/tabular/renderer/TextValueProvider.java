package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

/** Base class for rendering participant data as text */
public class TextValueProvider {

    /**
     * gets the values suitable for export in string form
     * @param filterConfig the config for the question/item
     * @param formMap a map of the data for this module response.  That should be a value returned from getModuleCompletions
     *                in TabularParticipantParser
     * @return a list of lists of response values.  The outer list has one entry for each response given (e.g. for a medication list
     *     question, one entry will correspond to each medication). The outer list will always have only one entry unless
     *     the question is an allowMultiple.  The inner lists corresponds to the values for each response. It will have one entry
     *     for most questions, but it will have multiple entries for either selectMode=Multiple questions or composite questions
     */
    public List<List<String>> getFormattedValues(FilterExportConfig filterConfig, Map<String, Object> formMap) {
        return collectFormattedResponses(getRawValues(filterConfig, formMap), filterConfig, formMap);
    }


    /** looks for additional details associated with an option response.  For example, if the participant was asked to
     * select a symptom, and then for each symptom selected, enter the age at which the symptom began.
     */
    public String getOptionDetails(FilterExportConfig filterConfig, Map<String, Object> moduleMap, String optionStableId, int responseNum) {
        Map<String, Object> answerObject = getRelevantAnswer(moduleMap, filterConfig);
        if (answerObject != null) {
            Object optionDetails = answerObject.get(ESObjectConstants.OPTIONDETAILS);

            if (optionDetails instanceof List) {

                return extractOptionDetails(optionStableId, (List<?>) optionDetails);
            }
        }
        return StringUtils.EMPTY;
    }

    /**
     * Option details are stored in ES as an array of maps
     *  e.g. [{option: "TELANGIECTASIA_EYES", details: "12"}, {option: "TELANGIECTASIA_SKIN", details: "34"}]
     *  if 'null' is passed as the optionStableId, this will return all option details
     */
    protected String extractOptionDetails(String optionStableId, List<?> optionDetailsObject) {
        return optionDetailsObject.stream().filter(detail ->
                        optionStableId == null || StringUtils.equals((String) ((Map) detail).get(ESObjectConstants.OPTION), optionStableId))
                .map(detail -> (String) ((Map<String, Object>) detail).get(ESObjectConstants.DETAILS))
                .collect(Collectors.joining("; "));
    }

    protected List<?> getRawValues(FilterExportConfig filterConfig, Map<String, Object> moduleMap) {
        List<Object> value = null;
        if (moduleMap == null) {
            value = Collections.singletonList(StringUtils.EMPTY);
        } else if (filterConfig.isQuestionAnswer()) {
            value = getRawAnswerValues(moduleMap, filterConfig);
        } else {
            value = Collections.singletonList(getValueFromMap(moduleMap, filterConfig));
        }

        return value;
    }

    protected List<List<String>> collectFormattedResponses(List<?> rawValues,
                                                           FilterExportConfig filterConfig,
                                                           Map<String, Object> formMap) {
        if (filterConfig.isAllowMultiple() && CollectionUtils.isNotEmpty(rawValues) && rawValues.get(0) instanceof List) {
            return rawValues.stream().map(valueSet -> formatRawValues((List) valueSet, filterConfig, formMap))
                    .collect(Collectors.toList());
        }
        return Collections.singletonList(formatRawValues(rawValues, filterConfig, formMap));
    }



    protected List<String> formatRawValues(List<?> rawValues, FilterExportConfig filterConfig, Map<String, Object> formMap) {
        return rawValues.stream().map(val -> {
            if (val == null) {
                return StringUtils.EMPTY;
            } else if (val instanceof List) {
                return (String) ((List) val).stream().map(item -> item != null ? item.toString() : StringUtils.EMPTY)
                        .collect(Collectors.joining(", "));
            }
            return val.toString();
        }).collect(Collectors.toList());
    }

    /** handles reading values from things other than questions. including basic properties and array properties
     * it falls back to reading the dynamic fields if an object is specified that doesn't exist on the
     * is found
     */
    protected Object getValueFromMap(Map<String, Object> moduleMap, FilterExportConfig filterConfig) {
        Map<String, Object> targetMap = null;
        String objectName = filterConfig.getColumn().getObject();
        String fieldName = filterConfig.getColumn().getName();
        if (objectName != null) {
            Object formObject = moduleMap.get(objectName);
            if (formObject instanceof List) {
                return extractListValues((List) formObject, fieldName);
            } else if (formObject instanceof Map) {
                targetMap = (Map<String, Object>) formObject;
            }
        }
        if (mightBeDynamicField(targetMap, filterConfig)) {
            // try dynamic fields
            Map<String, Object> dynamicFieldMap = (Map<String, Object>) moduleMap.get(ESObjectConstants.DYNAMIC_FIELDS);
            if (dynamicFieldMap != null) {
                // the field name may be given in either snake case or camelcase, but in the dynamic field map, it is camel case
                String camelCasedFieldName = fieldName;
                if (Character.isUpperCase(camelCasedFieldName.charAt(0))) {
                    camelCasedFieldName = Arrays.stream(fieldName.split("_"))
                            .map(word -> StringUtils.capitalize(StringUtils.toRootLowerCase(word))).collect(Collectors.joining());
                    camelCasedFieldName = StringUtils.uncapitalize(camelCasedFieldName);
                }

                if (dynamicFieldMap.get(camelCasedFieldName) != null) {
                    targetMap = dynamicFieldMap;
                    fieldName = camelCasedFieldName;
                }
            }
        }
        // if this isn't any of the special cases above, just use the passed-in map
        if (targetMap == null) {
            targetMap = moduleMap;
        }
        return targetMap.getOrDefault(fieldName, StringUtils.EMPTY);
    }

    private Object extractListValues(List formObject, String fieldName) {
        // this is a list, like "testResult" off of kitRequestShipping
        // transform an array of maps into a map of arrays
        Map<String, Object> answerMap = new HashMap();
        List<Object> answersArray = ((List<?>) formObject).stream().map(arrValue -> {
            if (arrValue instanceof Map) {
                return ((Map<String, Object>) arrValue).get(fieldName);
            }
            return StringUtils.EMPTY;
        }).collect(Collectors.toList());
        answerMap.put(fieldName, answersArray);
        return answerMap.getOrDefault(fieldName, StringUtils.EMPTY);
    }

    private boolean mightBeDynamicField(Map<String, Object> targetMap, FilterExportConfig filterConfig) {
        String objectName = filterConfig.getColumn().getObject();
        return (targetMap == null && objectName != null) || ESObjectConstants.ADDITIONAL_VALUE.equals(filterConfig.getType());
    }

    protected List<Object> getRawAnswerValues(Map<String, Object> moduleMap, FilterExportConfig filterConfig) {
        Map<String, Object> answerObject = getRelevantAnswer(moduleMap, filterConfig);
        Object rawValue = extractValuesFromAnswer(answerObject, filterConfig);
        if (answerObject != null) {
            Object optionDetails = answerObject.get(ESObjectConstants.OPTIONDETAILS);
            if (optionDetails != null && !((List<?>) optionDetails).isEmpty()) {
                List<String> optionDetailIds = ((List<Map<String, Object>>) optionDetails).stream()
                        .map(detail -> (String) detail.get(ESObjectConstants.OPTION)).collect(Collectors.toList());
                filterConfig.getOptionIdsWithDetails().addAll(optionDetailIds);
            }
        }

        if (!(rawValue instanceof List)) {
            return Collections.singletonList(rawValue);
        }
        return (List) rawValue;
    }

    protected Map<String, Object> getRelevantAnswer(Map<String, Object> moduleMap, FilterExportConfig filterConfig) {
        if (moduleMap != null) {
            List<Map<String, Object>> allAnswers =
                    (List<Map<String, Object>>) moduleMap.get(ElasticSearchUtil.QUESTIONS_ANSWER);
            if (allAnswers != null) {
                return allAnswers.stream()
                        .filter(ans -> filterConfig.getColumn().getName().equals(ans.get(ESObjectConstants.STABLE_ID)))
                        .findAny().orElse(null);
            }
        }
        return null;
    }

    protected Object extractValuesFromAnswer(Map<String, Object> targetAnswer, FilterExportConfig filterConfig) {
        if (targetAnswer == null) {
            return null;
        }
        return targetAnswer.getOrDefault(ESObjectConstants.ANSWER, targetAnswer.get(filterConfig.getColumn().getName()));
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
            return replaceNullsWithEmptyString(allValues);
        } else {
            return Collections.singletonList(o);
        }
    }

    private List<Object> replaceNullsWithEmptyString(Collection<Object> values) {
        return values.stream().map(val -> val == null ? StringUtils.EMPTY : val).collect(Collectors.toList());
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
