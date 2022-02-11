package org.broadinstitute.dsm.db.dao.ddp.tissue;

import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class TissueSMIDDao {
    private static final Logger logger = LoggerFactory.getLogger(TissueSMIDDao.class);

    public static final String SQL_GET_SM_ID_BASED_ON_TISSUE_ID = " SELECT * from sm_id sm where sm.tissue_id= ?   and NOT sm.deleted <=> 1";
    public static final String SQL_TYPE_ID_FOR_TYPE = "SELECT sm_id_type_id from sm_id_type where `sm_id_type` = ?";
    public static final String SQL_INSERT_SM_ID = "INSERT INTO sm_id SET tissue_id = ?, sm_id_type_id = ?, sm_id_value=?, last_changed = ?, changed_by = ?";
    public static final String SQL_INSERT_SM_ID_WITH_VALUE = "INSERT INTO sm_id SET tissue_id = ?, sm_id_type_id = ?, last_changed = ?, changed_by = ?, sm_id_value = ?";
    public static final String SQL_SELECT_SM_ID_VALUE_WITH_ID = "SELECT sm_id_value from sm_id where sm_id_value = ? and NOT sm_id_pk = ? and Not deleted <=> 1";
    public static final String SQL_SELECT_SM_ID_VALUE = "SELECT sm_id_value from sm_id where sm_id_value = ?  and Not deleted <=> 1";

    public String getTypeForName(String type) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_TYPE_ID_FOR_TYPE)) {
                stmt.setString(1, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.SM_ID_TYPE_ID);
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

        return (String) results.resultValue;
    }

    public String createNewSMIDForTissueWithValue(String tissueId, String userId, String smIdType, String smIdValue) {
        String smIdtypeId = getTypeForName(smIdType);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_SM_ID_WITH_VALUE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, tissueId);
                stmt.setString(2, smIdtypeId);
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
                    throw new RuntimeException("Error adding new sm id for tissue w/ id " + tissueId + " it was updating " + result + " rows");
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

    public String createNewSMIDForTissue(String tissueId, String userId, @NonNull String smIdType, @NonNull String smIdValue) {
        String smIdtypeId = getTypeForName(smIdType);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_SM_ID, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, tissueId);
                stmt.setString(2, smIdtypeId);
                stmt.setString(3, smIdValue);
                stmt.setLong(4, System.currentTimeMillis());
                stmt.setString(5, userId);
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
                    throw new RuntimeException("Error adding new sm id for tissue w/ id " + tissueId + " it was updating " + result + " rows");
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

    public boolean isUnique(String smIdValue, String smIdPk) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_SM_ID_VALUE_WITH_ID)) {
                stmt.setString(1, smIdValue);
                stmt.setString(2, smIdPk);// added to let updating
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
}
