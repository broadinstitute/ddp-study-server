package org.broadinstitute.dsm.model.elastic;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;

public class MockFieldTypeExtractor implements TypeExtractor<Map<String, String>> {
    @Override
    public Map<String, String> extract() {
        return Map.of();
    }

    @Override
    public void setIndex(String index) {

    }
}
