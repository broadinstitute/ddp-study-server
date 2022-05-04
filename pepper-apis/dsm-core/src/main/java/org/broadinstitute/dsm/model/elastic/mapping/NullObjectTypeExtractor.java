package org.broadinstitute.dsm.model.elastic.mapping;

import java.util.Map;

public class NullObjectTypeExtractor implements TypeExtractor<Map<String, String>> {

    @Override
    public Map<String, String> extract() {
        return Map.of();
    }

    @Override
    public void setIndex(String index) {

    }
}
