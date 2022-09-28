package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionTransformer;

public class MedicalRecordAbstractionTextTransformer extends MedicalRecordAbstractionTransformer {

    @Override
    public Map<String, Object> toMap(String fieldName, String value) {
        return new HashMap<>(Map.of(fieldName, value));
    }

}

