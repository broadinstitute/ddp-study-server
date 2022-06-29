package org.broadinstitute.dsm.model.elastic.export.tabular.renderer;

import static org.broadinstitute.dsm.model.QuestionType.AGREEMENT;
import static org.broadinstitute.dsm.model.QuestionType.BOOLEAN;
import static org.broadinstitute.dsm.model.QuestionType.CHECKBOX;
import static org.broadinstitute.dsm.model.QuestionType.COMPOSITE;
import static org.broadinstitute.dsm.model.QuestionType.DATE;
import static org.broadinstitute.dsm.model.QuestionType.JSON_ARRAY;
import static org.broadinstitute.dsm.model.QuestionType.MATRIX;
import static org.broadinstitute.dsm.model.QuestionType.NUMBER;
import static org.broadinstitute.dsm.model.QuestionType.OPTIONS;
import static org.broadinstitute.dsm.model.QuestionType.RADIO;

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
            CHECKBOX, booleanValueProvider,
            NUMBER, new NumberValueProvider(),
            BOOLEAN, booleanValueProvider,
            COMPOSITE, new CompositeValueProvider(),
            AGREEMENT, booleanValueProvider,
            MATRIX, new MatrixValueProvider(),
            DATE, new DateValueProvider(),
            OPTIONS, pickListValueProvider,
            JSON_ARRAY, new JsonArrayValueProvider(),
            RADIO, pickListValueProvider
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
