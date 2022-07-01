package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import java.util.Map;

import org.broadinstitute.dsm.model.QuestionType;

public class ValueProviderFactory {
    private static final String AMBULATION = "AMBULATION";
    private static final String ACTIVITY_STATUS = "activityStatus";

    private static final String COHORT_TAG_NAME = "cohortTagName";
    private final ValueProvider defaultValueProvider = new TextValueProvider();
    private final ValueProvider booleanValueProvider = new BooleanValueProvider();
    private final ValueProvider pickListValueProvider = new PickListValueProvider();
    private final Map<QuestionType, ValueProvider> valueProviders = Map.of(
            QuestionType.CHECKBOX, booleanValueProvider,
            QuestionType.NUMBER, new NumberValueProvider(),
            QuestionType.BOOLEAN, booleanValueProvider,
            QuestionType.COMPOSITE, new CompositeValueProvider(),
            QuestionType.AGREEMENT, booleanValueProvider,
            QuestionType.MATRIX, new MatrixValueProvider(),
            QuestionType.DATE, new DateValueProvider(),
            QuestionType.OPTIONS, pickListValueProvider,
            QuestionType.JSON_ARRAY, new JsonArrayValueProvider(),
            QuestionType.RADIO, pickListValueProvider
    );
    private final Map<String, ValueProvider> specialValueProviders = Map.of(
            AMBULATION, new AmbulationValueProvider(),
            ACTIVITY_STATUS, new ActivityStatusValueProvider(),
            COHORT_TAG_NAME, new CohortTagNameProvider()
    );

    public ValueProvider getValueProvider(String participantColumnName, String questionType) {
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
