package org.broadinstitute.dsm.model.elastic.export.excel.renderer;

import static org.broadinstitute.dsm.model.QuestionType.AGREEMENT;
import static org.broadinstitute.dsm.model.QuestionType.BOOLEAN;
import static org.broadinstitute.dsm.model.QuestionType.CHECKBOX;
import static org.broadinstitute.dsm.model.QuestionType.COMPOSITE;
import static org.broadinstitute.dsm.model.QuestionType.DATE;
import static org.broadinstitute.dsm.model.QuestionType.JSON_ARRAY;
import static org.broadinstitute.dsm.model.QuestionType.MATRIX;
import static org.broadinstitute.dsm.model.QuestionType.NUMBER;
import static org.broadinstitute.dsm.model.QuestionType.OPTIONS;

import java.util.Map;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.QuestionType;

public class ValueProviderFactory {
    private static final String AMBULATION = "AMBULATION";
    private static final String ACTIVITY_STATUS = "activityStatus";
    private final ValueProvider defaultValueProvider = new TextValueProvider();
    private final ValueProvider booleanValueProvider = new BooleanValueProvider();
    private final Map<QuestionType, ValueProvider> valueProviders = Map.of(
            CHECKBOX, booleanValueProvider,
            NUMBER, new NumberValueProvider(),
            BOOLEAN, booleanValueProvider,
            COMPOSITE, new CompositeValueProvider(),
            AGREEMENT, booleanValueProvider,
            MATRIX, new MatrixValueProvider(),
            DATE, new DateValueProvider(),
            OPTIONS, new PickListValueProvider(),
            JSON_ARRAY, new JsonArrayValueProvider()
    );
    private final Map<String, ValueProvider> specialValueProviders = Map.of(
            AMBULATION, new AmbulationValueProvider(),
            ACTIVITY_STATUS, new ActivityStatusValueProvider()
    );

    public ValueProvider getValueProvider(Filter filter) {
        String name = filter.getParticipantColumn().getName();
        if (isSpecialColumn(name)) {
            return specialValueProviders.get(name);
        }
        if (filter.getType() == null) {
            return defaultValueProvider;
        }
        return valueProviders.getOrDefault(QuestionType.getByValue(filter.getType()), defaultValueProvider);
    }

    private boolean isSpecialColumn(String participantColumn) {
        return specialValueProviders.containsKey(participantColumn);
    }
}
