package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping;

import java.util.Map;

public class NumericMappingGenerator extends MedicalRecordAbstractionMappingGenerator {

    @Override
    public Map<String, Object> toMap(String fieldName) {
        return (Map) baseParser.forNumeric(fieldName);
    }

}

