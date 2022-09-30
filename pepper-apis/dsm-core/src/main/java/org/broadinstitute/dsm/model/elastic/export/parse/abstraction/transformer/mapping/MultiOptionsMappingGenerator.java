package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.mapping;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.PROPERTIES;
import static org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator.NESTED;

import java.util.HashMap;
import java.util.Map;

public class MultiOptionsMappingGenerator extends MedicalRecordAbstractionMappingGenerator {

    @Override
    public Map<String, Object> toMap(String fieldName) {
        return new HashMap<>(Map.of(
                TYPE, NESTED,
                PROPERTIES, new HashMap<>(Map.of(
                        OTHER, baseParser.forString(fieldName),
                        VALUES, new HashMap<>(Map.of(
                                TYPE, NESTED,
                                PROPERTIES, new HashMap<>(Map.of(
                                        VALUES, baseParser.forString(fieldName)))))))));
    }
}
