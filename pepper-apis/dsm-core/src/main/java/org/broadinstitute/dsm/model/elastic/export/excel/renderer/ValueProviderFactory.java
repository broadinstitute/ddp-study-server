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

import org.broadinstitute.dsm.model.QuestionType;

public class ValueProviderFactory {
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

    public ValueProvider getValueProvider(String type) {
        if (type == null) {
            return defaultValueProvider;
        }
        return valueProviders.getOrDefault(QuestionType.getByValue(type), defaultValueProvider);
    }
}
