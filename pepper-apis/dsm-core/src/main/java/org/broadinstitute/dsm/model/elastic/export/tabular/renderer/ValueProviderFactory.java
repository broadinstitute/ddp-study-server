package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Map;

import org.broadinstitute.dsm.model.QuestionType;

public class ValueProviderFactory {
    private static final String AMBULATION = "AMBULATION";
    private static final String ACTIVITY_STATUS = "activityStatus";

    private static final String COHORT_TAG_NAME = "cohortTagName";
    private final TextValueProvider defaultValueProvider = new TextValueProvider();
    private final TextValueProvider booleanValueProvider = new BooleanValueProvider();
    private final TextValueProvider pickListValueProvider = new PickListValueProvider();
    private final Map<QuestionType, TextValueProvider> valueProviders = Map.of(
            QuestionType.CHECKBOX, booleanValueProvider,
            QuestionType.NUMBER, defaultValueProvider,
            QuestionType.BOOLEAN, booleanValueProvider,
            QuestionType.COMPOSITE, defaultValueProvider,
            QuestionType.AGREEMENT, booleanValueProvider,
            QuestionType.MATRIX, defaultValueProvider,
            QuestionType.DATE, new DateValueProvider(),
            QuestionType.OPTIONS, pickListValueProvider,
            QuestionType.JSON_ARRAY, defaultValueProvider,
            QuestionType.RADIO, pickListValueProvider
    );
    private final Map<String, TextValueProvider> specialValueProviders = Map.of(
            AMBULATION, new AmbulationValueProvider(),
            ACTIVITY_STATUS, new ActivityStatusValueProvider(),
            COHORT_TAG_NAME, new CohortTagNameProvider()
    );

    public TextValueProvider getValueProvider(String participantColumnName, String questionType) {
        if (isSpecialColumn(participantColumnName)) {
            return specialValueProviders.get(participantColumnName);
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
