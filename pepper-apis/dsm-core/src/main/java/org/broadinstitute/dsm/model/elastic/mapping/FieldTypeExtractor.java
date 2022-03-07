package org.broadinstitute.dsm.model.elastic.mapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;

public class FieldTypeExtractor implements TypeExtractor<Map<String, String>> {

    private String[] fields;
    private String index;

    @Override
    public Map<String, String> extract() {
        Map<String, GetFieldMappingsResponse.FieldMappingMetaData> mapping = getMapping().get(index);
        Map<String, String> fieldTypeMapping = new HashMap<>();
        for (Map.Entry<String, GetFieldMappingsResponse.FieldMappingMetaData> entry : mapping.entrySet()) {
            fieldTypeMapping.put(getRightMostFieldName(entry.getKey()), extractType(entry.getKey(), entry.getValue()));
        }
        return fieldTypeMapping;
    }

    private String extractType(String fullFieldName, GetFieldMappingsResponse.FieldMappingMetaData value) {
        String key = getRightMostFieldName(fullFieldName);
        return (String) ((Map<String, Object>) value.sourceAsMap().get(key)).get(MappingGenerator.TYPE);
    }

    private String getRightMostFieldName(String fullFieldName) {
        String[] splittedFieldName = fullFieldName.split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR);
        String key = splittedFieldName[splittedFieldName.length - 1];
        return key;
    }

    private Map<String, Map<String, GetFieldMappingsResponse.FieldMappingMetaData>> getMapping() {
        GetFieldMappingsRequest request = new GetFieldMappingsRequest();
        request.indices(index);
        request.fields(fields);
        GetFieldMappingsResponse response;
        try {
            response = ElasticSearchUtil.getClientInstance().indices().getFieldMapping(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return response.mappings();
    }

    @Override
    public void setIndex(String index) {
        this.index = index;
    }

    @Override
    public void setFields(String... fields) {
        this.fields = fields;
    }

}
