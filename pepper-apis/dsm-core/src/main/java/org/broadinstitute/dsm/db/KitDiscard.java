package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class KitDiscard {

    private static final Logger logger = LoggerFactory.getLogger(KitDiscard.class);

    public static final String HOLD = "hold";
    private static final String DISCARD = "discard";
    private static final String AWAITING_APPROVAL = "toReview";
    private static final String APPROVED = "toDestroy";
    private static final String DESTROYED = "done";

    public static final String BSP_FILE = "pathBSPScreenshot";
    public static final String IMAGE_FILE = "pathSampleImage";
    public static final String KIT_DISCARD_ID = "kitDiscardId";

    private String realm;
    private String ddpParticipantId;
    private String collaboratorParticipantId;
    private String kitRequestId;
    private String kitDiscardId;
    private String user;
    private long exitDate;
    private String kitType;
    private long scanDate;
    private long receivedDate;
    private String kitLabel;
    private String action;
    private String pathBSPScreenshot;
    private String pathSampleImage;
    private String note;
    private int changedById;
    private String changedBy;
    private String userConfirm;
    private String discardUser;
    private String discardDate;
    private String discard;
    private File file;
    private String path;
    private String token;

    public KitDiscard(String kitDiscardId, String kitType, String action) {
        this.kitDiscardId = kitDiscardId;
        this.kitType = kitType;
        this.action = action;
    }

    public KitDiscard(String realm, String ddpParticipantId, String collaboratorParticipantId, String kitRequestId, String kitDiscardId, String user,
                      long exitDate, String kitType, long scanDate, long receivedDate, String kitLabel, String action,
                      String pathBSPScreenshot, String pathSampleImage, String note, int changedById, String changedBy, String userConfirm,
                      String discardUser, String discardDate) {
        this.realm = realm;
        this.ddpParticipantId = ddpParticipantId;
        this.collaboratorParticipantId = collaboratorParticipantId;
        this.kitRequestId = kitRequestId;
        this.kitDiscardId = kitDiscardId;
        this.user = user;
        this.exitDate = exitDate;
        this.kitType = kitType;
        this.scanDate = scanDate;
        this.receivedDate = receivedDate;
        this.kitLabel = kitLabel;
        this.action = action;
        this.pathBSPScreenshot = pathBSPScreenshot;
        this.pathSampleImage = pathSampleImage;
        this.note = note;
        this.changedById = changedById;
        this.changedBy = changedBy;
        this.userConfirm = userConfirm;
        this.discardUser = discardUser;
        this.discardDate = discardDate;
    }

    public static List<KitDiscard> getExitedKits(@NonNull String realm) {
        Map<Integer, String> users = UserUtil.getUserMap();
        List<KitDiscard> exitedKits = new ArrayList();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_KIT_OF_EXITED_PARTICIPANTS) + QueryExtension.BY_INSTANCE_NAME)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int userIdChanged = rs.getInt(DBConstants.CHANGED_BY);
                        String userChanged = users.get(userIdChanged);
                        int userIdConfirm = rs.getInt(DBConstants.USER_CONFIRM);
                        String userConfirm = users.get(userIdConfirm);
                        exitedKits.add(new KitDiscard(rs.getString(DBConstants.INSTANCE_NAME),
                                rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID),
                                rs.getString(DBConstants.DSM_KIT_REQUEST_ID),
                                rs.getString(DBConstants.KIT_DISCARD_ID),
                                rs.getString(DBConstants.NAME),
                                rs.getLong(DBConstants.EXIT_DATE),
                                rs.getString(DBConstants.KIT_TYPE_NAME),
                                rs.getLong(DBConstants.DSM_SCAN_DATE),
                                rs.getLong(DBConstants.DSM_RECEIVE_DATE),
                                rs.getString(DBConstants.KIT_LABEL),
                                rs.getString(DBConstants.ACTION),
                                rs.getString(DBConstants.PATH_SCREENSHOT),
                                rs.getString(DBConstants.PATH_IMAGE),
                                rs.getString(DBConstants.NOTE),
                                userIdChanged,
                                userChanged,
                                userConfirm,
                                rs.getString(DBConstants.DISCARD_BY),
                                rs.getString(DBConstants.DISCARD_DATE)
                                ));
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get information of exited kits for " + realm, results.resultException);
        }
        return exitedKits;
    }

    public static boolean setConfirmed(@NonNull String kitDiscardId, @NonNull Integer userId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.SET_USER_CONFIRMED))) {
                stmt.setInt(1, userId);
                stmt.setString(2, APPROVED);
                stmt.setString(3, kitDiscardId);
                stmt.setInt(4, userId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    dbVals.resultValue = true;
                }
            }
            catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error confirming kit discarded w/ dsm_kit_id " + kitDiscardId, results.resultException);
        }
        return (boolean) results.resultValue;
    }

    public void setAction(@NonNull String kitDiscardId, @NonNull String action) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.UPDATE_KIT_DISCARD_ACTION))) {
                stmt.setString(1, action);
                stmt.setString(2, kitDiscardId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating discard kit " + kitDiscardId + " action. It was updating " + result + " rows");
                }
            }
            catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error updating discard kit w/ dsm_kit_id " + kitDiscardId, results.resultException);
        }
        else {
            logger.info("Updated discard kit w/ dsm_kit_id " + kitDiscardId, results.resultException);
        }
    }

    public void setKitDiscarded(@NonNull String kitDiscardId, @NonNull String userId, @NonNull String discardDate) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.UPDATE_KIT_DISCARDED))) {
                stmt.setString(1, userId);
                stmt.setString(2, discardDate);
                stmt.setString(3, DESTROYED);
                stmt.setString(4, kitDiscardId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating discard kit " + kitDiscardId + " discarded. It was updating " + result + " rows");
                }
            }
            catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error updating discard kit w/ dsm_kit_id " + kitDiscardId, results.resultException);
        }
        else {
            logger.info("Updated discard kit w/ dsm_kit_id " + kitDiscardId, results.resultException);
        }
    }

    public static void updateInfo(@NonNull String kitDiscardId, @NonNull String userId, String note, String pathName, String path) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            String query = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.UPDATE_KIT_DISCARD);
            String value = null;
            if (note != null) {
                query = query.replace("%file", DBConstants.NOTE);
                value = note;
            }
            else if (BSP_FILE.equals(pathName)) {
                query = query.replace("%file", DBConstants.PATH_SCREENSHOT);
                value = path;
            }
            else if (IMAGE_FILE.equals(pathName)) {
                query = query.replace("%file", DBConstants.PATH_IMAGE);
                value = path;
            }
            else {
                throw new RuntimeException("Missing field");
            }
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setObject(1, value);
                stmt.setString(2, userId);
                stmt.setString(3, kitDiscardId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating discard kit " + kitDiscardId + " discarded. It was updating " + result + " rows");
                }
            } catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error updating discard kit w/ dsm_kit_id " + kitDiscardId, results.resultException);
        }
        else {
            logger.info("Updated discard kit w/ dsm_kit_id " + kitDiscardId, results.resultException);
        }
    }

    public static String addKitToDiscard(@NonNull long kitRequestId, @NonNull String action) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.INSERT_KIT_DISCARD), Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, action);
                stmt.setLong(2, kitRequestId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            String discardId = rs.getString(1);
                            logger.info("Added kit to discard table w/ id " + kitRequestId);
                            dbVals.resultValue = discardId;
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Error getting id of new discard sample ", e);
                    }
                }
                else {
                    throw new RuntimeException("Error inserting discard kit w/ dsm_kit_id " + kitRequestId + " it was updating " + result + " rows");
                }
            }
            catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error inserting discard kit w/ dsm_kit_id " + kitRequestId, results.resultException);
        }
        return (String) results.resultValue;
    }

    public static KitDiscard getKitDiscard(@NonNull String kitDiscardId) {
        Map<Integer, String> users = UserUtil.getUserMap();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_KIT_OF_EXITED_PARTICIPANTS) + QueryExtension.DISCARD_KIT_BY_DISCARD_ID,
                    ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_READ_ONLY)) {
                stmt.setString(1, kitDiscardId);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    rs.beforeFirst();
                    if (count == 1 && rs.next()) {
                        int userIdChanged = rs.getInt(DBConstants.CHANGED_BY);
                        String userChanged = users.get(userIdChanged);
                        int userIdConfirm = rs.getInt(DBConstants.USER_CONFIRM);
                        String userConfirm = users.get(userIdConfirm);
                        dbVals.resultValue = new KitDiscard(rs.getString(DBConstants.INSTANCE_NAME),
                                rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.COLLABORATOR_PARTICIPANT_ID),
                                rs.getString(DBConstants.DSM_KIT_REQUEST_ID),
                                rs.getString(DBConstants.KIT_DISCARD_ID),
                                rs.getString(DBConstants.NAME),
                                rs.getLong(DBConstants.EXIT_DATE),
                                rs.getString(DBConstants.KIT_TYPE_NAME),
                                rs.getLong(DBConstants.DSM_SCAN_DATE),
                                rs.getLong(DBConstants.DSM_RECEIVE_DATE),
                                rs.getString(DBConstants.KIT_LABEL),
                                rs.getString(DBConstants.ACTION),
                                rs.getString(DBConstants.PATH_SCREENSHOT),
                                rs.getString(DBConstants.PATH_IMAGE),
                                rs.getString(DBConstants.NOTE),
                                userIdChanged,
                                userChanged,
                                userConfirm,
                                rs.getString(DBConstants.DISCARD_BY),
                                rs.getString(DBConstants.DISCARD_DATE)
                        );
                    }
                    else {
                        throw new RuntimeException("Error getting discard kit back. (Got " + count + " row back)");
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't get information of discard kit w/ id " + kitDiscardId, results.resultException);
        }
        return (KitDiscard) results.resultValue;

    }
}
