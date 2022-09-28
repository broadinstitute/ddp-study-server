package org.broadinstitute.dsm.model.elastic.migration;

public interface MedicalRecordFinalColumnNameBuilder {

    String joinAndThenMapToCamelCase(String displayName, Integer orderNumber);
}
