package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;

public interface Generator {
    Map<String, Object> generate();

    default String getPropertyName() {
        throw new UnsupportedOperationException();
    }
}
