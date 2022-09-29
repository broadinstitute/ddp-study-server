package org.broadinstitute.dsm.model.elastic.migration;

import java.util.function.UnaryOperator;

/**
 * A marker interface which is thought to be a base type of any concrete instance
 * which builds the column name of `medical_record_abstraction_field`
 */
public interface MedicalRecordFinalColumnNameBuilder extends UnaryOperator<String> {}
