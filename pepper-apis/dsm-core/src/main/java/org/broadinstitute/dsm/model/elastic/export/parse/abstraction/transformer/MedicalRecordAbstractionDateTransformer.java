
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionTransformer;
import org.broadinstitute.dsm.util.proxy.jackson.JsonParseException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class MedicalRecordAbstractionDateTransformer extends MedicalRecordAbstractionTransformer {



    @Override
    public Map<String, Object> toMap(String fieldName, String value) {
        String upperCaseFieldName = columnNameBuilder.apply(fieldName);
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> innerValues = ObjectMapperSingleton.readValue(value, new TypeReference<Map<String, Object>>() {});
            result.put(upperCaseFieldName, innerValues);
        } catch (JsonParseException jpe) {
            result.put(upperCaseFieldName, value);
        }
        return result;
    }

}
