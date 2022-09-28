package org.broadinstitute.dsm.model.elastic;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.MedicalRecordFinalDto;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionFieldType;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionTransformer;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionValueTransformerFactory;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnNameBuilder;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnBuilderLive;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class MedicalRecordFinalObjectTransformer extends ObjectTransformer {

    MedicalRecordFinalColumnNameBuilder columnBuilder;

    public MedicalRecordFinalObjectTransformer(String realm) {
        super(realm, null);
        this.columnBuilder = new MedicalRecordFinalColumnBuilderLive(CamelCaseConverter.of());
    }

    @Override
    public Map<String, Object> transformObjectToMap(Object obj) {
        MedicalRecordFinalDto mrFinal = (MedicalRecordFinalDto) obj;
        MedicalRecordAbstractionFieldType fieldType = MedicalRecordAbstractionFieldType.of(mrFinal.getType());
        MedicalRecordAbstractionTransformer transformer = MedicalRecordAbstractionValueTransformerFactory.getInstance(fieldType);
        Map<String, Object> result = ObjectMapperSingleton.readValue(
                ObjectMapperSingleton.writeValueAsString(mrFinal),
                new TypeReference<Map<String, Object>>() {});
        String fieldName = columnBuilder.joinAndThenMapToCamelCase(mrFinal.getDisplayName(), mrFinal.getOrderNumber());
        String value = mrFinal.getValue();
        if (Objects.nonNull(value)) {
            Map<String, Object> dynamicFields = transformer.toMap(fieldName, value);
            result.put(ESObjectConstants.DYNAMIC_FIELDS, dynamicFields);
        }
        return result;
    }

}
