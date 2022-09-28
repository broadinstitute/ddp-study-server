package org.broadinstitute.dsm.model.elastic;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

import org.broadinstitute.dsm.db.MedicalRecordFinalDto;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionFieldType;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionTransformer;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionValueTransformerFactory;

public class MedicalRecordFinalObjectTransformer extends ObjectTransformer {

    public MedicalRecordFinalObjectTransformer(String realm) {
        super(realm, null);
    }

    @Override
    protected Map<String, Object> extractAndConvertObjectToMap(Object obj, Field declaredField) {
        try {
            declaredField.setAccessible(true);
            Object fieldValue = declaredField.get(obj);
            Map<String, Object> result = Map.of();
            if (Objects.nonNull(fieldValue)) {
                MedicalRecordFinalDto medicalRecordFinalDto = (MedicalRecordFinalDto) fieldValue;
                MedicalRecordAbstractionFieldType fieldType =
                        MedicalRecordAbstractionFieldType.of(medicalRecordFinalDto.getType());
                MedicalRecordAbstractionTransformer transformer = MedicalRecordAbstractionValueTransformerFactory.getInstance(fieldType);
                result = transformer.toMap(medicalRecordFinalDto.getValue());
            }
            return result;
        } catch (IllegalAccessException iae) {
            throw new RuntimeException(iae);
        }
    }
}
