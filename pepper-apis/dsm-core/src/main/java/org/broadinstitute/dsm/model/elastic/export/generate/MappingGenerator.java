package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DsmNullDateException;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.mapping.FieldTypeExtractor;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MappingGenerator extends BaseGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MappingGenerator.class);

    public static final String TYPE = "type";
    public static final String NESTED = "nested";
    public static final String TYPE_KEYWORD = "keyword";
    public static final String LOWER_CASE_SORT = "lower_case_sort";
    public static final String ANALYZER = "analyzer";
    public static final String CASE_INSENSITIVE_SORT = "case_insensitive_sort";
    public static final String FIELD_DATA = "fielddata";

    private FieldTypeExtractor fieldTypeExtractor;

    public MappingGenerator(Parser parser, GeneratorPayload generatorPayload) {
        super(parser, generatorPayload);
    }

    public MappingGenerator() {
    }

    @Override
    public void setFieldTypeExtractor(FieldTypeExtractor fieldTypeExtractor) {
        this.fieldTypeExtractor = fieldTypeExtractor;
    }

    @Override
    public Map<String, Object> generate() {
        logger.info("preparing mapping to upsert");
        return getCompleteMap(construct());
    }

    @Override
    protected String getValueForParser() {
        return getFieldName();
    }

    @Override
    protected String getValueForParser(NameValue nameValue) {
        return nameValue.getCamelCaseFieldName();
    }

    public Map<String, Object> getCompleteMap(Object propertyMap) {
        String propertyName = getPropertyName();
        Map<String, Object> objectLevel = new HashMap<>(Map.of(propertyName, propertyMap));
        Map<String, Object> dsmLevelProperties = new HashMap<>(Map.of(PROPERTIES, objectLevel));
        Map<String, Map<String, Object>> dsmLevel = new HashMap<>(Map.of(DSM_OBJECT, dsmLevelProperties));
        return new HashMap<>(Map.of(PROPERTIES, dsmLevel));
    }

    @Override
    protected Map<String, Object> parseJson() throws DsmNullDateException {
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, Object> fieldsByValues = parseJsonToMapFromValue();
        if (fieldsByValues == null) {
            //This happens when the value is null, and here dsm throws an exception so that the null value is processed as single values
            throw new DsmNullDateException("fieldsByValues is null");
        }
        Map<String, String> esMap = extractDynamicFieldsEsMap(fieldsByValues);

        for (Map.Entry<String, Object> entry : fieldsByValues.entrySet()) {
            String fieldType = esMap.get(CamelCaseConverter.of(entry.getKey()).convert());
            if (StringUtils.isBlank(fieldType)) {
                parser.setFieldName(entry.getKey());
                Object eachType = parser.parse(entry.getKey());
                resultMap.put(CamelCaseConverter.of(entry.getKey()).convert(), eachType);
            }
        }
        return new HashMap<>(Map.of(ESObjectConstants.DYNAMIC_FIELDS, new HashMap<>(Map.of(PROPERTIES, resultMap))));
    }

    private Map<String, String> extractDynamicFieldsEsMap(Map<String, Object> fieldsByValues) {
        List<String> fields = fieldsByValues.keySet().stream()
                .map(field -> CamelCaseConverter.of(field).convert())
                .map(this::getFieldFullName)
                .collect(Collectors.toList());
        fieldTypeExtractor.setFields(fields.toArray(new String[] {}));
        return fieldTypeExtractor.extract();
    }

    private String getFieldFullName(String fieldName) {
        return String.join(
                DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, getPropertyName(), ESObjectConstants.DYNAMIC_FIELDS, fieldName
        );
    }
}
