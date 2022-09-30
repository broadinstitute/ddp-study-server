
package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.mapping;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.PROPERTIES;
import static org.broadinstitute.dsm.util.AbstractionUtil.DATE_STRING;

import java.util.HashMap;
import java.util.Map;

public class DateMappingGenerator extends MedicalRecordAbstractionMappingGenerator {

    private static final String EST = "est";

    @Override
    public Map<String, Object> toMap(String fieldName) {
        return new HashMap<>(Map.of(
                PROPERTIES, new HashMap<>(Map.of(
                        DATE_STRING, baseParser.forDate(fieldName),
                        EST, baseParser.forBoolean(fieldName)))));
    }

}
