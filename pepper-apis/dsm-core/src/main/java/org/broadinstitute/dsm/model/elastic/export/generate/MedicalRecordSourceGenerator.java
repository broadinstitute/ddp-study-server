package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MedicalRecordSourceGenerator extends CollectionSourceGenerator {

    @Override
    protected Optional<Map<String, Object>> getAdditionalData() {
        return Optional.ofNullable(obtainStrategyByFieldName(getFieldName()).generate());
    }

    Generator obtainStrategyByFieldName(String fieldName) {
        Map<String, Generator> strategyByField = new HashMap<>();
        strategyByField.put("faxSent", new FaxSentStrategy("faxConfirmed", generatorPayload.getValue()));
        strategyByField.put("faxSent2", new FaxSentStrategy("faxConfirmed2", generatorPayload.getValue()));
        strategyByField.put("faxSent3", new FaxSentStrategy("faxConfirmed3", generatorPayload.getValue()));
        return strategyByField.getOrDefault(fieldName, new NullObjectStrategy());
    }
}
