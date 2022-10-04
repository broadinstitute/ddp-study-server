package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping;

import java.util.Map;

public class TextMappingGenerator extends MedicalRecordAbstractionMappingGenerator {

    @Override
    public Map<String, Object> toMap(String fieldName) {
        return (Map) baseParser.forString(fieldName);
    }

}
