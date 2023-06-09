package org.broadinstitute.dsm.service.onchistory;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.statics.DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS;
import static org.broadinstitute.dsm.statics.DBConstants.FIELD_SETTINGS_ALIAS;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.files.parser.onchistory.OncHistoryParser;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.export.ExportFacade;
import org.broadinstitute.dsm.model.elastic.export.ExportFacadePayload;
import org.broadinstitute.dsm.model.elastic.export.generate.GeneratorPayload;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class OncHistoryUploadService {

    private final String realm;
    private final String userId;
    private final StudyColumnsProvider studyColumnsProvider;
    private Map<String, OncHistoryUploadColumn> studyColumns;
    private ColumnValidator columnValidator;
    private String participantIndex;
    private boolean initialized;
    private static final String ID_COLUMN = "RECORD_ID";


    public OncHistoryUploadService(String realm, String userId, StudyColumnsProvider studyColumnsProvider) {
        this.realm = realm;
        this.userId = userId;
        this.studyColumnsProvider = studyColumnsProvider;
        this.initialized = false;
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        int ddpInstanceId;
        try {
            Optional<DDPInstanceDto> ddpInstance = DDPInstanceDao.of().getDDPInstanceByInstanceName(realm);
            ddpInstanceId = ddpInstance.orElseThrow().getDdpInstanceId();
            participantIndex = ddpInstance.orElseThrow().getEsParticipantIndex();
        } catch (Exception e) {
            throw new DSMBadRequestException("Invalid realm: " + realm);
        }

        setColumnsForStudy();

        // get picklists for study
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        List<FieldSettingsDto> pickLists = fieldSettingsDao.getOptionAndRadioFieldSettingsByInstanceId(ddpInstanceId);

        columnValidator = new ColumnValidator(pickLists);
        initialized = true;
    }

    protected List<String> getOrderedDbColumnNames() {
        return studyColumns.values().stream().filter(col -> col.tableAlias.equals(DDP_ONC_HISTORY_DETAIL_ALIAS))
                .map(OncHistoryUploadColumn::getColumnName)
                .collect(Collectors.toList());
    }

    public void upload(String fileContent) {
        initialize();

        // parse file into list of OncHistoryRecords, checking basic file formatting issues
        OncHistoryParser parser = new OncHistoryParser(fileContent, this);
        List<OncHistoryRecord> rows;
        try {
            rows = parser.parseToObjects();
        } catch (Exception e) {
            // TODO distinguish between internal errors and parsing errors
            throw new OncHistoryValidationException(e.toString());
        }

        // validate row contents as a first pass to avoid confusing errors
        validateRows(rows);

        // verify each participant ID for the study and get an associated medical record ID
        Map<Integer, Integer> participantMedIds = getParticipantIds(rows,
                new ESParticipantIdProvider(realm, participantIndex), true);

        // write OncHistoryDetails to DB
        writeToDb(rows, participantMedIds);

        // update ES
        writeToES(rows);
    }

    /**
     * Given participant short IDs in uploaded rows, verify the short ID, and get and record associated
     * participant IDs and medical record IDs
     *
     * @throws OncHistoryValidationException for failed verification
     */
    protected Map<Integer, Integer> getParticipantIds(List<OncHistoryRecord> oncHistoryRecords,
                                                      ParticipantIdProvider participantIdProvider, boolean updateElastic) {
        Map<Integer, Integer> medIds = new HashMap<>();

        ParticipantDao participantDao = ParticipantDao.of();

        for (OncHistoryRecord rec : oncHistoryRecords) {
            int participantId = participantIdProvider.getParticipantIdForShortId(rec.getParticipantTextId());
            if (participantId == -1) {
                throw new DSMBadRequestException("Invalid participant short ID: " + rec.getParticipantTextId());
            }

            try {
                ParticipantDto participant = participantDao.get(participantId).orElseThrow();
                rec.setDdpParticipantId(participant.getDdpParticipantId().orElseThrow());
            } catch (Exception e) {
                throw new DsmInternalError("Participant not found for id " + participantId, e);
            }

            rec.setParticipantId(participantId);
            if (medIds.containsKey(participantId)) {
                continue;
            }
            int medId = OncHistoryDetail.verifyOrCreateMedicalRecord(participantId, rec.getDdpParticipantId(),
                    this.realm, updateElastic);
            medIds.put(participantId, medId);
        }
        return medIds;
    }

    /**
     *  validate row contents and convert certain columns to json additionalValues
     */
    protected void validateRows(List<OncHistoryRecord> rows) {
        for (OncHistoryRecord row: rows) {
            for (Map.Entry<String, String> entry: row.getColumns().entrySet()) {
                validateColumn(entry.getKey(), entry.getValue(), row);
                // TODO perhaps gather N errors into a buffer or on OncHistoryRecord and report multiple problems?
            }
            // remove the columns that were moved to additionalValues (here to avoid problems with iterator)
            row.getColumns().entrySet().removeIf(entry ->
                    studyColumns.get(entry.getKey()).tableAlias.equals(FIELD_SETTINGS_ALIAS));
        }
    }

    protected void validateColumn(String columnName, String value, OncHistoryRecord row) {
        if (value.isEmpty()) {
            return;
        }
        OncHistoryUploadColumn col = studyColumns.get(columnName);
        // assertion
        if (col == null) {
            throw new DsmInternalError("Invalid column name: " + columnName);
        }
        StringBuilder sb = new StringBuilder();
        boolean valid = columnValidator.validate(value, columnName, col.getParseType(), sb);
        if (!valid) {
            throw new OncHistoryValidationException(sb.toString());
        }

        // move certain row values to additionalValues
        if (col.tableAlias.equals(FIELD_SETTINGS_ALIAS)) {
            JsonObject json = row.getAdditionalValues();
            json.addProperty(col.columnName, value);
        }
    }

    /**
     * Write each row to ddp_onc_history_detail record
     *
     * @param participantMedIds map of participant ID to corresponding ddp_medical_record ID
     */
    protected void writeToDb(List<OncHistoryRecord> rows, Map<Integer, Integer> participantMedIds) {
        // get ordered list of columns to update
        List<String> dbColNames = getOrderedDbColumnNames();
        String insertQuery = constructInsertQuery(dbColNames);

        // TODO see about doing all rows in one transaction
        for (OncHistoryRecord row: rows) {
            Integer medId = participantMedIds.get(row.getParticipantId());
            // assertion
            if (medId == null) {
                throw new DsmInternalError("No medical record ID for participant " + row.getParticipantId());
            }
            int recordId = createOncHistoryDetail(row, userId, medId, insertQuery, dbColNames);
            row.setRecordId(recordId);
        }
    }

    protected static String constructInsertQuery(List<String> orderedColumns) {
        StringBuilder sb = new StringBuilder("INSERT INTO ddp_onc_history_detail SET"
                + " medical_record_id = ?"
                + ", last_changed = ?"
                + ", changed_by = ?"
                + ", additional_values_json = ?");
        for (String colName: orderedColumns) {
            sb.append(String.format(", %s = ?", colName));
        }
        return sb.toString();
    }

    protected static int createOncHistoryDetail(OncHistoryRecord row, String changedBy, int medicalRecordId,
                                                String insertQuery, List<String> orderedColNames) {
        String additionalValues = row.getAdditionalValuesString();
        Map<String, String> recordCols = row.getColumns();

        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, medicalRecordId);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, changedBy);
                stmt.setString(4, additionalValues);

                // the insert query positions are ordered the same as ordered column names
                int index = 5;
                for (String colName: orderedColNames) {
                    String value = recordCols.get(colName);
                    stmt.setString(index++, value);
                }
                int result = stmt.executeUpdate();
                if (result != 1) {
                    dbVals.resultException = new RuntimeException("Number of rows modified by executeUpdate is "
                            + result);
                } else {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        while (rs.next()) {
                            dbVals.resultValue = rs.getInt(1);
                        }
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (res.resultException != null) {
            String msg = String.format("Error adding new oncHistoryDetail for participant %s: %s",
                    row.getParticipantId(), res.resultException.toString());
            throw new DsmInternalError(msg);
        }
        return (Integer) res.resultValue;
    }

    /**
     * For each row, write column values to ES participant index
     */
    protected void writeToES(List<OncHistoryRecord> rows) {
        for (OncHistoryRecord row: rows) {
            List<NameValue> nameValues = new ArrayList<>();
            String additionalValues = row.getAdditionalValuesString();
            if (additionalValues != null) {
                nameValues.add(new NameValue("additionalValuesJson", additionalValues));
            }

            Map<String, String> colValues = row.getColumns();
            for (var entry: colValues.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    nameValues.add(new NameValue(entry.getKey(), value));
                }
            }
            try {
                exportToES(nameValues, Integer.toString(row.getParticipantId()), row.getRecordId());
            } catch (Exception e) {
                throw new DsmInternalError("Error writing OncHistoryDetail to ES for record ID " + row.getRecordId(), e);
            }
        }
    }

    private void exportToES(List<NameValue> nameValues, String participantId, int recordId) {
        GeneratorPayload generatorPayload = new GeneratorPayload(nameValues, realm, recordId);
        ExportFacadePayload exportFacadePayload =
                new ExportFacadePayload(participantIndex, participantId, generatorPayload, realm);
        ExportFacade exportFacade = new ExportFacade(exportFacadePayload);
        exportFacade.export();
    }

    /**
     * Given a map of row as column names to values, assure a participant ID was provided and create an
     * OncHistoryRecord
     */
    public static OncHistoryRecord createOncHistoryRecord(Map<String, String> rowMap) throws OncHistoryValidationException {
        if (!rowMap.containsKey(ID_COLUMN)) {
            throw new OncHistoryValidationException("Row does not contain a RECORD_ID");
        }
        String participantTextId = rowMap.get(ID_COLUMN);
        if (participantTextId.isEmpty()) {
            throw new OncHistoryValidationException("RECORD_ID column value cannot be blank");
        }
        rowMap.remove(ID_COLUMN);
        return new OncHistoryRecord(participantTextId, rowMap);
    }

    /**
     * Return a list of all valid column names for study
     */
    public List<String> getUploadColumnNames() {
        ArrayList<String> cols = new ArrayList<>();
        cols.add(ID_COLUMN);
        cols.addAll(studyColumns.keySet());
        return cols;
    }

    /**
     * For the instance study, set the study columns for this instance
     * (Provided mostly as a testing convenience)
     */
    public Map<String, OncHistoryUploadColumn> setColumnsForStudy() {
        if (studyColumns != null) {
            return studyColumns;
        }

        studyColumns = studyColumnsProvider.getColumnsForStudy(realm);
        return studyColumns;
    }
}
