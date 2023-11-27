package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.QuestionType;
import org.broadinstitute.dsm.statics.ESObjectConstants;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ValueProviderFactory {
    private static final String AMBULATION = "AMBULATION";

    private static final TextValueProvider defaultValueProvider = new TextValueProvider();
    private static final TextValueProvider booleanValueProvider = new BooleanValueProvider();
    private static final TextValueProvider pickListValueProvider = new PickListValueProvider();
    private static final TextValueProvider collatedValueProvider = new CollatedQuestionValueProvider();
    private static final Map<QuestionType, TextValueProvider> valueProviders = Map.ofEntries(
            Map.entry(QuestionType.CHECKBOX, booleanValueProvider),
            Map.entry(QuestionType.NUMBER, defaultValueProvider),
            Map.entry(QuestionType.BOOLEAN, booleanValueProvider),
            Map.entry(QuestionType.COMPOSITE, new CompositeValueProvider()),
            Map.entry(QuestionType.AGREEMENT, booleanValueProvider),
            Map.entry(QuestionType.MATRIX, defaultValueProvider),
            Map.entry(QuestionType.DATE, new DateValueProvider()),
            Map.entry(QuestionType.OPTIONS, pickListValueProvider),
            Map.entry(QuestionType.PICKLIST, pickListValueProvider),
            Map.entry(QuestionType.JSON_ARRAY, defaultValueProvider),
            Map.entry(QuestionType.RADIO, pickListValueProvider)
    );
    private static final Map<String, TextValueProvider> specialValueProviders = Map.of(
            ESObjectConstants.ACTIVITY_STATUS, new ActivityStatusValueProvider(),
            ESObjectConstants.COHORT_TAG_NAME, new CohortTagNameProvider()
    );

    public static final List<String> COLLATED_SUFFIXES = Arrays.asList("REGISTRATION_STATE_PROVINCE");

    public static TextValueProvider getValueProvider(String participantColumnName, String questionType) {
        if (isSpecialColumn(participantColumnName)) {
            return specialValueProviders.get(participantColumnName);
        }
        if (COLLATED_SUFFIXES.stream().anyMatch(suffix -> StringUtils.endsWith(participantColumnName, suffix))) {
            return collatedValueProvider;
        }
        if (questionType == null) {
            return defaultValueProvider;
        }
        return valueProviders.getOrDefault(QuestionType.getByValue(questionType), defaultValueProvider);
    }

    private static boolean isSpecialColumn(String participantColumn) {
        return specialValueProviders.containsKey(participantColumn);
    }
}
