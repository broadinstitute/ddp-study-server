package org.broadinstitute.dsm.model.patch;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class Patch {

    public static final String TABLE = "$table";
    public static final String PK = "$pk";
    public static final String COL_NAME = "$colName";
    public static final String PARTICIPANT_ID = "participantId";
    public static final String PARTICIPANT_DATA_ID = "participantDataId";
    public static final String DDP_PARTICIPANT_ID = "ddpParticipantId";
    private static final Logger logger = LoggerFactory.getLogger(Patch.class);
    private static final String SQL_UPDATE_VALUES = "UPDATE $table SET $colName = ?, last_changed = ?, changed_by = ? WHERE $pk = ?";
    private static final String ONC_HISTORY_DELETED_FIELD = "oD.deleted";
    private static final String TISSUE_DELETED_FIELD = "t.deleted";
    private static final String SM_ID_DELETED_FIELD = "sm.deleted";
    private static final String TRUE_FLAG = "1";

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
    private boolean deleteAnyway = true;

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
                 String ddpParticipantId, String realm, String tableAlias) {
        this.id = id;
        this.parent = parent;
        this.parentId = parentId;
        this.user = user;
        this.nameValue = nameValue;
        this.nameValues = nameValues;
        this.isUnique = isUnique;
        this.ddpParticipantId = ddpParticipantId;
        this.realm = realm;
        this.tableAlias = tableAlias;
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


    private static String toErrorMessage(String table, String column, Object value, String primaryKey, String id) {
        return String.format("Error updating %s.%s with value %s for %s=%s", table, column, value, primaryKey, id);
    }

    boolean isOncHistoryDeletePatch() {
        return getNameValue().getName().equals(ONC_HISTORY_DELETED_FIELD) &&
                getTableAlias().equals(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS);
    }

     boolean isTissueDeletePatch() {
        return getNameValue().getName().equals(TISSUE_DELETED_FIELD) && getTableAlias().equals(DBConstants.DDP_TISSUE_ALIAS);
    }

    //creates a list of patches for deleting tissues that belong to an onc history
    protected static List<Patch> getPatchForTissues(Patch oncHistoryPatch) {
        List<Integer> tissueIds = TissueDao.getTissuesByOncHistoryDetailId(oncHistoryPatch.getIdAsInt());
        List<Patch> deletePatches = new ArrayList<>();
        for (Integer tissueId : tissueIds) {
            NameValue nameValue = new NameValue(TISSUE_DELETED_FIELD, TRUE_FLAG);
            Patch patch =
                    new Patch(String.valueOf(tissueId), OncHistoryDetail.ONC_HISTORY_DETAIL_ID, oncHistoryPatch.getId(),
                            oncHistoryPatch.getUser(), nameValue, null, true,
                            oncHistoryPatch.getDdpParticipantId(), oncHistoryPatch.getRealm(), DBConstants.DDP_TISSUE_ALIAS);
            patch.setTableAlias(DBConstants.DDP_TISSUE_ALIAS);
            deletePatches.add(patch);
        }
        return deletePatches;
    }

    //creates a list of patches for deleting the sm ids that belong to a tissue
    protected static List<Patch> getPatchForSmIds(Patch tissuePatch) {
        List<String> smIds = TissueSMIDDao.getSmIdPksForTissue(tissuePatch.getId());
        List<Patch> deletePatches = new ArrayList<>();
        for (String smIdPk : smIds) {
            NameValue nameValue = new NameValue(SM_ID_DELETED_FIELD, TRUE_FLAG);
            deletePatches.add(
                    new Patch(smIdPk, TissuePatch.TISSUE_ID, tissuePatch.getId(), tissuePatch.getUser(), nameValue, null, true,
                            tissuePatch.getDdpParticipantId(), tissuePatch.getRealm(), DBConstants.SM_ID_TABLE_ALIAS));
        }
        return deletePatches;
    }

    public boolean hasDDPParticipantId() {
        return StringUtils.isNotBlank(this.ddpParticipantId);
    }

    public boolean isSmIdDeletePatch() {
        return (nameValue.getName().contains(".deleted")) &&
                DBConstants.SM_ID_TABLE_ALIAS.equals(tableAlias) && TissuePatch.TISSUE_ID.equals(parent);
    }

    boolean isTissueRelatedOncHistoryId() {
        return OncHistoryDetail.ONC_HISTORY_DETAIL_ID.equals(getParent());
    }

    public int getParentIdAsInt() {
        return Integer.parseInt(parentId);
    }
    public int getIdAsInt() {
        return Integer.parseInt(this.id);
    }
}
