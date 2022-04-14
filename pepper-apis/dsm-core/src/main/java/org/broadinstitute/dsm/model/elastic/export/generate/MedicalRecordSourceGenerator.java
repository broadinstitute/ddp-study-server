package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

public class MedicalRecordSourceGenerator extends AdditionalCollectionSourceGenerator {

    @Override
    protected Map<String, Generator> obtainStrategyByFieldName() {
        Map<String, Generator> strategyByField = new HashMap<>();
        strategyByField.put(FAX_SENT, new BaseStrategy(FAX_CONFIRMED, generatorPayload.getValue()));
        strategyByField.put(FAX_SENT_2, new BaseStrategy(FAX_CONFIRMED_2, generatorPayload.getValue()));
        strategyByField.put(FAX_SENT_3, new BaseStrategy(FAX_CONFIRMED_3, generatorPayload.getValue()));
        return strategyByField;
    }
}
