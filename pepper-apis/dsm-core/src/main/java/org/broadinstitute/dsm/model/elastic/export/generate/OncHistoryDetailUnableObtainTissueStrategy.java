package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.Map;

public class OncHistoryDetailUnableObtainTissueStrategy extends UnableObtainTissueStrategy {

    public OncHistoryDetailUnableObtainTissueStrategy(GeneratorPayload generatorPayload) {
        super(generatorPayload);
    }

    @Override
    public Map<String, Object> generate() {
        if (isUnableToObtain()) {
            return Map.of();
        }
        return super.generate();
    }

    private boolean isUnableToObtain() {
        return (boolean) generatorPayload.getValue();
    }
}
