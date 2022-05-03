package org.broadinstitute.dsm.model.elastic.export.excel.renderer;

import static org.broadinstitute.dsm.model.Filter.AGREEMENT;
import static org.broadinstitute.dsm.model.Filter.BOOLEAN;
import static org.broadinstitute.dsm.model.Filter.CHECKBOX;
import static org.broadinstitute.dsm.model.Filter.COMPOSITE;
import static org.broadinstitute.dsm.model.Filter.DATE;
import static org.broadinstitute.dsm.model.Filter.JSON_ARRAY;
import static org.broadinstitute.dsm.model.Filter.MATRIX;
import static org.broadinstitute.dsm.model.Filter.NUMBER;
import static org.broadinstitute.dsm.model.Filter.OPTIONS;

import java.util.Map;

public class ValueProviderFactory {
    private final ValueProvider defaultValueProvider = new TextValueProvider();
    private final ValueProvider booleanValueProvider = new BooleanValueProvider();
    private final Map<String, ValueProvider> valueProviders = Map.of(
            CHECKBOX, booleanValueProvider,
            NUMBER, new NumberValueProvider(),
            BOOLEAN, booleanValueProvider,
            COMPOSITE, new CompositeValueProvider(),
            AGREEMENT, new AgreementValueProvider(),
            MATRIX, new MatrixValueProvider(),
            DATE, new DateValueProvider(),
            OPTIONS, new PickListValueProvider(),
            JSON_ARRAY, new JsonArrayValueProvider()
    );

    public ValueProvider getValueProvider(String type) {
        if (type == null) {
            return defaultValueProvider;
        }
        return valueProviders.getOrDefault(type, defaultValueProvider);
    }
}
