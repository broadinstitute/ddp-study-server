package org.broadinstitute.dsm.model.elastic.export.parse.abstraction;

import java.util.HashMap;
import java.util.Map;

public class MedicalRecordAbstractionButtonSelectTransformer extends MedicalRecordAbstractionTransformer {

    @Override
    public Map<String, Object> toMap(String fieldName, String value) {
        return new HashMap<>(Map.of(columnNameBuilder.apply(fieldName), value));
    }

}
