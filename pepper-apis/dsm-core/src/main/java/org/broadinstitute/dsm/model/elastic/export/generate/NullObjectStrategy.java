package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;

public class NullObjectStrategy implements Generator {

    @Override
    public Map<String, Object> generate() {
        return Map.of();
    }
}
