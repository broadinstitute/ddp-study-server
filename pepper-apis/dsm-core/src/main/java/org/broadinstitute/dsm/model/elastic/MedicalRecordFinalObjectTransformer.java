package org.broadinstitute.dsm.model.elastic;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.MedicalRecordFinalDto;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionFieldType;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionTransformer;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionValueTransformerFactory;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnBuilder;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnBuilderLive;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class MedicalRecordFinalObjectTransformer extends ObjectTransformer {

    MedicalRecordFinalColumnBuilder medicalRecordFinalColumnBuilder;

    public MedicalRecordFinalObjectTransformer(String realm) {
        super(realm, null);
        this.medicalRecordFinalColumnBuilder = new MedicalRecordFinalColumnBuilderLive(CamelCaseConverter.of());
    }

    @Override
    public Map<String, Object> transformObjectToMap(Object obj) {
        MedicalRecordFinalDto mrFinal = (MedicalRecordFinalDto) obj;
        MedicalRecordAbstractionFieldType fieldType = MedicalRecordAbstractionFieldType.of(mrFinal.getType());
        MedicalRecordAbstractionTransformer transformer = MedicalRecordAbstractionValueTransformerFactory.getInstance(fieldType);
        String stringifiedDto = ObjectMapperSingleton.writeValueAsString(mrFinal);
        Map<String, Object> result = ObjectMapperSingleton.readValue(stringifiedDto, new TypeReference<Map<String, Object>>() {});
        String fieldName = medicalRecordFinalColumnBuilder.joinAndThenMapToCamelCase(mrFinal.getDisplayName(), mrFinal.getOrderNumber());
        Map<String, Object> dynamicFields = transformer.toMap(fieldName, mrFinal.getValue());
        result.put("dynamicFields", dynamicFields);
        return result;
    }

}
