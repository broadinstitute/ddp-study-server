package org.broadinstitute.dsm.model.elastic.export;

import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.util.PatchUtil;

import java.util.Map;

public class TestPatchUtil extends PatchUtil {

    public static final String MEDICAL_RECORD_COLUMN = "medical_record_column";
    public static final String TISSUE_RECORD_COLUMN = "tissue_record_column";
    public static final String MR_PROBLEM = "mr_problem";
    public static final String DATE_FIELD = "date_field";
    public static final String NUMERIC_FIELD = "numeric_field";
    public static final String ADDITIONAL_VALUES_JSON = "additional_values_json";

    public static Map<String, DBElement> getColumnNameMap() {
        DBElement medicalRecord = new DBElement("ddp_medical_record", "m", "pr", MEDICAL_RECORD_COLUMN);
        DBElement tissueRecord = new DBElement("ddp_tissue_record", "t", "pr", TISSUE_RECORD_COLUMN);
        DBElement mrProblem = new DBElement("mr_problem", "m", "pr", MR_PROBLEM);
        DBElement dateField = new DBElement("date_field", "m", "pr", DATE_FIELD);
        DBElement numericField = new DBElement("numeric_field", "m", "pr", NUMERIC_FIELD);
        DBElement additionalValuesJson = new DBElement("additional_values_json", "m", "pr", ADDITIONAL_VALUES_JSON);
        return Map.of(
                MEDICAL_RECORD_COLUMN, medicalRecord,
                TISSUE_RECORD_COLUMN, tissueRecord,
                MR_PROBLEM, mrProblem,
                DATE_FIELD, dateField,
                NUMERIC_FIELD, numericField,
                ADDITIONAL_VALUES_JSON, additionalValuesJson
        );
    }

}
