package org.broadinstitute.dsm.model;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.exception.DuplicateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class Patch {

    private static final Logger logger = LoggerFactory.getLogger(Patch.class);

    private static final String SQL_UPDATE_VALUES = "UPDATE $table SET $colName = ?, last_changed = ?, changed_by = ? WHERE $pk = ?";
    public static final String SQL_CHECK_UNIQUE = "SELECT * FROM $table WHERE ($colName = ? ) and deleted <=> 0 ";
    public static final String TABLE = "$table";
    public static final String PK = "$pk";
    public static final String COL_NAME = "$colName";

    public static final String PARTICIPANT_ID = "participantId";
    public static final String ONC_HISTORY_ID = "oncHistoryDetailId";

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

    //regular patch
    public Patch(String id, String parent, String parentId, String user, NameValue nameValue, List<NameValue> nameValues) {
        this.id = id;
        this.parent = parent;
        this.parentId = parentId;
        this.user = user;
        this.nameValue = nameValue;
        this.nameValues = nameValues;
        this.isUnique = false;
    }

    //unique field patch
    public Patch(String id, String parent, String parentId, String user, NameValue nameValue, List<NameValue> nameValues, Boolean isUnique) {
        this.id = id;
        this.parent = parent;
        this.parentId = parentId;
        this.user = user;
        this.nameValue = nameValue;
        this.nameValues = nameValues;
        this.isUnique = isUnique;
    }

    //abstraction patch
    public Patch(String id, String parent, String parentId, String fieldId, String fieldName, String user, NameValue nameValue, List<NameValue> nameValues) {
        this.id = id;
        this.parent = parent;
        this.parentId = parentId;
        this.fieldId = fieldId;
        this.fieldName = fieldName;
        this.user = user;
        this.nameValue = nameValue;
        this.nameValues = nameValues;
    }

    /**
     * Change value of given table
     * for the given id
     *
     * @return boolean value if changes were successful
     * @throws Exception
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
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_VALUES
                    .replace(TABLE, dbElement.getTableName()).replace(COL_NAME, dbElement.getColumnName())
                    .replace(PK, dbElement.getPrimaryKey()))) {
                if ((nameValue.getName().equals("countReceived") || nameValue.getName().equals("destructionPolicy"))
                        && StringUtils.isBlank(String.valueOf(nameValue.getValue()))) {
                    stmt.setNull(1, Types.INTEGER);
                }
                else if (StringUtils.isBlank(String.valueOf(nameValue.getValue()))) {
                    stmt.setNull(1, Types.VARCHAR);
                }
                else {
                    if (StringUtils.isBlank(String.valueOf(nameValue.getValue()))) {
                        stmt.setNull(1, Types.VARCHAR);
                    }
                    else {
                        stmt.setObject(1, nameValue.getValue());
                    }
                }

                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, user);
                stmt.setString(4, id);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updated " + dbElement.getTableName() + " record w/ id " + id);
                }
                else {
                    throw new RuntimeException("Error updating " + dbElement.getTableName() + " record of w/ id " + id + " it was updating " + result + " rows");
                }
            }
            catch (SQLIntegrityConstraintViolationException ex) {
                throw new DuplicateException(dbElement.getColumnName());
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error updating " + dbElement.getTableName() + " record w/ id " + id, results.resultException);
        }
        return true;
    }

    public static Boolean patchUniqueField(@NonNull String id, @NonNull String user, @NonNull NameValue nameValue, @NonNull DBElement dbElement){
        if(!isValueUnique(dbElement)){
            return false;
        }
        return patch(id, user, nameValue, dbElement);
    }

    public static Boolean isValueUnique(@NonNull DBElement dbElement){
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_CHECK_UNIQUE.
                    replace(TABLE, dbElement.getTableName()).replace(COL_NAME, dbElement.getColumnName()))) {
                try {
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        dbVals.resultValue = false;
                    }
                    else{
                        dbVals.resultValue = true;
                    }
                }
                catch (Exception e) {
                    dbVals.resultException = e;
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        return (Boolean) results.resultValue;
    }

}
