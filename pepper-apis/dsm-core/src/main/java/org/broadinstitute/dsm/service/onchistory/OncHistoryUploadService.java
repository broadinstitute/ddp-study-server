package org.broadinstitute.dsm.service.onchistory;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.statics.DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS;
import static org.broadinstitute.dsm.statics.DBConstants.FIELD_SETTINGS_ALIAS;
import static org.broadinstitute.dsm.statics.ESObjectConstants.DYNAMIC_FIELDS;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_CREATED;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_DETAIL;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_ID;
import static org.broadinstitute.dsm.statics.ESObjectConstants.PARTICIPANT_ID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.files.parser.onchistory.OncHistoryParser;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class OncHistoryUploadService {

    private final String realm;
    private final String userId;
    private final StudyColumnsProvider studyColumnsProvider;
    private Map<String, OncHistoryUploadColumn> studyColumns;
    private ColumnValidator columnValidator;
    private String participantIndex;
    private int ddpInstanceId;
    private OncHistoryElasticUpdater elasticUpdater;
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

        try {
            DDPInstanceDto ddpInstance = DDPInstanceDao.of().getDDPInstanceByInstanceName(realm).orElseThrow();
            ddpInstanceId = ddpInstance.getDdpInstanceId();
            participantIndex = ddpInstance.getEsParticipantIndex();
        } catch (Exception e) {
            throw new DSMBadRequestException("Invalid realm: " + realm);
        }

        initColumnsForStudy();

        // get picklists for study
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        List<FieldSettingsDto> pickLists = fieldSettingsDao.getOptionAndRadioFieldSettingsByInstanceId(ddpInstanceId);

        columnValidator = new ColumnValidator(pickLists);

        setElasticUpdater(new OncHistoryElasticUpdater(participantIndex));
        initialized = true;
    }

    protected void setElasticUpdater(OncHistoryElasticUpdater elasticUpdater) {
        this.elasticUpdater = elasticUpdater;
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
        log.info("Validated {} rows for onc history upload", rows.size());

        // verify each participant ID for the study and get an associated medical record ID
        Map<Integer, Integer> participantMedIds = getParticipantIds(rows,
                new ESParticipantIdProvider(realm, participantIndex), true);
        log.info("Processing {} participants for onc history upload", participantMedIds.size());

        // ensure oncHistory record for each participant
        createOncHistoryRecords(rows);

        // write OncHistoryDetails to DB
        writeToDb(rows, participantMedIds);
        log.info("Wrote {} rows of onc history to DB", rows.size());

        // update ES
        writeToES(rows);
        log.info("Updated {} rows of onc history in ES", rows.size());
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
     * validate row contents and convert certain columns to json additionalValues
     */
    protected void validateRows(List<OncHistoryRecord> rows) {
        for (OncHistoryRecord row : rows) {
            for (Map.Entry<String, String> entry : row.getColumns().entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    // TODO perhaps gather N errors into a buffer or on OncHistoryRecord and report multiple problems?
                    ColumnValidatorResponse res = validateColumn(entry.getKey(), entry.getValue(), row);
                    if (res.newValue != null && !res.newValue.isEmpty()) {
                        entry.setValue(res.newValue);
                    }
                }
            }
            // remove the columns that were moved to additionalValues (here to avoid problems with iterator)
            row.getColumns().entrySet().removeIf(entry ->
                    studyColumns.get(entry.getKey()).getTableAlias().equals(FIELD_SETTINGS_ALIAS));
        }
    }

    protected ColumnValidatorResponse validateColumn(String columnName, String value, OncHistoryRecord row) {
        if (value.isEmpty()) {
            return new ColumnValidatorResponse();
        }
        OncHistoryUploadColumn col = studyColumns.get(columnName);
        // assertion
        if (col == null) {
            throw new DsmInternalError("Invalid column name: " + columnName);
        }
        ColumnValidatorResponse res = columnValidator.validate(value, columnName, col.getParseType());
        if (!res.valid) {
            throw new OncHistoryValidationException(res.errorMessage);
        }

        // move certain row values to additionalValues
        if (col.getTableAlias().equals(FIELD_SETTINGS_ALIAS)) {
            JsonObject json = row.getAdditionalValues();
            json.addProperty(col.getColumnName(), value);
        }
        return res;
    }

    /**
     * Create or update OncHistory record for each participant, and update ES as needed
     */
    protected void createOncHistoryRecords(List<OncHistoryRecord> rows) {
        Set<Integer> participantIds = new HashSet<>();

        for (OncHistoryRecord row: rows) {
            if (!participantIds.contains(row.getParticipantId())) {
                createOncHistory(row);
                participantIds.add(row.getParticipantId());
            }
        }
    }

    /**
     * Create or update OncHistory record, and update ES if needed
     */
    protected void createOncHistory(OncHistoryRecord row) {
        OncHistoryDto oncHistoryDto;
        boolean updateEs = true;
        int participantId = row.getParticipantId();
        try {
            Optional<OncHistoryDto> res = OncHistoryDao.getByParticipantId(participantId);
            if (res.isEmpty()) {
                oncHistoryDto = new OncHistoryDto.Builder()
                        .withParticipantId(participantId)
                        .withChangedBy(userId)
                        .withLastChangedNow()
                        .withCreatedNow().build();
                OncHistoryDao oncHistoryDao = new OncHistoryDao();
                oncHistoryDto.setOncHistoryId(oncHistoryDao.create(oncHistoryDto));
            } else {
                oncHistoryDto = res.get();
                String createdDate = oncHistoryDto.getCreated();
                if (createdDate == null || createdDate.isEmpty()) {
                    NameValue created = OncHistory.setOncHistoryCreated(Integer.toString(participantId), userId);
                    oncHistoryDto.setCreated(created.getValue().toString());
                } else {
                    updateEs = false;
                }
            }
        } catch (Exception e) {
            throw new DsmInternalError("Error updating onc history record for participant " + participantId, e);
        }

        if (updateEs) {
            Map<String, Object> oncHistory = new HashMap<>();
            oncHistory.put(PARTICIPANT_ID, participantId);
            oncHistory.put(ONC_HISTORY_ID, oncHistoryDto.getOncHistoryId());
            oncHistory.put(ONC_HISTORY_CREATED, oncHistoryDto.getCreated());

            Map<String, Object> parent = new HashMap<>();
            parent.put(ONC_HISTORY, oncHistory);
            Map<String, Object> update = Map.of(ESObjectConstants.DSM, parent);

            elasticUpdater.update(update, row.getDdpParticipantId());
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
        Map<String, String> uploadColByDbCol = getUploadColByDbCol();

        // TODO see about doing all rows in one transaction
        for (OncHistoryRecord row : rows) {
            Integer medId = participantMedIds.get(row.getParticipantId());
            // assertion
            if (medId == null) {
                throw new DsmInternalError("No medical record ID for participant " + row.getParticipantId());
            }
            int recordId = createOncHistoryDetail(row, userId, medId, insertQuery, dbColNames, uploadColByDbCol);
            row.setRecordId(recordId);
        }
    }

    protected List<String> getOrderedDbColumnNames() {
        return studyColumns.values().stream().filter(col -> col.getTableAlias().equals(DDP_ONC_HISTORY_DETAIL_ALIAS))
                .map(OncHistoryUploadColumn::getColumnName)
                .collect(Collectors.toList());
    }

    protected static String constructInsertQuery(List<String> orderedColumns) {
        StringBuilder sb = new StringBuilder("INSERT INTO ddp_onc_history_detail SET"
                + " medical_record_id = ?"
                + ", last_changed = ?"
                + ", changed_by = ?"
                + ", additional_values_json = ?");
        for (String colName : orderedColumns) {
            sb.append(String.format(", %s = ?", colName));
        }
        return sb.toString();
    }

    protected static int createOncHistoryDetail(OncHistoryRecord row, String changedBy, int medicalRecordId,
                                                String insertQuery, List<String> orderedColNames,
                                                Map<String, String> uploadColByDbCol) {
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
                for (String colName : orderedColNames) {
                    String value = recordCols.get(uploadColByDbCol.get(colName));
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
        // TODO can be optimized by collecting oncHistoryDetail by participant
        for (OncHistoryRecord row : rows) {
            Map<String, Object> oncHistory = new HashMap<>();

            Map<String, String> colValues = row.getColumns();
            for (var entry : colValues.entrySet()) {
                String value = entry.getValue();
                if (value != null && !value.isEmpty()) {
                    String name = studyColumns.get(entry.getKey()).getColumnName();
                    oncHistory.put(CamelCaseConverter.of(name).convert(), value);
                }
            }

            JsonObject additionalValues = row.getAdditionalValues();
            if (!additionalValues.entrySet().isEmpty()) {
                Map<String, Object> dynamicFields = new HashMap<>();
                for (var prop : additionalValues.entrySet()) {
                    dynamicFields.put(CamelCaseConverter.of(prop.getKey()).convert(), prop.getValue().getAsString());
                }
                oncHistory.put(DYNAMIC_FIELDS, dynamicFields);
            }

            // ES document also includes DPP instance ID and oncHistoryDetail record ID
            oncHistory.put("ddpInstanceId", ddpInstanceId);
            oncHistory.put("oncHistoryDetailId", row.getRecordId());

            Map<String, Object> parent = new HashMap<>();
            parent.put(ONC_HISTORY_DETAIL, oncHistory);

            elasticUpdater.updateAppend(parent, row.getParticipantTextId());
        }
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

    protected Map<String, String> getUploadColByDbCol() {
        return studyColumns.values().stream()
                .collect(Collectors.toMap(OncHistoryUploadColumn::getColumnName, OncHistoryUploadColumn::getColumnAlias));
    }

    /**
     * For the instance study, set the study columns for this instance
     * (Provided mostly as a testing convenience)
     */
    public Map<String, OncHistoryUploadColumn> initColumnsForStudy() {
        if (studyColumns != null) {
            return studyColumns;
        }

        studyColumns = studyColumnsProvider.getColumnsForStudy(realm);
        return studyColumns;
    }

    protected Map<String, OncHistoryUploadColumn> getStudyColumns() {
        return studyColumns;
    }
}
