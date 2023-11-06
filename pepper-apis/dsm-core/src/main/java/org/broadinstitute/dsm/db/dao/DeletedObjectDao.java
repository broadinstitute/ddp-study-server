package org.broadinstitute.dsm.db.dao;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.patch.DeleteType;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class DeletedObjectDao {

    private static final String SQL_GET_JSON_OBJECT_FOR_TISSUE = "select concat('select json_object(', group_concat( "
            + " concat(quote(column_name), ', ', column_name)), ') from ddp_tissue where tissue_id = ? ;') into @sql "
            + " from information_schema.columns where table_name = 'ddp_tissue'";

    private static final String SQL_GET_JSON_OBJECT_FOR_SM_ID = "select concat('select json_object(', group_concat( "
            + " concat(quote(column_name), ', ', column_name)), ') from sm_id where sm_id_pk = ? ;') into @sql "
            + " from information_schema.columns where table_name = 'sm_id'";

    private static final String SQL_GET_JSON_OBJECT_FOR_ONC_HISTORY = "select concat('select json_object(', group_concat( "
            + " concat(quote(column_name), ', ', column_name)),') from ddp_onc_history_detail where onc_history_detail_id = ? ;')"
            + " into @sql from information_schema.columns where table_name = 'ddp_onc_history_detail'";
    
    private static final String TABLE_PLACE_HOLDER = "$table";
    private static final String PK_PLACE_HOLDER = "$pk";

    private static final String SQL_DELETE_VALUES = "DELETE FROM $table WHERE $pk = ?";
    private static final String SQL_INSERT_DELETED_VALUES = "INSERT INTO deleted_object SET original_table = ?, original_primary_key = ? ,"
            + " data =?, deleted_by = ?,  deleted_date = ? ";

    private static final String SQL_GET_DELETED_OBJECT_BY_ORIGINAL_ID_TABLE = "SELECT data FROM deleted_object WHERE original_table = ? "
            + " AND original_primary_key = ? ";

    public static boolean deletePatch(@NonNull String id, @NonNull String user,
                                      @NonNull DBElement dbElement, DeleteType deleteType) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            //Select everything in the db as the current state of the object into a json string , this is better than creating a Dto because
            // this can be done independent of class
            String currentData = getJsonRepOfDeletedObject(conn, id, deleteType);
            //insert the values of what is being deleted into the deleted_object table
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DELETED_VALUES)) {
                stmt.setString(1, dbElement.getTableName());
                stmt.setString(2, id);
                stmt.setString(3, currentData);
                stmt.setString(4, user);
                stmt.setLong(5, System.currentTimeMillis());
                int result = stmt.executeUpdate();
                if (result != 1) {
                    log.error("Inserted more than 1 row for deleted object with id {}, type {}, number of rows updated was {}", id,
                            deleteType, result);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            //now delete from the table by primary key
            try (PreparedStatement deleteStmt = conn.prepareStatement(getDeleteQuery(deleteType))) {
                deleteStmt.setString(1, id);
                int result = deleteStmt.executeUpdate();
                if (result > 1) {
                    throw new DsmInternalError("Deleting more than one row at a time is not allowed!");
                } else {
                    log.info("Deleted from {} row with id {}", deleteType, id);
                }

            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError(String.format("Unable to delete from table %s with id %s", deleteType, id), results.resultException);
        }
        return true;

    }

    /**
     * @param conn       The db connection where all the delete transactions should happen in
     * @param id         - id of the record we are deleting, primary key
     * @param deleteType DeleteType
     * @return
     */
    private static String getJsonRepOfDeletedObject(Connection conn, String id, DeleteType deleteType) {
        //This method is to choose the right query, so we can avoid having dynamic queries with table names and risk SQL injection
        String query;
        if (deleteType.equals(DeleteType.ONC_HISTORY_DETAIL)) {
            query = SQL_GET_JSON_OBJECT_FOR_ONC_HISTORY;
        } else if (deleteType.equals(DeleteType.TISSUE)) {
            query = SQL_GET_JSON_OBJECT_FOR_TISSUE;
        } else if (deleteType.equals(DeleteType.SM_ID)) {
            query = SQL_GET_JSON_OBJECT_FOR_SM_ID;
        } else {
            throw new DsmInternalError(String.format("DeleteType %s is not configured", deleteType));
        }
        return getJsonObject(conn, query, id, deleteType);
    }

    /**
     * @param deleteType DeleteType
     * @return the correct query for deleting from that table if the deleteType is from the alloweed tables, otherwise
     * @throws DsmInternalError
     */
    private static String getDeleteQuery(DeleteType deleteType) {
        //This method is to choose the right query, so we can avoid having dynamic queries with table names and risk SQL injection
        String query = SQL_DELETE_VALUES;
        if (deleteType.equals(DeleteType.ONC_HISTORY_DETAIL)) {
            query = query.replace(TABLE_PLACE_HOLDER, DBConstants.DDP_ONC_HISTORY_DETAIL).replace(PK_PLACE_HOLDER, DBConstants.ONC_HISTORY_DETAIL_ID);
        } else if (deleteType.equals(DeleteType.TISSUE)) {
            query = query.replace(TABLE_PLACE_HOLDER, DBConstants.DDP_TISSUE).replace(PK_PLACE_HOLDER, DBConstants.TISSUE_ID);
        } else if (deleteType.equals(DeleteType.SM_ID)) {
            query = query.replace(TABLE_PLACE_HOLDER, DBConstants.SM_ID_TABLE).replace(PK_PLACE_HOLDER, DBConstants.SM_ID_PK);
        } else {
            throw new DsmInternalError(String.format("DeleteType %s is not configured", deleteType));
        }
        log.info("Created delete query for table {} with primary key {}");
        return query;
    }

    /**
     * returns a json blob of the object selected by id, the table to select from and the query to run
     * is chosen based on the deleteType. The object can be used to be inserted in the deleted_object
     * table later.
     *
     * @param conn       DB connection from the transaction that will do the whole delete procedure
     * @param query      query to select from the right table
     * @param id         primary key /id that is supposed to get deleted
     * @param deleteType DeleteType of the patch.
     * @return JSON representation of the object that will be deleted later
     */

    private static String getJsonObject(Connection conn, String query, String id, DeleteType deleteType) {
        //Select everything in the db as the current state of the object into a json string , this is better than creating a Dto because
        // this can be done independent of class and changes to the schema
        String data = null;
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeQuery();
            PreparedStatement sql = conn.prepareStatement(" Select @sql");
            ResultSet rs = sql.executeQuery();
            if (rs.next()) {
                String finalQuery = rs.getString(1);
                PreparedStatement stmt2 = conn.prepareStatement(finalQuery);
                stmt2.setString(1, id);
                ResultSet rs2 = stmt2.executeQuery();
                if (rs2.next()) {
                    data = rs2.getString(1);
                } else {
                    log.warn("No data found in {} table with id {}", deleteType, id);
                    return null;
                }
            } else {
                throw new DsmInternalError("Error creating SQL for type " + deleteType);
            }
        } catch (SQLException ex) {
            throw new DsmInternalError("Error getting json data for type " + deleteType, ex);
        }
        return data;
    }


    /**
     * Used only in tests for now, this methods extract the data that was inserted in the deleted_object table
     *
     * @param id        original primary key of the object in its original table
     * @param tableName name of the original table of where record was before deleting
     */
    public String getDeletedDataByPKAndTable(int id, String tableName) {
        SimpleResult results = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement preparedStatement = conn.prepareStatement(SQL_GET_DELETED_OBJECT_BY_ORIGINAL_ID_TABLE)) {
                preparedStatement.setString(1, tableName);
                preparedStatement.setInt(2, id);
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString("data");
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = new DsmInternalError(String.format(
                        "Unable to select from deleted_object for table %s with original primary key %d", tableName, id), e);
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new DsmInternalError(results.resultException);
        }
        return (String) results.resultValue;
    }

}
