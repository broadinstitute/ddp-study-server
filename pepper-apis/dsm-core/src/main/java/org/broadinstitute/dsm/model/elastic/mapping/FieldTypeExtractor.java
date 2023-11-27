package org.broadinstitute.dsm.model.elastic.mapping;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;

public class FieldTypeExtractor implements TypeExtractor<Map<String, String>> {

    private static final Map<String, String> cachedFieldTypes = new HashMap<>();
    public static final int FIELDS_MAPPING_FETCH_SIZE = 50;

    private String[] fields;
    private String index;

    @Override
    public Map<String, String> extract() {
        if (isFieldsNotCached()) {
            Map<String, GetFieldMappingsResponse.FieldMappingMetadata> mapping = getMapping().get(index);
            Map<String, String> fieldTypeMapping = new HashMap<>();
            for (Map.Entry<String, GetFieldMappingsResponse.FieldMappingMetadata> entry : mapping.entrySet()) {
                fieldTypeMapping.put(getRightMostFieldName(entry.getKey()), extractType(entry.getKey(), entry.getValue()));
            }
            cachedFieldTypes.putAll(fieldTypeMapping);
        }
        return cachedFieldTypes;
    }

    private boolean isFieldsNotCached() {
        return this.notCachedFields().size() > 0;
    }

    private List<String> notCachedFields() {
        return Arrays.stream(fields)
                .filter(field -> !cachedFieldTypes.containsKey(this.getRightMostFieldName(field)))
                .collect(Collectors.toList());
    }

    private String extractType(String fullFieldName, GetFieldMappingsResponse.FieldMappingMetadata value) {
        String key = getRightMostFieldName(fullFieldName);
        return (String) ((Map<String, Object>) value.sourceAsMap().get(key)).get(MappingGenerator.TYPE);
    }

    private Map<String, Map<String, GetFieldMappingsResponse.FieldMappingMetadata>> getMapping() {
        Map<String, Map<String, GetFieldMappingsResponse.FieldMappingMetadata>> result = new HashMap<>();
        GetFieldMappingsRequest request = new GetFieldMappingsRequest();
        request.indices(index);
        String[] fields = this.notCachedFields().toArray(new String[] {});
        int fieldsSize = fields.length;
        for (int i = 0; i < fieldsSize; i += FIELDS_MAPPING_FETCH_SIZE) {
            int toIndex = calculateToIndex(fieldsSize, i);
            request.fields(Arrays.asList(fields).subList(i, toIndex).toArray(new String[] {}));
            try {
                result.putAll(ElasticSearchUtil.getClientInstance().indices().getFieldMapping(request, RequestOptions.DEFAULT).mappings());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private int calculateToIndex(int fieldsSize, int i) {
        int result;
        boolean isNextToIndexMoreThanFieldsSize = (i + FIELDS_MAPPING_FETCH_SIZE) > fieldsSize;
        if (isNextToIndexMoreThanFieldsSize) {
            boolean isFieldsSizeLessThanFetchSize = fieldsSize < FIELDS_MAPPING_FETCH_SIZE;
            if (isFieldsSizeLessThanFetchSize) {
                result = i + fieldsSize;
            } else {
                result = i + (fieldsSize - FIELDS_MAPPING_FETCH_SIZE);
            }
        } else {
            result = i + FIELDS_MAPPING_FETCH_SIZE;
        }
        return result;
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
