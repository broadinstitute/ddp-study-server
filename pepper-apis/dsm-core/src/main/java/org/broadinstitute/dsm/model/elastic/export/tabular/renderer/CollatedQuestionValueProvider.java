package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;

/** rolls up all the state/province questions into a single answer */
public class CollatedQuestionValueProvider extends PickListValueProvider {
    @Override
    public List<?> getRawValues(FilterExportConfig filterConfig, Map<String, Object> formMap) {
        Object value = StringUtils.EMPTY;
        String fieldName = filterConfig.getColumn().getName();
        if (formMap == null) {
            return Collections.singletonList(StringUtils.EMPTY);
        } else {
            List<Map<String, Object>> allAnswers =
                    (List<Map<String, Object>>) formMap.get(ElasticSearchUtil.QUESTIONS_ANSWER);
            if (allAnswers == null) {
                return Collections.singletonList(StringUtils.EMPTY);
            }
            Stream<Map<String, Object>> targetAnswers = allAnswers.stream()
                    .filter(answerObj -> {
                        return StringUtils.endsWith((String) answerObj.get(ESObjectConstants.STABLE_ID), filterConfig.getCollationSuffix());
                    });
            Stream<Collection<?>> answerLists = targetAnswers.map(answerObj -> {
                return mapToCollection(answerObj.getOrDefault(ESObjectConstants.ANSWER,
                        answerObj.get(filterConfig.getColumn().getName())));
            });
            List<String> answerStrings = answerLists.flatMap(Collection::stream)
                    .filter(ansValue -> StringUtils.isNotBlank((String) ansValue))
                    .map(val -> val == null ? StringUtils.EMPTY : (String) val).collect(Collectors.toList());
            return answerStrings;

        }
    }
}
