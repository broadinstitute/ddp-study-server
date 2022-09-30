package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnNameBuilder;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnNameBuilderLive;

/**
 * An abstract class for creating the top-level definition of the concrete implementations
 * for MedicalRecordAbstractionTransformer which transform each type to the source mapping
 */
public abstract class MedicalRecordAbstractionSourceGenerator {

    protected final MedicalRecordFinalColumnNameBuilder columnNameBuilder;

    protected MedicalRecordAbstractionSourceGenerator() {
        this.columnNameBuilder = new MedicalRecordFinalColumnNameBuilderLive(CamelCaseConverter.of());
    }

    /**
     * Returns the map representation of the source data
     * @param fieldName a display name of each record of the type `ddp_medical_record_final`
     * @param value     a value definition of each record of the type `ddp_medical_record_final`
     */
    public abstract Map<String, Object> toMap(String fieldName, String value);

}
