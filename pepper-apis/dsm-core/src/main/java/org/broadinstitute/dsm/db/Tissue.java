package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.db.structure.UniqueField;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@TableName (name = DBConstants.DDP_TISSUE, alias = DBConstants.DDP_TISSUE_ALIAS, primaryKey = DBConstants.TISSUE_ID, columnPrefix = "")
@JsonInclude (JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties (ignoreUnknown = true)
public class Tissue {

    public static final String SQL_SELECT_TISSUE_LAST_CHANGED = "SELECT t.last_changed FROM ddp_institution inst "
            + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
            + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
            + "LEFT JOIN ddp_medical_record as m on (m.institution_id = inst.institution_id AND NOT m.deleted <=> 1) "
            + "LEFT JOIN ddp_onc_history_detail as oD on (m.medical_record_id = oD.medical_record_id) "
            + "LEFT JOIN ddp_tissue as t on (t.onc_history_detail_id = oD.onc_history_detail_id) WHERE p.participant_id = ?";
    private static final Logger logger = LoggerFactory.getLogger(Tissue.class);
    private static final String SQL_SELECT_TISSUE =
            "SELECT t.tissue_id, onc_history_detail_id, notes, count_received, tissue_type, tissue_site, tumor_type, h_e, "
                    + "pathology_report, collaborator_sample_id, block_sent, expected_return, return_date, return_fedex_id, "
                    + "scrolls_received, sk_id, sm_id, "
                    + "scrolls_count, uss_count, blocks_count, h_e_count, first_sm_id, sent_gp, last_changed, changed_by, "
                    + "additional_tissue_value_json, shl_work_number, block_id_shl, t.deleted, "
                    + "tumor_percentage, tissue_sequence, sm.sm_id_value, sm.sm_id_type_id, sm.sm_id_pk, sm.deleted, sm.tissue_id "
                    + "FROM ddp_tissue t "
                    + "LEFT JOIN sm_id sm on (sm.tissue_id = t.tissue_id AND NOT sm.deleted <=> 1 AND NOT t.deleted <=> 1) "
                    + "WHERE NOT (t.deleted <=> 1) AND onc_history_detail_id = ?";
    private static final String SQL_INSERT_TISSUE =
            "INSERT INTO ddp_tissue SET onc_history_detail_id = ?, last_changed = ?, changed_by = ?";
    @TableName (name = DBConstants.DDP_TISSUE, alias = DBConstants.DDP_TISSUE_ALIAS, primaryKey = DBConstants.TISSUE_ID, columnPrefix = "")
    @ColumnName (DBConstants.TISSUE_ID)
    private Integer tissueId;

    @ColumnName (DBConstants.ONC_HISTORY_DETAIL_ID)
    private Integer oncHistoryDetailId;

    @ColumnName (DBConstants.NOTES)
    private String notes;

    @ColumnName (DBConstants.COUNT_RECEIVED)
    private Integer countReceived;

    @ColumnName (DBConstants.TISSUE_TYPE)
    private String tissueType;

    @ColumnName (DBConstants.TISSUE_SITE)
    private String tissueSite;

    @ColumnName (DBConstants.TUMOR_TYPE)
    private String tumorType;

    @ColumnName (DBConstants.H_E)
    private String hE;

    @ColumnName (DBConstants.PATHOLOGY_REPORT)
    private String pathologyReport;

    @UniqueField(true)
    @ColumnName (DBConstants.COLLABORATOR_SAMPLE_ID)
    private String collaboratorSampleId;

    @ColumnName (DBConstants.BLOCK_SENT)
    @DbDateConversion (SqlDateConverter.STRING_DAY)
    private String blockSent;

    @ColumnName (DBConstants.SHL_WORK_NUMBER)
    private String shlWorkNumber;

    @ColumnName (DBConstants.SCROLLS_RECEIVED)
    @DbDateConversion (SqlDateConverter.STRING_DAY)
    private String scrollsReceived;

    @UniqueField(true)
    @ColumnName (DBConstants.SK_ID)
    private String skId;

    @UniqueField(true)
    @ColumnName (DBConstants.SM_ID)
    private String smId;

    @ColumnName (DBConstants.SENT_GP)
    @DbDateConversion (SqlDateConverter.STRING_DAY)
    private String sentGp;

    private String changedBy;

    @ColumnName (DBConstants.DELETED)
    private boolean deleted;

    @ColumnName (DBConstants.FIRST_SM_ID)
    private String firstSmId;

    @ColumnName (DBConstants.ADDITIONAL_TISSUE_VALUES)
    @JsonProperty ("dynamicFields")
    @SerializedName ("dynamicFields")
    private String additionalValuesJson;
    @ColumnName (DBConstants.TISSUE_RETURN_DATE)
    @DbDateConversion (SqlDateConverter.STRING_DAY)
    private String returnDate;
    @ColumnName (DBConstants.RETURN_FEDEX_ID)
    private String returnFedexId;
    @ColumnName (DBConstants.EXPECTED_RETURN)
    @DbDateConversion (SqlDateConverter.STRING_DAY)
    private String expectedReturn;
    @ColumnName (DBConstants.TUMOR_PERCENTAGE)
    private String tumorPercentage;
    @ColumnName (DBConstants.TISSUE_SEQUENCE)
    private String tissueSequence;
    @ColumnName (DBConstants.SCROLLS_COUNT)
    private Integer scrollsCount;
    @ColumnName (DBConstants.USS_COUNT)
    private Integer ussCount;
    @ColumnName (DBConstants.BLOCKS_COUNT)
    private Integer blocksCount;
    @ColumnName (DBConstants.H_E_COUNT)
    private Integer hECount;

    @ColumnName (DBConstants.BLOCK_ID_SHL)
    private String blockIdShl;

    private List<SmId> ussSMID;
    private List<SmId> scrollSMID;
    private List<SmId> heSMID;

    public Tissue() {
    }

    public Tissue(int tissueId, Integer oncHistoryDetailId, String notes, Integer countReceived, String tissueType, String tissueSite,
                  String tumorType, String hE, String pathologyReport, String collaboratorSampleId, String blockSent,
                  String scrollsReceived, String skId, String smId, String sentGp, String firstSmId, String additionalValuesJson,
                  String expectedReturn, String returnDate, String returnFedexId, String shlWorkNumber, String tumorPercentage,
                  String tissueSequence, Integer scrollsCount, Integer ussCount, Integer blocksCount, Integer hECount,
                  String blockIdShl, List<SmId> ussSMIDs, List<SmId> scrollSMIDs, List<SmId> heSMID, Integer deleted) {
        this.tissueId = tissueId;
        this.oncHistoryDetailId = oncHistoryDetailId;
        this.notes = notes;
        this.countReceived = countReceived;
        this.tissueType = tissueType;
        this.tissueSite = tissueSite;
        this.tumorType = tumorType;
        this.hE = hE;
        this.pathologyReport = pathologyReport;
        this.collaboratorSampleId = collaboratorSampleId;
        this.blockSent = blockSent;
        this.scrollsReceived = scrollsReceived;
        this.skId = skId;
        this.smId = smId;
        this.sentGp = sentGp;
        this.firstSmId = firstSmId;
        this.additionalValuesJson = additionalValuesJson;
        this.expectedReturn = expectedReturn;
        this.returnDate = returnDate;
        this.returnFedexId = returnFedexId;
        this.shlWorkNumber = shlWorkNumber;
        this.tumorPercentage = tumorPercentage;
        this.tissueSequence = tissueSequence;
        this.scrollsCount = scrollsCount;
        this.hECount = hECount;
        this.blocksCount = blocksCount;
        this.ussCount = ussCount;
        this.blockIdShl = blockIdShl;
        this.scrollSMID = scrollSMIDs;
        this.ussSMID = ussSMIDs;
        this.heSMID = heSMID;
        this.deleted = deleted == 1;
    }

    public static Tissue getTissue(@NonNull ResultSet rs) throws SQLException {
        String tissueId = rs.getString(DBConstants.TISSUE_ID);
        if (StringUtils.isBlank(tissueId)) {
            return null;
        }
        Tissue tissue = new Tissue(rs.getInt(DBConstants.TISSUE_ID), rs.getInt(DBConstants.ONC_HISTORY_DETAIL_ID),
                rs.getString(DBConstants.DDP_TISSUE_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.NOTES),
                (Integer) rs.getObject(DBConstants.COUNT_RECEIVED), rs.getString(DBConstants.TISSUE_TYPE),
                rs.getString(DBConstants.TISSUE_SITE), rs.getString(DBConstants.TUMOR_TYPE), rs.getString(DBConstants.H_E),
                rs.getString(DBConstants.PATHOLOGY_REPORT), rs.getString(DBConstants.COLLABORATOR_SAMPLE_ID),
                rs.getString(DBConstants.BLOCK_SENT), rs.getString(DBConstants.SCROLLS_RECEIVED), rs.getString(DBConstants.SK_ID),
                rs.getString(DBConstants.SM_ID), rs.getString(DBConstants.SENT_GP), rs.getString(DBConstants.FIRST_SM_ID),
                rs.getString(DBConstants.ADDITIONAL_TISSUE_VALUES), rs.getString(DBConstants.EXPECTED_RETURN),
                rs.getString(DBConstants.TISSUE_RETURN_DATE), rs.getString(DBConstants.RETURN_FEDEX_ID),
                rs.getString(DBConstants.SHL_WORK_NUMBER), rs.getString(DBConstants.TUMOR_PERCENTAGE),
                rs.getString(DBConstants.TISSUE_SEQUENCE), (Integer) rs.getObject(DBConstants.SCROLLS_COUNT),
                (Integer) rs.getObject(DBConstants.USS_COUNT), (Integer) rs.getObject(DBConstants.BLOCKS_COUNT),
                (Integer) rs.getObject(DBConstants.H_E_COUNT), rs.getString(DBConstants.BLOCK_ID_SHL),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                rs.getInt(DBConstants.DDP_TISSUE_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.DELETED));
        return tissue;
    }

    public static List<Tissue> getTissue(@NonNull Connection conn, @NonNull String oncHistoryDetailId) {
        List<Tissue> tissueList = new ArrayList<>();
        SimpleResult dbVals = new SimpleResult();
        Map<Integer, Tissue> tissues = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_TISSUE)) {
            stmt.setString(1, oncHistoryDetailId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SmId tissueSmId = getSMIds(rs);
                    Tissue tissue;
                    if (tissueSmId != null && tissues.containsKey(tissueSmId.getTissueId())) {
                        tissue = tissues.get(tissueSmId.getTissueId());
                    } else {
                        tissue = getTissue(rs);
                    }
                    if (tissueSmId != null) {
                        tissue.setSmIdBasedOnType(tissueSmId, rs);
                    }
                    tissues.put(tissue.tissueId, tissue);
                }
            }
        } catch (SQLException ex) {
            dbVals.resultException = ex;
        }

        if (dbVals.resultException != null) {
            throw new RuntimeException("Error getting tissue for oncHistoryDetails w/ id " + oncHistoryDetailId, dbVals.resultException);
        }
        tissueList.addAll(tissues.values());
        logger.info("Found " + tissueList.size() + " tissue for oncHistoryDetails w/ id " + oncHistoryDetailId);
        return tissueList;
    }

    public static SmId getSMIds(ResultSet rs) {
        return SmId.getSMIdsForTissueId(rs);
    }

    public static String createNewTissue(@NonNull String oncHistoryId, @NonNull String user) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_TISSUE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, oncHistoryId);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, user);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            logger.info("Created new tissue for oncHistoryDetail w/ id " + oncHistoryId);
                            dbVals.resultValue = rs.getString(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error getting id of new institution ", e);
                    }
                } else {
                    throw new RuntimeException(
                            "Error adding new tissue for oncHistoryDetail w/ id " + oncHistoryId + " it was updating " + result + " rows");
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new tissue for oncHistoryDetail w/ id " + oncHistoryId, results.resultException);
        } else {
            return (String) results.resultValue;
        }
    }

    @JsonProperty ("dynamicFields")
    public Map<String, Object> getDynamicFields() {
        return ObjectMapperSingleton.readValue(additionalValuesJson, new TypeReference<Map<String, Object>>() {
        });
    }

    @JsonProperty("hECount")
    public Integer gethECount() {
        return hECount;
    }

    public void setSmIdBasedOnType(SmId tissueSmId, ResultSet rs) {
        if (tissueSmId == null || tissueSmId.getSmIdType() == null) {
            return;
        }
        try {
            String type = rs.getString(DBConstants.SM_ID_TYPE_TABLE_ALIAS + "." + DBConstants.SM_ID_TYPE);
            switch (type.toLowerCase()) {
                case "he": {
                    this.heSMID.add(tissueSmId);
                    break;

                }
                case "uss": {
                    this.ussSMID.add(tissueSmId);
                    break;
                }
                case "scrolls": {
                    this.scrollSMID.add(tissueSmId);
                    break;
                }
                default:
                    logger.error("Unknown type");
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public Boolean isDeleted() {
        return deleted;
    }

    public List<SmId> getScrollSMID() {
        if (scrollSMID == null) {
            scrollSMID = new ArrayList<>();
        }
        return scrollSMID;
    }

    public List<SmId> getUssSMID() {
        if (null == ussSMID) {
            ussSMID = new ArrayList<>();
        }
        return ussSMID;
    }

    public List<SmId> getHeSMID() {
        if (null == heSMID) {
            heSMID = new ArrayList<>();
        }
        return heSMID;
    }
}
