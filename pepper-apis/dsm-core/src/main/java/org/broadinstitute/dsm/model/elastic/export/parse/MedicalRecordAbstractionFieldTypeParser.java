
package org.broadinstitute.dsm.model.elastic.export.parse;

import static org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator.NESTED;
import static org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator.PROPERTIES;
import static org.broadinstitute.dsm.model.elastic.export.parse.TypeParser.TEXT_KEYWORD_MAPPING;
import static org.broadinstitute.dsm.statics.DBConstants.VALUE;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.converters.split.SpaceSplittingStrategy;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping.DateMappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping.MultiOptionsMappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping.NumericMappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping.OptionsMappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.mapping.TextMappingGenerator;
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
                parsedType = new DateMappingGenerator().toMap(columnName);
                break;
            case NUMBER:
                parsedType = new NumericMappingGenerator().toMap(columnName);
                break;
            case MULTI_OPTIONS:
                parsedType = new MultiOptionsMappingGenerator().toMap(columnName);
                break;
            case TABLE:
            case MULTI_TYPE_ARRAY:
                parsedType = forMultiTypeArray(columnName);
                break;
            case OPTIONS:
                parsedType = new OptionsMappingGenerator().toMap(columnName);
                break;
            default:
                // handles all other cases such as: text, textarea, button_select etc...
                parsedType = new TextMappingGenerator().toMap(columnName);
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
            if (Objects.isNull(fieldName)) {
                innerMapping.put(columnName, TEXT_KEYWORD_MAPPING);
            } else {
                camelCaseConverter.setStringToConvert(fieldName);
                String camelCaseFieldName = camelCaseConverter.convert();
                String fieldType = possibleValue.get(TYPE);
                this.setType(fieldType);
                Object fieldMapping = this.parse(columnName);
                innerMapping.put(camelCaseFieldName, fieldMapping);
            }
        }


        return finalMapping;
    }

    public void setPossibleValues(String possibleValues) {
        this.possibleValues = ObjectMapperSingleton.readValue(possibleValues, new TypeReference<List>() {});
    }

    public List<Map<String, String>> getPossibleValues() {
        return possibleValues;
    }

}
