package org.broadinstitute.dsm.model.elastic.migration;

public interface MedicalRecordFinalColumnBuilder {

    String joinAndThenMapToCamelCase(String displayName, Integer orderNumber);
}
