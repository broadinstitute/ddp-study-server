package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDao;
import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDto;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionFieldType;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

/**
 * A class which is responsible for building a map for `multi_type_array` and `table` data types
 */
public class MultiTypeArraySourceGenerator extends MedicalRecordAbstractionSourceGenerator {

    private static final String MULTI_TYPE_ARRAY = MedicalRecordAbstractionFieldType.MULTI_TYPE_ARRAY.asString();

    private final MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> dao;

    public MultiTypeArraySourceGenerator(MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> dao) {
        this.dao = dao;
    }

    /**
     * Transforms field-value pair into map representation
     * @param fieldName a field name of a concrete record
     * @param value     a value of a concrete record
     */
    @Override
    public Map<String, Object> toMap(String fieldName, String value) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> dynamicFields = new HashMap<>();
        String camelCaseFieldName = columnNameBuilder.apply(fieldName);
        String possibleValuesAsString = dao.getPossibleValuesByDisplayNameAndType(fieldName, MULTI_TYPE_ARRAY);
        List<Map<String, Object>> possibleValues =
                ObjectMapperSingleton.readValue(possibleValuesAsString, new TypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> values = ObjectMapperSingleton.readValue(value, new TypeReference<List<Map<String, Object>>>() {});
        for (Map<String, Object> possibleValue : possibleValues) {
            MedicalRecordAbstractionFieldType fieldType =
                    MedicalRecordAbstractionFieldType.of(String.valueOf(possibleValue.get(DBConstants.TYPE)));
            MedicalRecordAbstractionSourceGenerator transformer = MedicalRecordAbstractionSourceGeneratorFactory.getInstance(fieldType);
            String currentField = String.valueOf(possibleValue.get(DBConstants.VALUE));
            Optional<Object> currentValue = values.stream()
                    .filter(map -> map.containsKey(currentField) && Objects.nonNull(map.get(currentField)))
                    .map(map -> map.get(currentField))
                    .findFirst();
            currentValue.ifPresent(val -> {
                Map<String, Object> sourceMap = transformer.toMap(columnNameBuilder.apply(currentField), String.valueOf(val));
                dynamicFields.putAll(sourceMap);
            });
        }
        result.put(camelCaseFieldName, dynamicFields);
        return result;
    }
}
