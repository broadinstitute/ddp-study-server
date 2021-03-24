package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
@TableName (
        name = DBConstants.DDP_TISSUE,
        alias = DBConstants.DDP_TISSUE_ALIAS,
        primaryKey = DBConstants.TISSUE_ID,
        columnPrefix = "")
public class Tissue {

    private static final Logger logger = LoggerFactory.getLogger(Tissue.class);

    private static final String SQL_SELECT_TISSUE = "SELECT tissue_id, onc_history_detail_id, notes, count_received, tissue_type, tissue_site, tumor_type, h_e, " +
            "pathology_report, collaborator_sample_id, block_sent, expected_return, return_date, return_fedex_id, scrolls_received, sk_id, sm_id, " +
            "scrolls_count, uss_count, blocks_count, h_e_count, first_sm_id, sent_gp, last_changed, changed_by, additional_tissue_value_json, shl_work_number, " +
            "tumor_percentage, tissue_sequence FROM ddp_tissue t WHERE NOT (deleted <=> 1) AND onc_history_detail_id = ?";
    private static final String SQL_INSERT_TISSUE = "INSERT INTO ddp_tissue SET onc_history_detail_id = ?, last_changed = ?, changed_by = ?";
    public static final String SQL_SELECT_TISSUE_LAST_CHANGED = "SELECT t.last_changed FROM ddp_institution inst " +
            "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) " +
            "LEFT JOIN ddp_medical_record as m on (m.institution_id = inst.institution_id AND NOT m.deleted <=> 1) LEFT JOIN ddp_onc_history_detail as oD on (m.medical_record_id = oD.medical_record_id) " +
            "LEFT JOIN ddp_tissue as t on (t.onc_history_detail_id = oD.onc_history_detail_id) WHERE p.participant_id = ?";

    private String tissueId;
    private final String oncHistoryDetailId;

    @ColumnName (DBConstants.NOTES)
    private final String tNotes;

    @ColumnName (DBConstants.COUNT_RECEIVED)
    private final Integer countReceived;

    @ColumnName (DBConstants.TISSUE_TYPE)
    private final String tissueType;

    @ColumnName (DBConstants.TISSUE_SITE)
    private final String tissueSite;

    @ColumnName (DBConstants.TUMOR_TYPE)
    private final String tumorType;

    @ColumnName (DBConstants.H_E)
    private final String hE;

    @ColumnName (DBConstants.PATHOLOGY_REPORT)
    private final String pathologyReport;

    @ColumnName (DBConstants.COLLABORATOR_SAMPLE_ID)
    private final String collaboratorSampleId;

    @ColumnName (DBConstants.BLOCK_SENT)
    private final String blockSent;

    @ColumnName (DBConstants.SHL_WORK_NUMBER)
    private final String shlWorkNumber;

    @ColumnName (DBConstants.SCROLLS_RECEIVED)
    private final String scrollsReceived;

    @ColumnName (DBConstants.SK_ID)
    private final String skId;

    @ColumnName (DBConstants.SM_ID)
    private final String smId;

    @ColumnName (DBConstants.SENT_GP)
    private final String sentGp;

    private String changedBy;

    @ColumnName (DBConstants.TDELETED)
    private boolean tDeleted;

    @ColumnName (DBConstants.FIRST_SM_ID)
    private String firstSmId;

    @ColumnName (DBConstants.ADDITIONAL_TISSUE_VALUES)
    private String additionalValues;

    @ColumnName (DBConstants.TISSUE_RETURN_DATE)
    private String tissueReturnDate;
    //
    @ColumnName (DBConstants.RETURN_FEDEX_ID)
    private String returnFedexId;

    @ColumnName (DBConstants.EXPECTED_RETURN)
    private String expectedReturn;

    @ColumnName (DBConstants.TUMOR_PERCENTAGE)
    private String tumorPercentage;

    @ColumnName (DBConstants.TISSUE_SEQUENCE)
    private String sequenceResults;

    @ColumnName (DBConstants.SCROLLS_COUNT)
    private Integer scrollsCount;

    @ColumnName (DBConstants.USS_COUNT)
    private Integer ussCount;

    @ColumnName (DBConstants.BLOCKS_COUNT)
    private Integer blocksCount;

    @ColumnName (DBConstants.H_E_COUNT)
    private Integer hECount;



    public Tissue(String tissueId, String oncHistoryDetailId, String tNotes, Integer countReceived, String tissueType,
                  String tissueSite, String tumorType, String hE, String pathologyReport, String collaboratorSampleId,
                  String blockSent, String scrollsReceived, String skId, String smId, String sentGp, String firstSmId,
                  String additionalValues, String expectedReturn, String tissueReturnDate,
                  String returnFedexId, String shlWorkNumber, String tumorPercentage, String sequenceResults, Integer scrollsCount,
                  Integer ussCount, Integer blocksCount, Integer hECount) {
        this.tissueId = tissueId;
        this.oncHistoryDetailId = oncHistoryDetailId;
        this.tNotes = tNotes;
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
        this.additionalValues = additionalValues;
        this.expectedReturn = expectedReturn;
        this.tissueReturnDate = tissueReturnDate;
        this.returnFedexId = returnFedexId;
        this.shlWorkNumber = shlWorkNumber;
        this.tumorPercentage = tumorPercentage;
        this.sequenceResults = sequenceResults;
        this.scrollsCount = scrollsCount;
        this.hECount = hECount;
        this.blocksCount = blocksCount;
        this.ussCount = ussCount;
    }

    public static Tissue getTissue(@NonNull ResultSet rs) throws SQLException {
        Tissue tissue = new Tissue(
                rs.getString(DBConstants.TISSUE_ID),
                rs.getString(DBConstants.ONC_HISTORY_DETAIL_ID),
                rs.getString(DBConstants.DDP_TISSUE_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.NOTES),
                rs.getInt(DBConstants.COUNT_RECEIVED),
                rs.getString(DBConstants.TISSUE_TYPE),
                rs.getString(DBConstants.TISSUE_SITE),
                rs.getString(DBConstants.TUMOR_TYPE),
                rs.getString(DBConstants.H_E),
                rs.getString(DBConstants.PATHOLOGY_REPORT),
                rs.getString(DBConstants.COLLABORATOR_SAMPLE_ID),
                rs.getString(DBConstants.BLOCK_SENT),
                rs.getString(DBConstants.SCROLLS_RECEIVED),
                rs.getString(DBConstants.SK_ID),
                rs.getString(DBConstants.SM_ID),
                rs.getString(DBConstants.SENT_GP),
                rs.getString(DBConstants.FIRST_SM_ID),
                rs.getString(DBConstants.ADDITIONAL_TISSUE_VALUES),
                rs.getString(DBConstants.EXPECTED_RETURN),
                rs.getString(DBConstants.TISSUE_RETURN_DATE),
                rs.getString(DBConstants.RETURN_FEDEX_ID),
                rs.getString(DBConstants.SHL_WORK_NUMBER),
                rs.getString(DBConstants.TUMOR_PERCENTAGE),
                rs.getString(DBConstants.TISSUE_SEQUENCE),
                rs.getInt(DBConstants.SCROLLS_COUNT),
                rs.getInt(DBConstants.USS_COUNT),
                rs.getInt(DBConstants.BLOCKS_COUNT),
                rs.getInt(DBConstants.H_E_COUNT));
        return tissue;
    }

    public static List<Tissue> getTissue(@NonNull Connection conn, @NonNull String oncHistoryDetailId) {

        List<Tissue> tissue = new ArrayList<>();
        SimpleResult dbVals = new SimpleResult();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_TISSUE)) {
            stmt.setString(1, oncHistoryDetailId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tissue.add(getTissue(rs));
                }
            }
        }
        catch (SQLException ex) {
            dbVals.resultException = ex;
        }

        if (dbVals.resultException != null) {
            throw new RuntimeException("Error getting tissue for oncHistoryDetails w/ id " + oncHistoryDetailId, dbVals.resultException);
        }

        logger.info("Found " + tissue.size() + " tissue for oncHistoryDetails w/ id " + oncHistoryDetailId);
        return tissue;
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
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Error getting id of new institution ", e);
                    }
                }
                else {
                    throw new RuntimeException("Error adding new tissue for oncHistoryDetail w/ id " + oncHistoryId + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new tissue for oncHistoryDetail w/ id " + oncHistoryId, results.resultException);
        }
        else {
            return (String) results.resultValue;
        }
    }
}
