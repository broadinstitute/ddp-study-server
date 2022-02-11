package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.parse.Parser;

public class CollectionMappingGenerator extends MappingGenerator {

    public CollectionMappingGenerator(Parser parser, GeneratorPayload generatorPayload) {
        super(parser, generatorPayload);
    }

    public CollectionMappingGenerator() {}

    @Override
    protected Map<String, Object> getElement(Object type) { return new HashMap<>(Map.of(getFieldName(), type));
    }

    @Override
    public Map<String, Object> construct() {
        return new HashMap<>(Map.of(TYPE, NESTED, PROPERTIES, collect()));
    }

}
