package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.export.tabular.FilterExportConfig;

/**
 * value provider for COMPOSITE-type questions.  This supports providing a separate string value for each
 * child question, and delegating each child question to a different appropriate value provider.
 */
public class CompositeValueProvider extends TextValueProvider {
    public List<String> formatRawValues(List<?> rawValues, FilterExportConfig filterConfig, Map<String, Object> formMap) {
        if (CollectionUtils.isNotEmpty(filterConfig.getChildConfigs())) {
            List<String> answerVals = new ArrayList<>();
            if (isSingleListResponse(rawValues)) {
                rawValues = (List) rawValues.get(0);
            }
            for (int questionIndex = 0; questionIndex < filterConfig.getChildConfigs().size(); questionIndex++) {
                // for each child question, delegate to the appropriate value provider
                FilterExportConfig childQuestionConfig = filterConfig.getChildConfigs().get(questionIndex);

                TextValueProvider valueProvider =
                        ValueProviderFactory.getValueProvider(childQuestionConfig.getColumn().getName(),
                                childQuestionConfig.getQuestionType());
                Object rawValue = rawValues.size() > questionIndex ? rawValues.get(questionIndex) : StringUtils.EMPTY;


                List<String> formattedValues = valueProvider.formatRawValues(Collections.singletonList(rawValue),
                        childQuestionConfig, formMap);
                answerVals.add(formattedValues.stream().collect(Collectors.joining(", ")));
            }
            return answerVals;

        }
        return super.formatRawValues(rawValues, filterConfig, formMap);
    }

    /** sometimes, e.g. Singular's ABOUT_YOU_HEALTHY.MAILING_ADDRESS, the composite values are nested inside another array.
     *  this checks for that circumstance */
    private boolean isSingleListResponse(List<?> rawValues) {
        return rawValues != null && rawValues.size() == 1 && rawValues.get(0) instanceof List;
    }
}
