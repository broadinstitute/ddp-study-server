package org.broadinstitute.dsm.model.elastic.export.parse;

import static org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator.NESTED;
import static org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator.PROPERTIES;
import static org.broadinstitute.dsm.model.elastic.export.parse.TypeParser.TEXT_KEYWORD_MAPPING;
import static org.broadinstitute.dsm.statics.DBConstants.VALUE;
import static org.broadinstitute.dsm.util.AbstractionUtil.DATE_STRING;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.converters.split.SpaceSplittingStrategy;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class MedicalRecordAbstractionFieldTypeParser extends DynamicFieldsParser {

    public static final String SINGLE_ANSWER = "singleAnswer";
    private String type;
    private final BaseParser baseParser;
    private List<Map<String, String>> possibleValues;
    protected CamelCaseConverter camelCaseConverter;

    public static final String OTHER = "other";
    public static final String VALUES = "values";
    public static final String EST = "est";

    public MedicalRecordAbstractionFieldTypeParser(BaseParser baseParser) {
        this.baseParser = baseParser;
        this.camelCaseConverter = CamelCaseConverter.of();
        this.camelCaseConverter.setSplittingStrategy(new SpaceSplittingStrategy());
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public Object parse(String columnName) {
        Object parsedType;
        MedicalRecordAbstractionFieldType fieldType = MedicalRecordAbstractionFieldType.of(type);
        switch (fieldType) {
            case DATE:
                parsedType = forDate(columnName);
                break;
            case NUMBER:
                parsedType = forNumeric(columnName);
                break;
            case MULTI_OPTIONS:
                parsedType = forMultiOptions(columnName);
                break;
            case TABLE:
            case MULTI_TYPE_ARRAY:
                parsedType = forMultiTypeArray(columnName);
                break;
            default:
                parsedType = forString(columnName);
        }
        return parsedType;
    }

    private Object forMultiTypeArray(String columnName) {

        Map<String, Object> innerMapping = new LinkedHashMap<>(Map.of(SINGLE_ANSWER, TEXT_KEYWORD_MAPPING));

        Map<String, Object> finalMapping = new HashMap<>(Map.of(
                TYPE, NESTED,
                PROPERTIES, innerMapping));

        for (Map<String, String> possibleValue : possibleValues) {
            String fieldName = possibleValue.get(VALUE);
            camelCaseConverter.setStringToConvert(fieldName);
            String camelCaseFieldName = camelCaseConverter.convert();
            String fieldType = possibleValue.get(TYPE);
            this.setType(fieldType);
            Object fieldMapping = this.parse(columnName);
            innerMapping.put(camelCaseFieldName, fieldMapping);
        }

        return finalMapping;
    }

    protected Object forMultiOptions(String columnName) {
        return new HashMap<>(Map.of(
                TYPE, NESTED,
                PROPERTIES, new HashMap<>(Map.of(
                        OTHER, baseParser.forString(columnName),
                        VALUES, new HashMap<>(Map.of(
                                TYPE, NESTED,
                                PROPERTIES, new HashMap<>(Map.of(
                                        VALUES, baseParser.forString(columnName)))))))));
    }

    @Override
    protected Object forNumeric(String value) {
        return baseParser.forNumeric(value);
    }

    @Override
    protected Object forBoolean(String value) {
        return baseParser.forBoolean(value);
    }

    @Override
    protected Object forDate(String value) {
        return new HashMap<>(Map.of(
                PROPERTIES, new HashMap<>(Map.of(
                        DATE_STRING, baseParser.forDate(value),
                        EST, baseParser.forBoolean(value)))));
    }

    @Override
    protected Object forString(String value) {
        return baseParser.forString(value);
    }


    public void setPossibleValues(String possibleValues) {
        this.possibleValues = ObjectMapperSingleton.readValue(possibleValues, new TypeReference<List>() {});
    }

    public List<Map<String, String>> getPossibleValues() {
        return possibleValues;
    }

}
