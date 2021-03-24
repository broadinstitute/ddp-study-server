package org.broadinstitute.dsm.db;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class DDPInstance {

    private static final Logger logger = LoggerFactory.getLogger(DDPInstance.class);

    private static final String SQL_SELECT_INSTANCE_WITH_ROLE = "SELECT ddp_instance_id, instance_name, base_url, collaborator_id_prefix, migrated_ddp, billing_reference, " +
            "es_participant_index, es_activity_definition_index, es_users_index, (SELECT count(role.name) " +
            "FROM ddp_instance realm, ddp_instance_role inRol, instance_role role WHERE realm.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id AND role.name = ? " +
            "AND realm.ddp_instance_id = main.ddp_instance_id) AS 'has_role', mr_attention_flag_d, tissue_attention_flag_d, auth0_token, notification_recipients FROM ddp_instance main " +
            "WHERE is_active = 1";
    private static final String SQL_SELECT_INSTANCE_WITH_KIT_BEHAVIOR = "SELECT main.ddp_instance_id, instance_name, base_url, collaborator_id_prefix, migrated_ddp, billing_reference, " +
            "es_participant_index, es_activity_definition_index, es_users_index, mr_attention_flag_d, tissue_attention_flag_d, auth0_token, notification_recipients, kit_behavior_change " +
            "FROM ddp_instance main, instance_settings setting WHERE main.ddp_instance_id = setting.ddp_instance_id " +
            "AND main.is_active = 1 AND setting.kit_behavior_change IS NOT NULL";
    public static final String SQL_SELECT_ALL_ACTIVE_REALMS = "SELECT ddp_instance_id, instance_name, base_url, collaborator_id_prefix, es_participant_index, es_activity_definition_index, es_users_index, " +
            "mr_attention_flag_d, tissue_attention_flag_d, auth0_token, notification_recipients, migrated_ddp, billing_reference FROM ddp_instance WHERE is_active = 1";
    private static final String SQL_SELECT_ACTIVE_REALMS_WITH_ROLE_INFORMATION_BY_PARTICIPANT_ID = "SELECT main.ddp_instance_id, main.instance_name, main.base_url, " +
            "main.collaborator_id_prefix, main.migrated_ddp, main.billing_reference, main.es_participant_index, main.es_activity_definition_index, es_users_index, (SELECT count(role.name) " +
            "FROM ddp_instance realm, ddp_instance_role inRol, instance_role role WHERE realm.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id " +
            "AND role.name = ? AND realm.ddp_instance_id = main.ddp_instance_id) as 'has_role', mr_attention_flag_d, tissue_attention_flag_d, auth0_token, notification_recipients " +
            "FROM ddp_instance main, ddp_participant part WHERE main.ddp_instance_id = part.ddp_instance_id AND main.is_active = 1 and part.participant_id = ?";
    private static final String SQL_SELECT_ACTIVE_REALMS_WITH_ROLE_INFORMATION_BY_DDP_PARTICIPANT_ID_REALM = "SELECT main.ddp_instance_id, main.instance_name, main.base_url, " +
            "main.collaborator_id_prefix, main.migrated_ddp, main.billing_reference, main.es_participant_index, main.es_activity_definition_index, es_users_index, " +
            "(SELECT count(role.name) FROM ddp_instance realm, ddp_instance_role inRol, instance_role role WHERE realm.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id "+
            "AND role.name = ? AND realm.ddp_instance_id = main.ddp_instance_id) as 'has_role', mr_attention_flag_d, tissue_attention_flag_d, auth0_token, notification_recipients "+
            "FROM ddp_instance main, ddp_participant part WHERE main.ddp_instance_id = part.ddp_instance_id AND main.is_active = 1 AND part.ddp_participant_id = ? AND main.instance_name = ?";
    public static final String SQL_SELECT_GROUP = "SELECT ddp_group_id from ddp_instance_group g LEFT JOIN ddp_instance realm ON (g.ddp_instance_id = realm.ddp_instance_id) WHERE instance_name =?";
    public static final String BY_BASE_URL = " and base_url like \"%dsm/studies/%1\"";
    private static final String SQL_SELECT_STUDY_GUID_BY_INSTANCE_NAME =
            "SELECT " +
                    "study_guid " +
            "FROM " +
                    "ddp_instance " +
            "WHERE " +
                    "instance_name = ?";

    private final String ddpInstanceId;
    private final String name;
    private final String baseUrl;
    private final boolean hasRole;
    private final String collaboratorIdPrefix;
    private final int daysMrAttentionNeeded;
    private final int daysTissueAttentionNeeded;
    private final boolean hasAuth0Token;
    private final List<String> notificationRecipient;
    private final boolean migratedDDP;
    private final String billingReference;
    private final String participantIndexES;
    private final String activityDefinitionIndexES;
    private final String usersIndexES;

    private InstanceSettings instanceSettings;

    public DDPInstance(String ddpInstanceId, String name, String baseUrl, String collaboratorIdPrefix, boolean hasRole,
                       int daysMrAttentionNeeded, int daysTissueAttentionNeeded, boolean hasAuth0Token, List<String> notificationRecipient,
                       boolean migratedDDP, String billingReference, String participantIndexES, String activityDefinitionIndexES, String usersIndexES) {
        this.ddpInstanceId = ddpInstanceId;
        this.name = name;
        this.baseUrl = baseUrl;
        this.collaboratorIdPrefix = collaboratorIdPrefix;
        this.hasRole = hasRole;
        this.daysMrAttentionNeeded = daysMrAttentionNeeded;
        this.daysTissueAttentionNeeded = daysTissueAttentionNeeded;
        this.hasAuth0Token = hasAuth0Token;
        this.notificationRecipient = notificationRecipient;
        this.migratedDDP = migratedDDP;
        this.billingReference = billingReference;
        this.participantIndexES = participantIndexES;
        this.activityDefinitionIndexES = activityDefinitionIndexES;
        this.usersIndexES = usersIndexES;
    }

    public static DDPInstance getDDPInstance(@NonNull String realm) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ALL_ACTIVE_REALMS + QueryExtension.BY_INSTANCE_NAME)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceFormResultSet(rs);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting information for " + realm, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get realm information for " + realm, results.resultException);
        }
        return (DDPInstance) results.resultValue;
    }

    public static DDPInstance getDDPInstanceById(@NonNull Integer ddpInstanceId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ALL_ACTIVE_REALMS + QueryExtension.BY_INSTANCE_ID)) {
                stmt.setInt(1, ddpInstanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceFormResultSet(rs);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting information for realm with instance id" + ddpInstanceId, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get realm information for realm with instance id " + ddpInstanceId, results.resultException);
        }
        return (DDPInstance) results.resultValue;
    }

    public static String getStudyGuidByInstanceName(@NonNull String instanceName) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_STUDY_GUID_BY_INSTANCE_NAME)) {
                stmt.setString(1, instanceName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(1);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting study guid by instance name " + instanceName, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get study guid by instance name " + instanceName, results.resultException);
        }
        return (String) results.resultValue;
    }



    public static DDPInstance getDDPInstanceWithRole(@NonNull String realm, @NonNull String role) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_INSTANCE_WITH_ROLE + QueryExtension.BY_INSTANCE_NAME)) {
                stmt.setString(1, role);
                stmt.setString(2, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceWithRoleFormResultSet(rs);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting list of ddps ", e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of ddps ", results.resultException);
        }
        return (DDPInstance) results.resultValue;
    }

    public static String getDDPGroupId(@NonNull String realm) {
        SimpleResult resultsGroup = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_GROUP)) {
                stmt.setString(1, realm);
                try {
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.DDP_GROUP_ID);
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
        if (resultsGroup.resultException != null) {
            throw new RuntimeException(resultsGroup.resultException);
        }
        return (String) resultsGroup.resultValue;
    }

    public static List<DDPInstance> getDDPInstanceListWithRole(@NonNull String role) {
        List<DDPInstance> ddpInstances = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement bspStatement = conn.prepareStatement(SQL_SELECT_INSTANCE_WITH_ROLE)) {
                bspStatement.setString(1, role);
                try (ResultSet rs = bspStatement.executeQuery()) {
                    while (rs.next()) {
                        DDPInstance ddpInstance = getDDPInstanceWithRoleFormResultSet(rs);
                        ddpInstances.add(ddpInstance);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking ddpInstances ", results.resultException);
        }
        return ddpInstances;
    }

    public static DDPInstance getDDPInstanceWithRoleByDDPParticipantAndRealm(@NonNull String realm, @NonNull String ddpParticipantId, @NonNull String role) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ACTIVE_REALMS_WITH_ROLE_INFORMATION_BY_DDP_PARTICIPANT_ID_REALM)) {
                stmt.setString(1, role);
                stmt.setString(2, ddpParticipantId);
                stmt.setString(3, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceWithRoleFormResultSet(rs);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting ddps ", e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of participants ", results.resultException);
        }
        return (DDPInstance) results.resultValue;
    }

    public static DDPInstance getDDPInstanceWithRoleByParticipant(@NonNull String participantId, @NonNull String role) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ACTIVE_REALMS_WITH_ROLE_INFORMATION_BY_PARTICIPANT_ID)) {
                stmt.setString(1, role);
                stmt.setString(2, participantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceWithRoleFormResultSet(rs);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting ddps ", e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of participants ", results.resultException);
        }
        return (DDPInstance) results.resultValue;
    }

    public static boolean getRole(@NonNull String realm, @NonNull String role) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_INSTANCE_WITH_ROLE + QueryExtension.BY_INSTANCE_NAME)) {
                stmt.setString(1, role);
                stmt.setString(2, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getBoolean(DBConstants.HAS_ROLE);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting role of realm " + realm, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get role of realm " + realm, results.resultException);
        }
        return (boolean) results.resultValue;
    }

    private static DDPInstance getDDPInstanceWithRoleFormResultSet(@NonNull ResultSet rs) throws SQLException {
        String notificationRecipient = rs.getString(DBConstants.NOTIFICATION_RECIPIENT);
        List<String> recipients = null;
        if (StringUtils.isNotBlank(notificationRecipient)) {
            notificationRecipient = notificationRecipient.replaceAll("\\s", "");
            recipients = Arrays.asList(notificationRecipient.split(","));
        }
        return new DDPInstance(rs.getString(DBConstants.DDP_INSTANCE_ID),
                rs.getString(DBConstants.INSTANCE_NAME),
                rs.getString(DBConstants.BASE_URL), rs.getString(DBConstants.COLLABORATOR_ID_PREFIX),
                rs.getBoolean(DBConstants.HAS_ROLE), rs.getInt(DBConstants.DAYS_MR_ATTENTION_NEEDED),
                rs.getInt(DBConstants.DAYS_TISSUE_ATTENTION_NEEDED),
                rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN),
                recipients, rs.getBoolean(DBConstants.MIGRATED_DDP),
                rs.getString(DBConstants.BILLING_REFERENCE),
                rs.getString(DBConstants.ES_PARTICIPANT_INDEX),
                rs.getString(DBConstants.ES_ACTIVITY_DEFINITION_INDEX),
                rs.getString(DBConstants.ES_USERS_INDEX));
    }

    private static DDPInstance getDDPInstanceFormResultSet(@NonNull ResultSet rs) throws SQLException {
        String notificationRecipient = rs.getString(DBConstants.NOTIFICATION_RECIPIENT);
        List<String> recipients = null;
        if (StringUtils.isNotBlank(notificationRecipient)) {
            notificationRecipient = notificationRecipient.replaceAll("\\s", "");
            recipients = Arrays.asList(notificationRecipient.split(","));
        }
        return new DDPInstance(rs.getString(DBConstants.DDP_INSTANCE_ID),
                rs.getString(DBConstants.INSTANCE_NAME),
                rs.getString(DBConstants.BASE_URL), rs.getString(DBConstants.COLLABORATOR_ID_PREFIX),
                false, rs.getInt(DBConstants.DAYS_MR_ATTENTION_NEEDED),
                rs.getInt(DBConstants.DAYS_TISSUE_ATTENTION_NEEDED),
                rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN),
                recipients, rs.getBoolean(DBConstants.MIGRATED_DDP),
                rs.getString(DBConstants.BILLING_REFERENCE),
                rs.getString(DBConstants.ES_PARTICIPANT_INDEX),
                rs.getString(DBConstants.ES_ACTIVITY_DEFINITION_INDEX),
                rs.getString(DBConstants.ES_USERS_INDEX));
    }

    //assumption: base url of pepper studies will always end like: dsm/studies/<STUDYNAME>
    public static DDPInstance getDDPInstanceByRequestParameter(@NonNull String realm) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ALL_ACTIVE_REALMS + BY_BASE_URL.replace("%1", realm))) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceFormResultSet(rs);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting information for " + realm, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get realm information for " + realm, results.resultException);
        }
        return (DDPInstance) results.resultValue;
    }

    public static List<DDPInstance> getDDPInstanceListWithKitBehavior() {
        List<DDPInstance> ddpInstances = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement bspStatement = conn.prepareStatement(SQL_SELECT_INSTANCE_WITH_KIT_BEHAVIOR)) {
                try (ResultSet rs = bspStatement.executeQuery()) {
                    while (rs.next()) {
                        DDPInstance ddpInstance = getDDPInstanceFormResultSet(rs);
                        List<Value> kitBehavior = Arrays.asList(new Gson().fromJson(rs.getString(DBConstants.KIT_BEHAVIOR_CHANGE), Value[].class));
                        InstanceSettings instanceSettings = new InstanceSettings(null, kitBehavior, null, null, false);
                        ddpInstance.setInstanceSettings(instanceSettings);
                        ddpInstances.add(ddpInstance);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting ddpInstances ", results.resultException);
        }
        return ddpInstances;
    }
}
