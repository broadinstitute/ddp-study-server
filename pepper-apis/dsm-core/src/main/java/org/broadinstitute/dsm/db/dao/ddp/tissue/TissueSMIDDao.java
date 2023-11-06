package org.broadinstitute.dsm.db.dao.ddp.tissue;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.NonNull;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.db.dao.util.DaoUtil;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TissueSMIDDao {

    private static final Logger logger = LoggerFactory.getLogger(TissueSMIDDao.class);

    public static final String SQL_GET_SM_ID_BASED_ON_TISSUE_ID =
            " SELECT * from sm_id sm where sm.tissue_id= ?   and NOT sm.deleted <=> 1";
    public static final String SQL_GET_SEQUENCING_SM_ID_BASED_ON_TISSUE_ID = " SELECT * from sm_id sm "
            + "left join sm_id_type smtype on (sm.sm_id_type_id = smtype.sm_id_type_id) "
            + "where sm.tissue_id = ? and (smtype.sm_id_type = \"uss\" or smtype.sm_id_type = \"scrolls\") "
            + "and sm.received_date is not null ";
    public static final String SQL_TYPE_ID_FOR_TYPE = "SELECT sm_id_type_id from sm_id_type where `sm_id_type` = ?";
    public static final String SQL_INSERT_SM_ID =
            "INSERT INTO sm_id SET tissue_id = ?, sm_id_type_id = ?, sm_id_value=?, last_changed = ?, changed_by = ?";
    public static final String SQL_INSERT_SM_ID_WITH_VALUE =
            "INSERT INTO sm_id SET tissue_id = ?, sm_id_type_id = ?, last_changed = ?, changed_by = ?, sm_id_value = ?";
    public static final String SQL_SELECT_SM_ID_VALUE_WITH_ID =
            "SELECT sm_id_value from sm_id where sm_id_value = ? and NOT sm_id_pk = ? and Not deleted <=> 1";
    public static final String SQL_SELECT_SM_ID_VALUE = "SELECT sm_id_value from sm_id where sm_id_value = ?  and Not deleted <=> 1";

    public static final String SQL_SELECT_ALL_SMIDS_BY_INSTANCE_NAME =
            "SELECT p.ddp_participant_id, sm.tissue_id, sm.sm_id_pk, sm.sm_id_value, sm.deleted, sm_type.sm_id_type "
                    + "FROM sm_id as sm "
                    + "LEFT JOIN sm_id_type as sm_type ON (sm.sm_id_type_id = sm_type.sm_id_type_id) "
                    + "LEFT JOIN ddp_tissue as t ON (sm.tissue_id = t.tissue_id) "
                    + "LEFT JOIN ddp_onc_history_detail as oD ON (t.onc_history_detail_id = oD.onc_history_detail_id) "
                    + "LEFT JOIN ddp_medical_record as m ON (oD.medical_record_id = m.medical_record_id) "
                    + "LEFT JOIN ddp_institution as ins ON (m.institution_id = ins.institution_id) "
                    + "LEFT JOIN ddp_participant as p ON (p.participant_id = ins.participant_id) "
                    + "LEFT JOIN ddp_instance as realm ON (realm.ddp_instance_id = p.ddp_instance_id) "
                    + "WHERE realm.instance_name = ?";

    private static final String DELETE_SM_ID = "delete from sm_id where sm_id_pk = ?";

    private static final String DELETE_TISSUE = "delete from ddp_tissue where tissue_id = ?";

    public Integer getTypeForName(String type) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_TYPE_ID_FOR_TYPE)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(DBConstants.SM_ID_TYPE_ID);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting type ids for sm id " + type, results.resultException);
        }

        return (Integer) results.resultValue;
    }

    public String createNewSMIDForTissueWithValue(int tissueId, String userId, String smIdType, String smIdValue) {
        Integer smIdtypeId = getTypeForName(smIdType);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_SM_ID_WITH_VALUE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, tissueId);
                stmt.setInt(2, smIdtypeId);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setString(4, userId);
                stmt.setString(5, smIdValue);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            logger.info("Created new sm id for tissue w/ id " + tissueId);
                            dbVals.resultValue = rs.getString(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error getting id of new sm id ", e);
                    }
                } else {
                    throw new RuntimeException(
                            "Error adding new sm id for tissue w/ id " + tissueId + " it was updating " + result + " rows");
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });


        if (results.resultException != null) {
            throw new RuntimeException("Error adding new sm id for tissue w/ id " + tissueId, results.resultException);
        } else {
            return (String) results.resultValue;
        }
    }

    public void deleteTissueById(int tissueId) {
        DaoUtil.deleteSingleRowById(tissueId, DELETE_TISSUE);
    }

    public void deleteSampleById(int sampleId) {
        DaoUtil.deleteSingleRowById(sampleId, DELETE_SM_ID);
    }

    public int createNewSMIDForTissue(int tissueId, String userId, @NonNull String smIdType, @NonNull String smIdValue) {
        Integer smIdtypeId = getTypeForName(smIdType);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_SM_ID, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, tissueId);
                stmt.setInt(2, smIdtypeId);
                stmt.setString(3, smIdValue);
                stmt.setLong(4, System.currentTimeMillis());
                stmt.setString(5, userId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            logger.info("Created new sm id for tissue w/ id " + tissueId);
                            dbVals.resultValue = rs.getInt(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error getting id of new sm id ", e);
                    }
                } else {
                    throw new RuntimeException(
                            "Error adding new sm id for tissue w/ id " + tissueId + " it was updating " + result + " rows");
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new sm id for tissue w/ id " + tissueId, results.resultException);
        } else {
            return (Integer) results.resultValue;
        }
    }

    public boolean isUnique(String smIdValue, String smIdPk) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_SM_ID_VALUE_WITH_ID)) {
                stmt.setString(1, smIdValue);
                stmt.setString(2, smIdPk); // added to let updating
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = false;
                    } else {
                        dbVals.resultValue = true;
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting values from sm_id table matching " + smIdValue, results.resultException);
        }

        return (boolean) results.resultValue;
    }

    public boolean isUnique(String smIdValue) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_SM_ID_VALUE)) {
                stmt.setString(1, smIdValue);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = false;
                    } else {
                        dbVals.resultValue = true;
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting values from sm_id table matching " + smIdValue, results.resultException);
        }

        return (boolean) results.resultValue;
    }

    public Map<String, List<SmId>> getSmIdsByParticipantByStudy(String instanceName) {
        Map<String, List<SmId>> smIds = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ALL_SMIDS_BY_INSTANCE_NAME)) {
                stmt.setString(1, instanceName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        Integer tissueId = rs.getInt(DBConstants.TISSUE_ID);
                        Integer smIdPk = rs.getInt(DBConstants.SM_ID_PK);
                        String smIdValue = rs.getString(DBConstants.SM_ID_VALUE);
                        String smType = rs.getString(DBConstants.SM_ID_TYPE);
                        Boolean deleted = rs.getBoolean(DBConstants.DELETED);
                        SmId smId = new SmId(smIdPk, smType, smIdValue, tissueId, deleted);
                        List<SmId> participantSmIds = smIds.getOrDefault(ddpParticipantId, new ArrayList<>());
                        participantSmIds.add(smId);
                        smIds.put(ddpParticipantId, participantSmIds);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of smIds for instance " + instanceName, results.resultException);
        }
        logger.info("Got " + smIds.size() + " participants smIds in DSM DB for " + instanceName);
        return smIds;
    }

    public List<String> getSequencingSmIdsForTissue(long tissueId) {
        ArrayList<String> smIds = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_SEQUENCING_SM_ID_BASED_ON_TISSUE_ID)) {
                stmt.setLong(1, tissueId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        smIds.add(rs.getString(DBConstants.SM_ID_VALUE));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Couldn't get list of smIds for tissue " + tissueId, results.resultException);
        }
        logger.info("Got " + smIds.size() + " sequencing smIds in DSM DB for " + tissueId);
        return smIds;
    }

    /** finds sm ids that belong to a tissue,
     *
      * @param tissueId
     * @return a list of sm id primary keys
     */
    public static List<String> getSmIdPksForTissue(String tissueId) {
        List<String> smIds = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_SM_ID_BASED_ON_TISSUE_ID)) {
                stmt.setString(1, tissueId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        smIds.add(rs.getString(DBConstants.SM_ID_PK));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Couldn't get list of smIds for tissue " + tissueId, results.resultException);
        }
        logger.info("Got %d smId pks in DSM DB for tissue with id %s", smIds.size() , tissueId);
        return smIds;
    }
}
