
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
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionFieldType;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

/**
 * A class which is responsible for building the mapping of each concrete data type of `medical_record_abstraction_field`
 * suited for the ElasticSearch.
 */
public class MedicalRecordAbstractionFieldTypeParser extends DynamicFieldsParser {

    public static final String SINGLE_ANSWER = "singleAnswer";
    public static final String OTHER         = "other";
    public static final String VALUES        = "values";
    public static final String EST           = "est";

    private String type;
    private final BaseParser baseParser;
    private List<Map<String, String>> possibleValues;
    protected CamelCaseConverter camelCaseConverter;

    public MedicalRecordAbstractionFieldTypeParser(BaseParser baseParser) {
        this.baseParser = baseParser;
        this.camelCaseConverter = CamelCaseConverter.of();
        this.camelCaseConverter.setSplittingStrategy(new SpaceSplittingStrategy());
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the map representation of mapping suited for ElasticSearch.
     * @param columnName a display name for the concrete data of `medical_record_abstraction_field'
     */
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
            case OPTIONS:
                parsedType = forOptions(columnName);
                break;
            default:
                // handles all other cases such as: text, textarea, button_select etc...
                parsedType = forString(columnName);
        }
        return parsedType;
    }

    private Object forOptions(String columnName) {
        camelCaseConverter.setStringToConvert(columnName);
        String camelCaseColumnName = camelCaseConverter.convert();
        return new HashMap<>(Map.of(PROPERTIES, Map.of(OTHER, TEXT_KEYWORD_MAPPING, camelCaseColumnName, TEXT_KEYWORD_MAPPING)));
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
