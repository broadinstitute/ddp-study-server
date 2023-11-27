package org.broadinstitute.dsm.service.onchistory;

import static org.broadinstitute.dsm.statics.DBConstants.ACCESSION_NUMBER;
import static org.broadinstitute.dsm.statics.DBConstants.DATE_PX;
import static org.broadinstitute.dsm.statics.DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS;
import static org.broadinstitute.dsm.statics.DBConstants.DESTRUCTION_POLICY;
import static org.broadinstitute.dsm.statics.DBConstants.FACILITY;
import static org.broadinstitute.dsm.statics.DBConstants.FAX;
import static org.broadinstitute.dsm.statics.DBConstants.FIELD_SETTINGS_ALIAS;
import static org.broadinstitute.dsm.statics.DBConstants.HISTOLOGY;
import static org.broadinstitute.dsm.statics.DBConstants.LOCATION_PX;
import static org.broadinstitute.dsm.statics.DBConstants.PHONE;
import static org.broadinstitute.dsm.statics.DBConstants.REQUEST;
import static org.broadinstitute.dsm.statics.DBConstants.TYPE_PX;

import java.util.LinkedHashMap;
import java.util.Map;

import org.broadinstitute.dsm.exception.DSMBadRequestException;

// TODO temporary for development, will store in DB or config
public class CodeStudyColumnsProvider implements StudyColumnsProvider {
    public Map<String, OncHistoryUploadColumn> getColumnsForStudy(String realm) {
        if (realm.equals("osteo2")) {
            return getOsteo2Columns();
        } else if (realm.equals("cmi-lms")) {
            return getLmsColumns();
        }

        throw new DSMBadRequestException("Unsupported realm " + realm);
    }

    private Map<String, OncHistoryUploadColumn> getOsteo2Columns() {
        Map<String, OncHistoryUploadColumn> columns = new LinkedHashMap<>();
        columns.put("DATE_PX", new OncHistoryUploadColumn(DATE_PX, "DATE_PX", DDP_ONC_HISTORY_DETAIL_ALIAS, "d", "Date of PX"));
        columns.put("TYPE_PX", new OncHistoryUploadColumn(TYPE_PX, "TYPE_PX", DDP_ONC_HISTORY_DETAIL_ALIAS, "s", "Type of PX"));
        columns.put("LOCATION_PX", new OncHistoryUploadColumn(LOCATION_PX, "LOCATION_PX", DDP_ONC_HISTORY_DETAIL_ALIAS, "s",
                "Location of PX"));
        columns.put("HISTOLOGY", new OncHistoryUploadColumn(HISTOLOGY, "HISTOLOGY", DDP_ONC_HISTORY_DETAIL_ALIAS, "s",
                "Histology"));
        columns.put("ACCESSION", new OncHistoryUploadColumn(ACCESSION_NUMBER, "ACCESSION", DDP_ONC_HISTORY_DETAIL_ALIAS, "s",
                "Accession Number"));
        columns.put("FACILITY", new OncHistoryUploadColumn(FACILITY, "FACILITY", DDP_ONC_HISTORY_DETAIL_ALIAS, "s", "Facility"));
        columns.put("PHONE", new OncHistoryUploadColumn(PHONE, "PHONE", DDP_ONC_HISTORY_DETAIL_ALIAS, "s", "Phone"));
        columns.put("FAX", new OncHistoryUploadColumn(FAX, "FAX", DDP_ONC_HISTORY_DETAIL_ALIAS, "s", "Fax"));
        columns.put("DESTRUCTION", new OncHistoryUploadColumn(DESTRUCTION_POLICY, "DESTRUCTION", DDP_ONC_HISTORY_DETAIL_ALIAS, "s",
                "Destruction Policy (years)"));
        columns.put("BLOCKS_WITH_TUMOR", new OncHistoryUploadColumn("BLOCKS_WITH_TUMOR", "BLOCKS_WITH_TUMOR", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("TUMOR_SIZE", new OncHistoryUploadColumn("TUMOR_SIZE", "TUMOR_SIZE", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("LOCAL_CONTROL", new OncHistoryUploadColumn("LOCAL_CONTROL", "LOCAL_CONTROL", FIELD_SETTINGS_ALIAS, "o"));
        columns.put("NECROSIS", new OncHistoryUploadColumn("NECROSIS", "NECROSIS", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("VIABLE_TUMOR", new OncHistoryUploadColumn("VIABLE_TUMOR", "VIABLE_TUMOR", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("FFPE", new OncHistoryUploadColumn("FFPE", "FFPE", FIELD_SETTINGS_ALIAS, "o"));
        columns.put("DECALCIFICATION", new OncHistoryUploadColumn("DECALCIFICATION", "DECALCIFICATION", FIELD_SETTINGS_ALIAS, "o"));
        columns.put("BLOCK_TO_REQUEST", new OncHistoryUploadColumn("BLOCK_TO_REQUEST", "BLOCK_TO_REQUEST", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("REQUEST_STATUS", new OncHistoryUploadColumn(REQUEST, "REQUEST_STATUS", DDP_ONC_HISTORY_DETAIL_ALIAS, "o",
                "Request Status"));
        return columns;
    }

    private Map<String, OncHistoryUploadColumn> getLmsColumns() {
        Map<String, OncHistoryUploadColumn> columns = new LinkedHashMap<>();
        columns.put("DATE_PX", new OncHistoryUploadColumn(DATE_PX, "DATE_PX", DDP_ONC_HISTORY_DETAIL_ALIAS, "d", "Date of PX"));
        columns.put("TYPE_PX", new OncHistoryUploadColumn(TYPE_PX, "TYPE_PX", DDP_ONC_HISTORY_DETAIL_ALIAS, "s", "Type of PX"));
        columns.put("LOCATION_PX", new OncHistoryUploadColumn(LOCATION_PX, "LOCATION_PX", DDP_ONC_HISTORY_DETAIL_ALIAS, "s",
                "Location of PX"));
        columns.put("HISTOLOGY", new OncHistoryUploadColumn(HISTOLOGY, "HISTOLOGY", DDP_ONC_HISTORY_DETAIL_ALIAS, "s",
                "Histology"));
        columns.put("ACCESSION", new OncHistoryUploadColumn(ACCESSION_NUMBER, "ACCESSION", DDP_ONC_HISTORY_DETAIL_ALIAS, "s",
                "Accession Number"));
        columns.put("FACILITY_PX", new OncHistoryUploadColumn(FACILITY, "FACILITY_PX", DDP_ONC_HISTORY_DETAIL_ALIAS, "s",
                "Facility"));
        columns.put("TUMOR_SIZE", new OncHistoryUploadColumn("TUMOR_SIZE", "TUMOR_SIZE", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("NECROSIS", new OncHistoryUploadColumn("NECROSIS", "NECROSIS", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("VIABLE_TUMOR", new OncHistoryUploadColumn("VIABLE_TUMOR", "VIABLE_TUMOR", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("TX_EFFECT", new OncHistoryUploadColumn("TX_EFFECT", "TX_EFFECT", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("BLOCKS_TO_REQUEST", new OncHistoryUploadColumn("BLOCKS_TO_REQUEST", "BLOCKS_TO_REQUEST", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("SLIDES_TO_REQUEST", new OncHistoryUploadColumn("SLIDES_TO_REQUEST", "SLIDES_TO_REQUEST", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("SLIDES_TOTAL", new OncHistoryUploadColumn("SLIDES_TOTAL", "SLIDES_TOTAL", FIELD_SETTINGS_ALIAS, "s"));
        columns.put("FACILITY_PATH_REVIEW", new OncHistoryUploadColumn("FACILITY_PATH_REVIEW", "FACILITY_PATH_REVIEW",
                FIELD_SETTINGS_ALIAS, "s"));
        columns.put("REQUEST_STATUS", new OncHistoryUploadColumn(REQUEST, "REQUEST_STATUS", DDP_ONC_HISTORY_DETAIL_ALIAS, "o",
                "Request Status"));
        return columns;
    }
}
