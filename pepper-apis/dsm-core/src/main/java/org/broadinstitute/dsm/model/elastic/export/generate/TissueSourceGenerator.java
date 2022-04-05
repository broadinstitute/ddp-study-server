package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TissueSourceGenerator extends CollectionSourceGenerator {

    @Override
    protected Optional<Map<String, Object>> getParentWithId() {
        return Optional.of(new HashMap<>(Map.of(generatorPayload.getParent(), generatorPayload.getParentId())));
    }
}
