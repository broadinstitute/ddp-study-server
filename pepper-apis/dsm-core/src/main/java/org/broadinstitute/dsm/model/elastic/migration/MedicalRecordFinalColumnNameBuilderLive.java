
package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.converters.split.SpaceSplittingStrategy;

public class MedicalRecordFinalColumnNameBuilderLive implements MedicalRecordFinalColumnNameBuilder {

    CamelCaseConverter camelCaseConverter;

    public MedicalRecordFinalColumnNameBuilderLive(CamelCaseConverter camelCaseConverter) {
        this.camelCaseConverter = camelCaseConverter;
        this.camelCaseConverter.setSplittingStrategy(new SpaceSplittingStrategy());
    }

    @Override
    public String apply(String displayName) {
        camelCaseConverter.setStringToConvert(displayName);
        return camelCaseConverter.convert();
    }

}


