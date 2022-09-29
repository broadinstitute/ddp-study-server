
package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.converters.split.SpaceSplittingStrategy;

/**
 * A production implementation for building the column name of `medical_record_abstraction_field`
 */
public class MedicalRecordFinalColumnNameBuilderLive implements MedicalRecordFinalColumnNameBuilder {

    CamelCaseConverter camelCaseConverter;

    public MedicalRecordFinalColumnNameBuilderLive(CamelCaseConverter camelCaseConverter) {
        this.camelCaseConverter = camelCaseConverter;
        this.camelCaseConverter.setSplittingStrategy(new SpaceSplittingStrategy());
    }

    /**
     * returns the column name of `ddp_medical_record_final`
     * @param displayName a display name of the concrete record of `medical_record_abstraction_field`
     */
    @Override
    public String apply(String displayName) {
        camelCaseConverter.setStringToConvert(displayName);
        return camelCaseConverter.convert();
    }

}


