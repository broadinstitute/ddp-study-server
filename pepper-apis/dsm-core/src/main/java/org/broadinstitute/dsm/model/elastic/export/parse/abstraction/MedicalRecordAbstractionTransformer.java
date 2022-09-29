package org.broadinstitute.dsm.model.elastic.export.parse.abstraction;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnNameBuilder;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordFinalColumnNameBuilderLive;

public abstract class MedicalRecordAbstractionTransformer {

    protected final MedicalRecordFinalColumnNameBuilder columnNameBuilder;

    protected MedicalRecordAbstractionTransformer() {
        this.columnNameBuilder = new MedicalRecordFinalColumnNameBuilderLive(CamelCaseConverter.of());
    }

    public abstract Map<String, Object> toMap(String fieldName, String value);

}
