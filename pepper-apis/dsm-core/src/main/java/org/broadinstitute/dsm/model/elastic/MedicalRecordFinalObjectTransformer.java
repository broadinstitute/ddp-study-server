package org.broadinstitute.dsm.model.elastic;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.db.MedicalRecordFinalDto;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionFieldType;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source.MedicalRecordAbstractionSourceGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source.MedicalRecordAbstractionSourceGeneratorFactory;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnNameBuilder;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnNameBuilderLive;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;


/**
 * The class which is responsible for transforming `medical_record_final` objects into the map instances suited for ElasticSearch.
 */
public class MedicalRecordFinalObjectTransformer extends ObjectTransformer {

    MedicalRecordFinalColumnNameBuilder columnBuilder;

    public MedicalRecordFinalObjectTransformer(String realm) {
        super(realm, null);
        this.columnBuilder = new MedicalRecordFinalColumnNameBuilderLive(CamelCaseConverter.of());
    }

    /**
     * Transforms each object to the map representation
     * @param obj a concrete object of `ddp_medical_record_final`
     */
    @Override
    public Map<String, Object> transformObjectToMap(Object obj) {
        MedicalRecordFinalDto medicalRecordFinalDto = (MedicalRecordFinalDto) obj;
        MedicalRecordAbstractionFieldType fieldType = MedicalRecordAbstractionFieldType.of(medicalRecordFinalDto.getType());
        MedicalRecordAbstractionSourceGenerator transformer = MedicalRecordAbstractionSourceGeneratorFactory.getInstance(fieldType);
        Map<String, Object> result = ObjectMapperSingleton.readValue(
                ObjectMapperSingleton.writeValueAsString(medicalRecordFinalDto),
                new TypeReference<Map<String, Object>>() {});
        String value = medicalRecordFinalDto.getValue();
        if (Objects.nonNull(value)) {
            Map<String, Object> dynamicFields = transformer.toMap(medicalRecordFinalDto.getDisplayName(), value);
            result.put(ESObjectConstants.DYNAMIC_FIELDS, dynamicFields);
        }
        return result;
    }

}
