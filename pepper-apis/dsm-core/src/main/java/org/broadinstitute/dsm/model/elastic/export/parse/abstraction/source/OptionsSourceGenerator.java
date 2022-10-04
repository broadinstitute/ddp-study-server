
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.util.proxy.jackson.JsonParseException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class OptionsSourceGenerator extends MedicalRecordAbstractionSourceGenerator {

    private static final String OTHER = "other";

    @Override
    public Map<String, Object> toMap(String fieldName, String value) {
        Map<String, Object> result = new HashMap<>();
        String camelCaseFieldName = columnNameBuilder.apply(fieldName);
        try {
            Map<String, Object> values = ObjectMapperSingleton.readValue(value, new TypeReference<Map<String, Object>>() {});
            String fieldValue = String.valueOf(values.get(fieldName));
            Map<String, Object> innerMap = new HashMap<>(Map.of(
                    camelCaseFieldName, fieldValue,
                    OTHER, values.get(OTHER)));
            result.put(camelCaseFieldName, innerMap);
        } catch (JsonParseException jpe) {
            result.put(camelCaseFieldName, value);
        }
        return result;
    }

}
