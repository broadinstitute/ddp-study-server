package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.AbstractionUtil;
import org.broadinstitute.dsm.util.DBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
@TableName (
        name = DBConstants.MEDICAL_RECORD_ABSTRACTION_ACTIVITY,
        alias = DBConstants.DDP_ABSTRACTION_ALIAS,
        primaryKey = DBConstants.MEDICAL_RECORD_ABSTRACTION_ACTIVITY_ID,
        columnPrefix = "")
public class AbstractionActivity {

    private static final Logger logger = LoggerFactory.getLogger(AbstractionActivity.class);

    private static final String SQL_INSERT_MEDICAL_RECORD_ABSTRACTION_ACTIVITY = "INSERT INTO ddp_medical_record_abstraction_activities SET participant_id = (SELECT participant_id FROM ddp_participant pt, ddp_instance realm " +
            "WHERE realm.ddp_instance_id = pt.ddp_instance_id AND pt.ddp_participant_id = ? AND realm.instance_name = ?), start_date = ?, user_id = ?, activity = ?, status = ?";
    private static final String SQL_UPDATE_MEDICAL_RECORD_ABSTRACTION_ACTIVITY = "UPDATE ddp_medical_record_abstraction_activities SET user_id = ?, activity = ?, status = ?, files_used = ?, last_changed = ? WHERE medical_record_abstraction_activities_id = ?";
    private static final String SQL_SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_ACTIVITY = "SELECT medical_record_abstraction_activities_id, p.ddp_participant_id, a.participant_id, user.name, a.activity, a.status, a.start_date, files_used, a.last_changed " +
            "FROM ddp_medical_record_abstraction_activities a LEFT JOIN access_user user ON (user.user_id = a.user_id) LEFT JOIN ddp_participant pt ON (a.participant_id = pt.participant_id) " +
            "LEFT JOIN ddp_participant p ON (p.participant_id = a.participant_id) LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = pt.ddp_instance_id) WHERE realm.instance_name = ?";
    private static final String SQL_SELECT_MEDICAL_RECORD_ABSTRACTION_ACTIVITY = "SELECT medical_record_abstraction_activities_id, abst.participant_id, user.name, activity, status, start_date, files_used, abst.last_changed " +
            "FROM ddp_medical_record_abstraction_activities abst LEFT JOIN access_user user ON (user.user_id = abst.user_id) LEFT JOIN ddp_participant pt ON (abst.participant_id = pt.participant_id) " +
            "LEFT JOIN ddp_instance realm ON (realm.ddp_instance_id = pt.ddp_instance_id) WHERE realm.instance_name = ? AND pt.ddp_participant_id = ?";
    private static final String SQL_SELECT_AND_WHERE_ACTIVITY = " AND activity = ?";

    private Integer medicalRecordAbstractionActivityId;
    private String participantId;

    @ColumnName (DBConstants.ACTIVITY)
    private String activity;

    @ColumnName (DBConstants.STATUS)
    private String aStatus;

    @ColumnName (DBConstants.PROCESS)
    private String process;

    @ColumnName (DBConstants.USER_ID)
    private String user;
    private Long startDate;
    private String filesUsed;
    private Long lastChanged;

    public AbstractionActivity(Integer medicalRecordAbstractionActivityId, String participantId, String activity, String aStatus, String user, Long startDate, String filesUsed, Long lastChanged) {
        this.medicalRecordAbstractionActivityId = medicalRecordAbstractionActivityId;
        this.participantId = participantId;
        this.activity = activity;
        this.aStatus = aStatus;
        this.user = user;
        this.startDate = startDate;
        this.filesUsed = filesUsed;
        this.lastChanged = lastChanged;
    }

    public static AbstractionActivity startAbstractionActivity(@NonNull String participantId, @NonNull String realm, @NonNull Integer changedBy, @NonNull String activity, @NonNull String status) {
        Long startDate = System.currentTimeMillis();
        User user = User.getUser(changedBy);
        AbstractionActivity abstractionActivity = new AbstractionActivity(null, participantId, activity, status, user.getName(), startDate, null, null);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_MEDICAL_RECORD_ABSTRACTION_ACTIVITY, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, participantId);
                stmt.setString(2, realm);
                stmt.setLong(3, startDate);
                stmt.setInt(4, changedBy);
                stmt.setString(5, activity);
                stmt.setString(6, status);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            dbVals.resultValue = rs.getInt(1);
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Error adding new medical record abstraction activity ", e);
                    }
                }
                else {
                    throw new RuntimeException("Error adding new medical record abstraction activity for participant w/ id " + participantId + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new medical record abstraction activity for participantId w/ id " + participantId, results.resultException);
        }
        else {
            abstractionActivity.setMedicalRecordAbstractionActivityId((int) results.resultValue);
            return abstractionActivity;
        }
    }

    public static AbstractionActivity changeAbstractionActivity(@NonNull AbstractionActivity abstractionActivity, @NonNull Integer changedBy, @NonNull String status) {
        Long currentDate = System.currentTimeMillis();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_MEDICAL_RECORD_ABSTRACTION_ACTIVITY)) {
                stmt.setInt(1, changedBy);
                stmt.setString(2, abstractionActivity.getActivity());
                stmt.setString(3, status);
                stmt.setString(4, abstractionActivity.getFilesUsed());
                stmt.setLong(5, currentDate);
                stmt.setInt(6, abstractionActivity.medicalRecordAbstractionActivityId);
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating aStatus of medical record abstraction activity for participant w/ id " + abstractionActivity.getParticipantId() + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new medical record abstraction activity for participantId w/ id " + abstractionActivity.getParticipantId(), results.resultException);
        }
        abstractionActivity.setAStatus(status);
        return abstractionActivity;
    }

    public static Map<String, List<AbstractionActivity>> getAllAbstractionActivityByRealm(@NonNull String realm) {
        return getAllAbstractionActivityByRealm(realm, null);
    }

    public static Map<String, List<AbstractionActivity>> getAllAbstractionActivityByRealm(@NonNull String realm, String queryAddition) {
        logger.info("Collection abstraction activity information");
        Map<String, List<AbstractionActivity>> abstractionActivitiesMap = new HashMap<>();

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(DBUtil.getFinalQuery(SQL_SELECT_ALL_MEDICAL_RECORD_ABSTRACTION_ACTIVITY, changeQueryAddition(queryAddition)))) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        AbstractionActivity abstractionActivity = new AbstractionActivity(rs.getInt(DBConstants.MEDICAL_RECORD_ABSTRACTION_ACTIVITY_ID),
                                rs.getString(DBConstants.PARTICIPANT_ID),
                                rs.getString(DBConstants.ACTIVITY),
                                rs.getString(DBConstants.STATUS),
                                rs.getString(DBConstants.NAME),
                                rs.getLong(DBConstants.START_DATE),
                                rs.getString(DBConstants.FILES_USED),
                                rs.getLong(DBConstants.LAST_CHANGED)
                        );
                        if (abstractionActivitiesMap.containsKey(ddpParticipantId)) {
                            List<AbstractionActivity> abstractionActivities = abstractionActivitiesMap.get(ddpParticipantId);
                            abstractionActivities.add(abstractionActivity);
                        }
                        else {
                            List<AbstractionActivity> abstractionActivities = new ArrayList<>();
                            abstractionActivities.add(abstractionActivity);
                            abstractionActivitiesMap.put(ddpParticipantId, abstractionActivities);
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of abstraction activities for " + realm, results.resultException);
        }
        logger.info("Got " + abstractionActivitiesMap.size() + " participants abstraction activity in DSM DB for " + realm);
        return abstractionActivitiesMap;
    }

    public static List<AbstractionActivity> getAbstractionActivity(@NonNull String realm, @NonNull String ddpParticipantId) {
        List<AbstractionActivity> activities = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_MEDICAL_RECORD_ABSTRACTION_ACTIVITY)) {
                stmt.setString(1, realm);
                stmt.setString(2, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String participantId = rs.getString(DBConstants.PARTICIPANT_ID);
                        activities.add(new AbstractionActivity(rs.getInt(DBConstants.MEDICAL_RECORD_ABSTRACTION_ACTIVITY_ID),
                                participantId,
                                rs.getString(DBConstants.ACTIVITY),
                                rs.getString(DBConstants.STATUS),
                                rs.getString(DBConstants.NAME),
                                rs.getLong(DBConstants.START_DATE),
                                rs.getString(DBConstants.FILES_USED),
                                rs.getLong(DBConstants.LAST_CHANGED)
                        ));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of abstraction activities for " + realm, results.resultException);
        }
        return activities;
    }

    public static AbstractionActivity getAbstractionActivity(@NonNull String realm, @NonNull String ddpParticipantId, @NonNull String activity) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_MEDICAL_RECORD_ABSTRACTION_ACTIVITY + SQL_SELECT_AND_WHERE_ACTIVITY)) {
                stmt.setString(1, realm);
                stmt.setString(2, ddpParticipantId);
                stmt.setString(3, activity);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String participantId = rs.getString(DBConstants.PARTICIPANT_ID);
                        dbVals.resultValue = new AbstractionActivity(rs.getInt(DBConstants.MEDICAL_RECORD_ABSTRACTION_ACTIVITY_ID),
                                participantId,
                                rs.getString(DBConstants.ACTIVITY),
                                rs.getString(DBConstants.STATUS),
                                rs.getString(DBConstants.NAME),
                                rs.getLong(DBConstants.START_DATE),
                                rs.getString(DBConstants.FILES_USED),
                                rs.getLong(DBConstants.LAST_CHANGED));
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of abstraction activities for " + realm, results.resultException);
        }
        return (AbstractionActivity) results.resultValue;
    }

    private static String changeQueryAddition(String queryAddition) {
        if (StringUtils.isNotBlank(queryAddition)
                && queryAddition.contains(DBConstants.DDP_ABSTRACTION_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.PROCESS)) {
            queryAddition = queryAddition.replace(DBConstants.PROCESS, DBConstants.STATUS);
            if (queryAddition.contains("qc_"+AbstractionUtil.STATUS_IN_PROGRESS)) {
                queryAddition = queryAddition + " AND (activity = \'qc\')";
                queryAddition = queryAddition.replace("qc_"+AbstractionUtil.STATUS_IN_PROGRESS, AbstractionUtil.STATUS_IN_PROGRESS);
            }
            else if (queryAddition.contains(AbstractionUtil.STATUS_IN_PROGRESS)) {
                queryAddition = queryAddition + " AND (activity = \'abstraction\')";
            }
            if (queryAddition.contains(AbstractionUtil.STATUS_DONE)) {
                queryAddition = queryAddition + " AND (activity = \'qc\')";
            }
            if (queryAddition.contains("waiting_qc")) {
                queryAddition = queryAddition + " AND (activity = \'abstraction\')";
                queryAddition = queryAddition + " OR (activity = \'review\')";
                queryAddition = queryAddition.replace("waiting_qc", AbstractionUtil.STATUS_DONE);
            }
        }
        return queryAddition;
    }
}
