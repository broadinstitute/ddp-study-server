package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ParentChildRelationGenerator extends CollectionSourceGenerator {

    @Override
    protected Optional<Map<String, Object>> getParentWithId() {
        ValueParser valueParser = new ValueParser();
        valueParser.setFieldName(generatorPayload.getParent());
        valueParser.setPropertyInfo(getOuterPropertyByAlias());
        return Optional.of(new HashMap<>(Map.of(generatorPayload.getParent(), valueParser.parse(generatorPayload.getParentId()))));
    }
}
