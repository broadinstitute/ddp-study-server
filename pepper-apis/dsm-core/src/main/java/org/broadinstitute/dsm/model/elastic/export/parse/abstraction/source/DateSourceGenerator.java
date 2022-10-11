
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source;

import static org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser.EST;
import static org.broadinstitute.dsm.util.AbstractionUtil.DATE_STRING;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.util.proxy.jackson.JsonParseException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.eclipse.jetty.util.StringUtil;

/**
 * A class which is responsible for building a map for `date` data type
 */
public class DateSourceGenerator extends MedicalRecordAbstractionSourceGenerator {

    /**
     * Transforms field-value pair into map representation
     * @param fieldName a field name of a concrete record
     * @param value     a value of a concrete record
     */
    @Override
    public Map<String, Object> toMap(String fieldName, String value) {
        String upperCaseFieldName = columnNameBuilder.apply(fieldName);
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> innerValues = ObjectMapperSingleton.readValue(value, new TypeReference<Map<String, Object>>() {});
            if (StringUtil.isBlank((String) innerValues.get(DATE_STRING))) {
                result.put(upperCaseFieldName, Map.of(EST, innerValues.get(EST)));
            } else {
                result.put(upperCaseFieldName, innerValues);
            }
        } catch (JsonParseException jpe) {
            result.put(upperCaseFieldName, value);
        }
        return result;
    }

}
