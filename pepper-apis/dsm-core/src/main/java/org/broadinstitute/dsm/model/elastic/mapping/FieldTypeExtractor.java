package org.broadinstitute.dsm.model.elastic.mapping;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;

public class FieldTypeExtractor implements TypeExtractor<Map<String, String>> {

    private static final Map<String, String> cachedFieldTypes = new HashMap<>();

    private String[] fields;
    private String index;

    @Override
    public Map<String, String> extract() {
        if (isFieldNotCached()) {
            Map<String, GetFieldMappingsResponse.FieldMappingMetaData> mapping = getMapping().get(index);
            Map<String, String> fieldTypeMapping = new HashMap<>();
            for (Map.Entry<String, GetFieldMappingsResponse.FieldMappingMetaData> entry : mapping.entrySet()) {
                fieldTypeMapping.put(getRightMostFieldName(entry.getKey()), extractType(entry.getKey(), entry.getValue()));
            }
            cachedFieldTypes.putAll(fieldTypeMapping);
        }
        return cachedFieldTypes;
    }

    private boolean isFieldNotCached() {
        return Arrays.stream(fields)
                .map(this::getRightMostFieldName)
                .anyMatch(field -> !cachedFieldTypes.containsKey(field));
    }

    private String extractType(String fullFieldName, GetFieldMappingsResponse.FieldMappingMetaData value) {
        String key = getRightMostFieldName(fullFieldName);
        return (String) ((Map<String, Object>) value.sourceAsMap().get(key)).get(MappingGenerator.TYPE);
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
