package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.mapping.FieldTypeExtractor;

public interface Generator {
    Map<String, Object> generate();

    default String getPropertyName() {
        throw new UnsupportedOperationException();
    }

    default void setFieldTypeExtractor(FieldTypeExtractor fieldTypeExtractor) {}
}
