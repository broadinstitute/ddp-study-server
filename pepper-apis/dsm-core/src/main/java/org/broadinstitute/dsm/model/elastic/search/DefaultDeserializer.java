package org.broadinstitute.dsm.model.elastic.search;

import java.util.Map;

public class DefaultDeserializer extends SourceMapDeserializer {


    //used if we don't need to convert dynamic fields to pascal case
    @Override
    protected Map<String, Object> convertDynamicFieldsFromCamelCaseToPascalCase(Map<String, Object> dynamicFields) {
        return dynamicFields;
    }
}
