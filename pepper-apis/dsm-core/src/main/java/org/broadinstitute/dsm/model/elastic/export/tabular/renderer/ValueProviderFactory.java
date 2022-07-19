package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.QuestionType;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class ValueProviderFactory {
    private static final String AMBULATION = "AMBULATION";

    private final TextValueProvider defaultValueProvider = new TextValueProvider();
    private final TextValueProvider booleanValueProvider = new BooleanValueProvider();
    private final TextValueProvider pickListValueProvider = new PickListValueProvider();
    private final TextValueProvider collatedValueProvider = new CollatedQuestionValueProvider();
    private final Map<QuestionType, TextValueProvider> valueProviders = Map.of(
            QuestionType.CHECKBOX, booleanValueProvider,
            QuestionType.NUMBER, defaultValueProvider,
            QuestionType.BOOLEAN, booleanValueProvider,
            QuestionType.COMPOSITE, new CompositeValueProvider(),
            QuestionType.AGREEMENT, booleanValueProvider,
            QuestionType.MATRIX, defaultValueProvider,
            QuestionType.DATE, new DateValueProvider(),
            QuestionType.OPTIONS, pickListValueProvider,
            QuestionType.JSON_ARRAY, defaultValueProvider,
            QuestionType.RADIO, pickListValueProvider
    );
    private final Map<String, TextValueProvider> specialValueProviders = Map.of(
            AMBULATION, new AmbulationValueProvider(),
            ESObjectConstants.ACTIVITY_STATUS, new ActivityStatusValueProvider(),
            ESObjectConstants.COHORT_TAG_NAME, new CohortTagNameProvider()
    );

    public static final List<String> COLLATED_SUFFIXES = Arrays.asList("REGISTRATION_STATE_PROVINCE");

    public TextValueProvider getValueProvider(String participantColumnName, String questionType) {
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

    private boolean isSpecialColumn(String participantColumn) {
        return specialValueProviders.containsKey(participantColumn);
    }
}
