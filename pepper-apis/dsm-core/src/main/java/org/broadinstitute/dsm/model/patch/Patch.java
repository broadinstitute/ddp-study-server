package org.broadinstitute.dsm.model.patch;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class Patch {

    public static final String SQL_CHECK_UNIQUE = "SELECT * FROM $table WHERE ($colName = ? ) and deleted <=> 0 ";
    public static final String TABLE = "$table";
    public static final String PK = "$pk";
    public static final String COL_NAME = "$colName";
    public static final String PARTICIPANT_ID = "participantId";
    public static final String PARTICIPANT_DATA_ID = "participantDataId";
    public static final String DDP_PARTICIPANT_ID = "ddpParticipantId";
    private static final Logger logger = LoggerFactory.getLogger(Patch.class);
    private static final String SQL_UPDATE_VALUES = "UPDATE $table SET $colName = ?, last_changed = ?, changed_by = ? WHERE $pk = ?";
    private static final String SQL_GET_JSON_OBJECT = "select concat('select json_object(', "
            + "    group_concat(concat(quote(column_name), ', ', column_name)), "
            + "    ') from ? where ? = ?;') into @sql "
            + "  from information_schema.columns "
            + "  where table_name = ?;";

    private static final String SQL_DELETE_VALUES = "DELETE FROM ? WHERE ? = ?";
    private static final String SQL_INSERT_DELETED_VALUES = "INSERT INTO deleted_object SET original_table = ?, original_primary_key = ? ,"
            + "data =?, deleted_by_email = ?,  deleted_by_user_id = ?, deleted_date = ? ";
    private String id;
    private String parent; //for new added rows at oncHistoryDetails/tissue
    private String parentId; //for new added rows at oncHistoryDetails/tissue
    private String fieldId; //for new added rows at abstraction
    private String fieldName; //for questions at abstraction
    private String user;
    private NameValue nameValue;
    private String tableAlias;
    private List<NameValue> nameValues;
    private Boolean isUnique;
    private String realm;
    private List<Value> actions;
    private String ddpParticipantId;
    private boolean deleteAnyway;

    public Patch() {

    }

    //regular patch
    public Patch(String id, String parent, String parentId, String user, NameValue nameValue, List<NameValue> nameValues,
                 String ddpParticipantId) {
        this.id = id;
        this.parent = parent;
        this.parentId = parentId;
        this.user = user;
        this.nameValue = nameValue;
        this.nameValues = nameValues;
        this.isUnique = false;
        this.ddpParticipantId = ddpParticipantId;
    }

    //dynamic form patch
    public Patch(String id, String parent, String parentId, String user, List<NameValue> nameValues, String realm, List<Value> actions,
                 String ddpParticipantId) {
        this.id = id;
        this.parent = parent;
        this.parentId = parentId;
        this.user = user;
        this.nameValues = nameValues;
        this.realm = realm;
        this.actions = actions;
        this.ddpParticipantId = ddpParticipantId;
    }

    //unique field patch
    public Patch(String id, String parent, String parentId, String user, NameValue nameValue, List<NameValue> nameValues, Boolean isUnique,
                 String ddpParticipantId) {
        this.id = id;
        this.parent = parent;
        this.parentId = parentId;
        this.user = user;
        this.nameValue = nameValue;
        this.nameValues = nameValues;
        this.isUnique = isUnique;
        this.ddpParticipantId = ddpParticipantId;
    }

    //abstraction patch
    public Patch(String id, String parent, String parentId, String fieldId, String fieldName, String user, NameValue nameValue,
                 List<NameValue> nameValues, String ddpParticipantId) {
        this.id = id;
        this.parent = parent;
        this.parentId = parentId;
        this.fieldId = fieldId;
        this.fieldName = fieldName;
        this.user = user;
        this.nameValue = nameValue;
        this.nameValues = nameValues;
        this.ddpParticipantId = ddpParticipantId;
    }

    // Patch for deleting tissues and sm ids in a deleted oncHistory or tissue
    public Patch(String id, String parent, String parentId, String user, NameValue nameValue, List<NameValue> nameValues, Boolean isUnique,
                 String ddpParticipantId, String realm) {
        this.id = id;
        this.parent = parent;
        this.parentId = parentId;
        this.user = user;
        this.nameValue = nameValue;
        this.nameValues = nameValues;
        this.isUnique = isUnique;
        this.ddpParticipantId = ddpParticipantId;
        this.realm = realm;
    }

    // for testing purposes
    public static Patch fromNameValue(NameValue nameValue) {
        return new Patch(null, null, null, null, null, null, nameValue, null, null);
    }

    /**
     * Change value of given table
     * for the given id
     */
    public static boolean patch(@NonNull String id, @NonNull String user, @NonNull NameValue nameValue, @NonNull DBElement dbElement) {
        String multiSelect = null;
        if (nameValue.getValue() instanceof ArrayList) {
            Gson gson = new Gson();
            multiSelect = gson.toJson(nameValue.getValue(), ArrayList.class);
            nameValue.setValue(multiSelect);
        }
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    SQL_UPDATE_VALUES.replace(TABLE, dbElement.getTableName()).replace(COL_NAME, dbElement.getColumnName())
                            .replace(PK, dbElement.getPrimaryKey()))) {
                if ((nameValue.getName().equals("countReceived") || nameValue.getName().equals("destructionPolicy")) && StringUtils.isBlank(
                        String.valueOf(nameValue.getValue()))) {
                    stmt.setNull(1, Types.INTEGER);
                } else if (StringUtils.isBlank(String.valueOf(nameValue.getValue()))) {
                    stmt.setNull(1, Types.VARCHAR);
                } else {
                    stmt.setObject(1, nameValue.getValue());
                }

                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, user);
                stmt.setString(4, id);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updated {} value in table {}, record ID={}", nameValue.getName(), dbElement.getTableName(), id);
                } else {
                    throw new DsmInternalError(toErrorMessage(dbElement.getTableName(), dbElement.getColumnName(),
                            nameValue.getValue(), dbElement.getPrimaryKey(), id) + ": " + result + " rows updated");
                }
            } catch (SQLIntegrityConstraintViolationException ex) {
                throw new DsmInternalError(toErrorMessage(dbElement.getTableName(), dbElement.getColumnName(),
                        nameValue.getValue(), dbElement.getPrimaryKey(), id), ex);
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError(toErrorMessage(dbElement.getTableName(), dbElement.getColumnName(),
                    nameValue.getValue(), dbElement.getPrimaryKey(), id), results.resultException);
        }
        return true;
    }

    /****/

    public static boolean deletePatch(@NonNull String id, @NonNull String user, @NonNull NameValue nameValue,
                                      @NonNull DBElement dbElement, Patch patch) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            //Select everything in the db as the current state of the object into a json string , this is better than creating a Dto because
            // this can be done independent of class
            String currentData = null;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_JSON_OBJECT)) {
                stmt.setString(1, dbElement.getTableName());
                stmt.setString(2, dbElement.getTableName());
                stmt.setString(3, dbElement.getPrimaryKey());
                stmt.setString(4, id);
                ResultSet rs = stmt.executeQuery();
                String sql = rs.getString("@sql");
                PreparedStatement stmt2 = conn.prepareStatement(sql);
                ResultSet rs2 = stmt2.executeQuery();
                if(rs2.next()){
                    logger.info("*************{}************", rs2.getString(1));
                    currentData = rs2.getString(1);

                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            //insert the values of what is being deleted into the deleted_object table
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DELETED_VALUES)) {
                stmt.setString(1, dbElement.getTableName());
                stmt.setString(2, id);
                stmt.setString(3, currentData);
                stmt.setString(4, user);
                stmt.setString(5, patch.getUser());//todo pegah get userId
                stmt.setLong(6, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            //now delete from the table by primary key
            try (PreparedStatement deleteStmt = conn.prepareStatement(SQL_DELETE_VALUES)) {
                deleteStmt.setString(1, dbElement.getTableName());
                deleteStmt.setString(2, dbElement.getPrimaryKey());
                deleteStmt.setString(3, id);
                deleteStmt.executeUpdate();

            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError(toErrorMessage(dbElement.getTableName(), dbElement.getColumnName(),
                    nameValue.getValue(), dbElement.getPrimaryKey(), id), results.resultException);
        }
        return true;

    }

    private static String toErrorMessage(String table, String column, Object value, String primaryKey, String id) {
        return String.format("Error updating %s.%s with value %s for %s=%s", table, column, value, primaryKey, id);
    }

}
