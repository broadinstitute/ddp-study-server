package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.PROPERTIES;
import static org.broadinstitute.dsm.model.elastic.export.parse.TypeParser.TEXT_KEYWORD_MAPPING;

import java.util.HashMap;
import java.util.Map;

public class OptionsMappingGenerator extends MedicalRecordAbstractionMappingGenerator {

    @Override
    public Map<String, Object> toMap(String fieldName) {
        camelCaseConverter.setStringToConvert(fieldName);
        String camelCaseColumnName = camelCaseConverter.convert();
        return new HashMap<>(Map.of(PROPERTIES, Map.of(OTHER, TEXT_KEYWORD_MAPPING, camelCaseColumnName, TEXT_KEYWORD_MAPPING)));
    }

}

