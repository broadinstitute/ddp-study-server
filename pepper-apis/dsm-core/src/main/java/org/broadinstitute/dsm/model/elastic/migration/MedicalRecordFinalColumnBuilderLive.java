package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.converters.split.SpaceSplittingStrategy;

public class MedicalRecordFinalColumnBuilderLive implements MedicalRecordFinalColumnNameBuilder {

    CamelCaseConverter camelCaseConverter;

    public MedicalRecordFinalColumnBuilderLive(CamelCaseConverter camelCaseConverter) {
        this.camelCaseConverter = camelCaseConverter;
        this.camelCaseConverter.setSplittingStrategy(new SpaceSplittingStrategy());
    }

    @Override
    public String joinAndThenMapToCamelCase(String displayName, Integer orderNumber) {
        String columnName = displayName;
        if (displayName != null && orderNumber != null) {
            columnName = displayName + orderNumber;
        }
        camelCaseConverter.setStringToConvert(columnName);
        return camelCaseConverter.convert();
    }

}

