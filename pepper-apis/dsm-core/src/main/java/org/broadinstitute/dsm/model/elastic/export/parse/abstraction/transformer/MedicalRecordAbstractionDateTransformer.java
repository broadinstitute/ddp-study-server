package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionTransformer;
import org.broadinstitute.dsm.util.proxy.jackson.JsonParseException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class MedicalRecordAbstractionDateTransformer extends MedicalRecordAbstractionTransformer {


    @Override
    public Map<String, Object> toMap(String value) {
        Map<String, Object> result = new HashMap<>();
        try {
            return (Map) ObjectMapperSingleton.readValue(value, new TypeReference<Map<String, String>>() {});
        } catch(JsonParseException jpe) {
            return value;
        }

    }

}
