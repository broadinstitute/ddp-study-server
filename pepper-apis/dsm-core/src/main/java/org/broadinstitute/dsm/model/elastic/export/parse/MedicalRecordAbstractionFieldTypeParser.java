package org.broadinstitute.dsm.model.elastic.export.parse;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MedicalRecordAbstractionFieldTypeParser extends DynamicFieldsParser {

    private String type;
    private final BaseParser baseParser;
    private List<Map<String, String>> possibleValues;

    public MedicalRecordAbstractionFieldTypeParser(BaseParser baseParser) {
        this.baseParser = baseParser;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public Object parse(String columnName) {
        Object parsedType;
        var fieldType = MedicalRecordAbstractionFieldType.of(type);
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
            case MULTI_TYPE_ARRAY:
                
                parsedType = forMultiTypeArray(columnName);
                break;
            default:
                parsedType = forString(columnName);
        }
        return parsedType;
    }

    private Object forMultiTypeArray(String columnName) {
        return new HashMap<String, Object>(Map.of(
                "type", "nested",
                "properties", new HashMap<>(Map.of(

                ))
        ));
    }

    protected Object forMultiOptions(String columnName) {
        return new HashMap<String, Object>(Map.of(
                "type", "nested",
                "properties", new HashMap<>(Map.of(
                        "other", baseParser.forString(columnName),
                        "values", new HashMap<>(Map.of(
                                "type", "nested",
                                "properties", new HashMap<>(Map.of(
                                        "value", baseParser.forString(columnName)))))))));
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
        return new HashMap<String, Object>(Map.of(
                "properties", new HashMap<>(Map.of(
                        "dateString", baseParser.forDate(value),
                        "est", baseParser.forBoolean(value)))));
    }

    @Override
    protected Object forString(String value) {
        return baseParser.forString(value);
    }


    public void setPossibleValues(String possibleValues) {
        this.possibleValues = ObjectMapperSingleton.readValue(possibleValues, new TypeReference<>() {});
    }

    public List<Map<String, String>> getPossibleValues() {
        return possibleValues;
    }

}
