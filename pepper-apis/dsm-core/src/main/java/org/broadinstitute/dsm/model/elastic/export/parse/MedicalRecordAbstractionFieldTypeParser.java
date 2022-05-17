package org.broadinstitute.dsm.model.elastic.export.parse;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import static org.broadinstitute.dsm.statics.DBConstants.*;
import static org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator.*;
import static org.broadinstitute.dsm.util.AbstractionUtil.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MedicalRecordAbstractionFieldTypeParser extends DynamicFieldsParser {

    private String type;
    private final BaseParser baseParser;
    private List<Map<String, String>> possibleValues;

    public static final String OTHER = "other";
    public static final String VALUES = "values";
    public static final String EST = "est";

    public MedicalRecordAbstractionFieldTypeParser(BaseParser baseParser) {
        this.baseParser = baseParser;
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
            case TEXT:
            case TEXT_AREA:
            case BUTTON_SELECT:
                parsedType = forString(columnName);
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

        Map<String, Object> innerMapping = new LinkedHashMap<>();

        Map<String, Object> finalMapping = new HashMap<>(Map.of(
                TYPE, NESTED,
                PROPERTIES, innerMapping));

        for (Map<String, String> possibleValue : possibleValues) {
            String fieldName = Util.spacedLowerCaseToCamelCase(possibleValue.get(VALUE));
            String fieldType = possibleValue.get(TYPE);
            this.setType(fieldType);
            Object fieldMapping = this.parse(columnName);
            innerMapping.put(fieldName, fieldMapping);
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
        this.possibleValues = ObjectMapperSingleton.readValue(possibleValues, new TypeReference<List>() {
        });
    }

    public List<Map<String, String>> getPossibleValues() {
        return possibleValues;
    }

}
