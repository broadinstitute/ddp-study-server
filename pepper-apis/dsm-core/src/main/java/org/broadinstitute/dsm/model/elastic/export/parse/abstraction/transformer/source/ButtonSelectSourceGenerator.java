package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source;

import java.util.HashMap;
import java.util.Map;

/**
 * A class which is responsible for building a map for `button_select` data type
 */
public class ButtonSelectSourceGenerator extends MedicalRecordAbstractionSourceGenerator {

    /**
     * Transforms field-value pair into map representation
     * @param fieldName a field name of a concrete record
     * @param value     a value of a concrete record
     */
    @Override
    public Map<String, Object> toMap(String fieldName, String value) {
        return new HashMap<>(Map.of(columnNameBuilder.apply(fieldName), value));
    }

}
