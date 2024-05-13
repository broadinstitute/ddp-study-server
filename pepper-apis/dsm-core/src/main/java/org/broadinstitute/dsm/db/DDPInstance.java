package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@Builder(toBuilder = true, setterPrefix = "with")
public class DDPInstance {

    public static final String SQL_SELECT_ALL_ACTIVE_REALMS =
            "SELECT ddp_instance_id, instance_name, base_url, collaborator_id_prefix, es_participant_index, es_activity_definition_index, "
                    + "es_users_index, mr_attention_flag_d, tissue_attention_flag_d, auth0_token, notification_recipients, migrated_ddp, "
                    + "billing_reference, research_project, display_name, mercury_order_creator  FROM ddp_instance WHERE is_active = 1";
    public static final String SQL_SELECT_GROUP =
            "SELECT ddp_group_id from ddp_instance_group g LEFT JOIN ddp_instance realm ON (g.ddp_instance_id = realm.ddp_instance_id) "
                    + "WHERE instance_name =?";
    public static final String BY_BASE_URL = " and base_url like \"%dsm/studies/%1\"";
    private static final Logger logger = LoggerFactory.getLogger(DDPInstance.class);
    private static final String SQL_SELECT_INSTANCE_WITH_ROLE =
            "SELECT ddp_instance_id, instance_name, base_url, collaborator_id_prefix, migrated_ddp, billing_reference, "
                    + "es_participant_index, es_activity_definition_index,  es_users_index, research_project, display_name,  "
                    + "mercury_order_creator, (SELECT count(role.name) "
                    + "FROM ddp_instance realm, ddp_instance_role inRol, instance_role role "
                    + "WHERE realm.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id "
                    + "AND role.name = ? AND realm.ddp_instance_id = main.ddp_instance_id) AS 'has_role', mr_attention_flag_d, "
                    + "tissue_attention_flag_d, auth0_token, notification_recipients FROM ddp_instance main "
                    + "WHERE is_active = 1";
    private static final String SQL_SELECT_INSTANCE_WITH_KIT_BEHAVIOR =
            "SELECT main.ddp_instance_id, instance_name, base_url, collaborator_id_prefix, migrated_ddp, billing_reference, "
                    + "es_participant_index, es_activity_definition_index, es_users_index, mr_attention_flag_d, tissue_attention_flag_d, "
                    + "auth0_token, notification_recipients, kit_behavior_change, research_project, "
                    + "display_name, main.mercury_order_creator "
                    + "FROM ddp_instance main, instance_settings setting WHERE main.ddp_instance_id = setting.ddp_instance_id "
                    + "AND main.is_active = 1 AND setting.kit_behavior_change IS NOT NULL";
    private static final String SQL_SELECT_ACTIVE_REALMS_WITH_ROLE_INFORMATION_BY_PARTICIPANT_ID =
            "SELECT main.ddp_instance_id, main.instance_name, main.base_url, "
                    + "main.collaborator_id_prefix, main.migrated_ddp, main.billing_reference, main.es_participant_index, "
                    + "main.es_activity_definition_index, es_users_index, research_project, display_name, mercury_order_creator"
                    + ", (SELECT count(role.name) "
                    + "FROM ddp_instance realm, ddp_instance_role inRol, instance_role role "
                    + "WHERE realm.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id "
                    + "AND role.name = ? AND realm.ddp_instance_id = main.ddp_instance_id) as 'has_role', mr_attention_flag_d, "
                    + "tissue_attention_flag_d, auth0_token, notification_recipients "
                    + "FROM ddp_instance main, ddp_participant part WHERE main.ddp_instance_id = part.ddp_instance_id "
                    + "AND main.is_active = 1 and part.participant_id = ?";
    private static final String SQL_SELECT_ACTIVE_REALMS_WITH_ROLE_INFORMATION_BY_DDP_PARTICIPANT_ID_REALM =
            "SELECT main.ddp_instance_id, main.instance_name, main.base_url, "
                    + "main.collaborator_id_prefix, main.migrated_ddp, main.billing_reference, main.es_participant_index, "
                    + "main.es_activity_definition_index, es_users_index, research_project, display_name, mercury_order_creator, "
                    + "(SELECT count(role.name) FROM ddp_instance realm, ddp_instance_role inRol, instance_role role "
                    + "WHERE realm.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id "
                    + "AND role.name = ? AND realm.ddp_instance_id = main.ddp_instance_id) as 'has_role', mr_attention_flag_d, "
                    + "tissue_attention_flag_d, auth0_token, notification_recipients "
                    + "FROM ddp_instance main, ddp_participant part WHERE main.ddp_instance_id = part.ddp_instance_id "
                    + "AND main.is_active = 1 AND part.ddp_participant_id = ? AND main.instance_name = ?";
    private static final String SQL_SELECT_STUDY_GUID_BY_INSTANCE_NAME =
            "SELECT  study_guid  FROM  ddp_instance  WHERE  instance_name = ?";

    private final String ddpInstanceId;
    private final String name;
    private final String baseUrl;
    private final String collaboratorIdPrefix;
    private final boolean hasRole;
    private final int daysMrAttentionNeeded;
    private final int daysTissueAttentionNeeded;
    private final boolean hasAuth0Token;
    private final List<String> notificationRecipient;
    private final boolean migratedDDP;
    private final String billingReference;
    private final String participantIndexES;
    private final String activityDefinitionIndexES;
    private final String usersIndexES;
    private final String researchProject;
    private final String mercuryOrderCreator;
    private final String displayName;

    private InstanceSettings instanceSettings;

    public DDPInstance(String ddpInstanceId, String name, String baseUrl, String collaboratorIdPrefix, boolean hasRole,
                       int daysMrAttentionNeeded, int daysTissueAttentionNeeded, boolean hasAuth0Token, List<String> notificationRecipient,
                       boolean migratedDDP, String billingReference, String participantIndexES, String activityDefinitionIndexES,
                       String usersIndexES, String researchProject, String displayName, String mercuryOrderCreator,
                       InstanceSettings instanceSettings) {
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
        this.researchProject = researchProject;
        this.mercuryOrderCreator = mercuryOrderCreator;
        this.displayName = displayName;
        this.instanceSettings = instanceSettings;
    }


    @VisibleForTesting
    public DDPInstance(int ddpInstanceId, String name) {
        this.ddpInstanceId = Integer.toString(ddpInstanceId);
        this.name = name;
        this.baseUrl = null;
        this.collaboratorIdPrefix = null;
        this.hasRole = false;
        this.daysMrAttentionNeeded = 0;
        this.daysTissueAttentionNeeded = 0;
        this.hasAuth0Token = false;
        this.notificationRecipient = null;
        this.migratedDDP = false;
        this.billingReference = null;
        this.participantIndexES = null;
        this.activityDefinitionIndexES = null;
        this.usersIndexES = null;
        this.researchProject = null;
        this.mercuryOrderCreator = null;
        this.displayName = null;
    }

    /**
     * Get instance by instance_name
     *
     * @param realm instance_name
     */
    public static DDPInstance getDDPInstance(@NonNull String realm) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ALL_ACTIVE_REALMS + QueryExtension.BY_INSTANCE_NAME)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceFormResultSet(rs);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error getting information for " + realm, e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get realm information for " + realm, results.resultException);
        }
        return (DDPInstance) results.resultValue;
    }

    public static DDPInstance getDDPInstanceByGuid(@NonNull String studyGuid) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ALL_ACTIVE_REALMS + QueryExtension.BY_STUDY_GUID)) {
                stmt.setString(1, studyGuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceFormResultSet(rs);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Could not get ddp_instance for study guid: " + studyGuid, results.resultException);
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
                } catch (SQLException e) {
                    throw new RuntimeException("Error getting information for realm with instance id" + ddpInstanceId, e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get realm information for realm with instance id " + ddpInstanceId,
                    results.resultException);
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
                } catch (SQLException e) {
                    throw new RuntimeException("Error getting study guid by instance name " + instanceName, e);
                }
            } catch (SQLException ex) {
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
            return getDDPInstanceWithRole(realm, role, conn);
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Couldn't get study %s with role %s".formatted(realm, role), results.resultException);
        }
        return (DDPInstance) results.resultValue;
    }

    // This method is used to get DDPInstance with role in a transaction, only called by getDDPInstanceWithRole in this class
    // and by Covid19OrderRegistrar
    public static SimpleResult getDDPInstanceWithRole(@NonNull String realm, @NonNull String role, Connection conn) {
        SimpleResult dbVals = new SimpleResult();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_INSTANCE_WITH_ROLE + QueryExtension.BY_INSTANCE_NAME)) {
            stmt.setString(1, role);
            stmt.setString(2, realm);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    dbVals.resultValue = getDDPInstanceWithRoleFormResultSet(rs);
                }
            } catch (SQLException e) {
                dbVals.resultException = new DsmInternalError("Error getting study %s with role %s ".formatted(realm, role), e);
            }
        } catch (SQLException ex) {
            dbVals.resultException = new DsmInternalError("Couldn't get study %s with role %s".formatted(realm, role), ex);
        }
        return dbVals;
    }

    public static DDPInstance getDDPInstanceWithRoleByStudyGuid(@NonNull String studyGuid, @NonNull String role) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_INSTANCE_WITH_ROLE + QueryExtension.BY_STUDY_GUID)) {
                stmt.setString(1, role);
                stmt.setString(2, studyGuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceWithRoleFormResultSet(rs);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error getting list of studies ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of studies ", results.resultException);
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
                } catch (Exception e) {
                    dbVals.resultException = e;
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (resultsGroup.resultException != null) {
            throw new RuntimeException(resultsGroup.resultException);
        }
        return (String) resultsGroup.resultValue;
    }

    /**
     * Get all DDP instances that have a role
     * Note: The list of instances is not filtered by the role, it just provides a field 'isHasRole' that indicates
     * if the instance has the role. TODO: Filter the list by the role, since callers expect that.
     */
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
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error looking ddpInstances ", results.resultException);
        }
        return ddpInstances;
    }

    public static DDPInstance getDDPInstanceWithRoleByDDPParticipantAndRealm(@NonNull String realm, @NonNull String ddpParticipantId,
                                                                             @NonNull String role) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    SQL_SELECT_ACTIVE_REALMS_WITH_ROLE_INFORMATION_BY_DDP_PARTICIPANT_ID_REALM)) {
                stmt.setString(1, role);
                stmt.setString(2, ddpParticipantId);
                stmt.setString(3, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDDPInstanceWithRoleFormResultSet(rs);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error getting ddps ", e);
                }
            } catch (SQLException ex) {
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
                } catch (SQLException e) {
                    throw new RuntimeException("Error getting ddps ", e);
                }
            } catch (SQLException ex) {
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
                } catch (SQLException e) {
                    throw new RuntimeException("Error getting role of realm " + realm, e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get role of realm " + realm, results.resultException);
        }
        return (boolean) results.resultValue;
    }

    public static DDPInstance getDDPInstanceWithRoleFormResultSet(@NonNull ResultSet rs) throws SQLException {
        String notificationRecipient = rs.getString(DBConstants.NOTIFICATION_RECIPIENT);
        List<String> recipients = null;
        if (StringUtils.isNotBlank(notificationRecipient)) {
            notificationRecipient = notificationRecipient.replaceAll("\\s", "");
            recipients = Arrays.asList(notificationRecipient.split(","));
        }
        return new DDPInstance(rs.getString(DBConstants.DDP_INSTANCE_ID), rs.getString(DBConstants.INSTANCE_NAME),
                rs.getString(DBConstants.BASE_URL), rs.getString(DBConstants.COLLABORATOR_ID_PREFIX), rs.getBoolean(DBConstants.HAS_ROLE),
                rs.getInt(DBConstants.DAYS_MR_ATTENTION_NEEDED), rs.getInt(DBConstants.DAYS_TISSUE_ATTENTION_NEEDED),
                rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN), recipients, rs.getBoolean(DBConstants.MIGRATED_DDP),
                rs.getString(DBConstants.BILLING_REFERENCE), rs.getString(DBConstants.ES_PARTICIPANT_INDEX),
                rs.getString(DBConstants.ES_ACTIVITY_DEFINITION_INDEX), rs.getString(DBConstants.ES_USERS_INDEX),
                rs.getString(DBConstants.RESEARCH_PROJECT), rs.getString(DBConstants.DISPLAY_NAME),
                rs.getString(DBConstants.MERCURY_ORDER_CREATOR), null);
    }

    private static DDPInstance getDDPInstanceFormResultSet(@NonNull ResultSet rs) throws SQLException {
        String notificationRecipient = rs.getString(DBConstants.NOTIFICATION_RECIPIENT);
        List<String> recipients = null;
        if (StringUtils.isNotBlank(notificationRecipient)) {
            notificationRecipient = notificationRecipient.replaceAll("\\s", "");
            recipients = Arrays.asList(notificationRecipient.split(","));
        }
        return new DDPInstance(rs.getString(DBConstants.DDP_INSTANCE_ID), rs.getString(DBConstants.INSTANCE_NAME),
                rs.getString(DBConstants.BASE_URL), rs.getString(DBConstants.COLLABORATOR_ID_PREFIX), false,
                rs.getInt(DBConstants.DAYS_MR_ATTENTION_NEEDED), rs.getInt(DBConstants.DAYS_TISSUE_ATTENTION_NEEDED),
                rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN), recipients, rs.getBoolean(DBConstants.MIGRATED_DDP),
                rs.getString(DBConstants.BILLING_REFERENCE), rs.getString(DBConstants.ES_PARTICIPANT_INDEX),
                rs.getString(DBConstants.ES_ACTIVITY_DEFINITION_INDEX), rs.getString(DBConstants.ES_USERS_INDEX),
                rs.getString(DBConstants.RESEARCH_PROJECT), rs.getString(DBConstants.DISPLAY_NAME),
                rs.getString(DBConstants.MERCURY_ORDER_CREATOR), null);
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
                } catch (SQLException e) {
                    throw new RuntimeException("Error getting information for " + realm, e);
                }
            } catch (SQLException ex) {
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
                        List<Value> kitBehavior =
                                Arrays.asList(new Gson().fromJson(rs.getString(DBConstants.KIT_BEHAVIOR_CHANGE), Value[].class));
                        InstanceSettings instanceSettings = new InstanceSettings(null, kitBehavior, null, null, null, null, false, false);
                        ddpInstance.setInstanceSettings(instanceSettings);
                        ddpInstances.add(ddpInstance);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting ddpInstances ", results.resultException);
        }
        return ddpInstances;
    }

    public static void setDdpInstanceActive(String instanceName, boolean isActive) {
        TransactionWrapper.useTxn(TransactionWrapper.DB.DSM, handle -> {
            handle.execute("UPDATE ddp_instance SET is_active=?  WHERE instance_name=?", isActive, instanceName);
        });
    }

    public int getDdpInstanceIdAsInt() {
        return Integer.parseInt(ddpInstanceId);
    }

    public boolean isESUpdatePossible() {
        return StringUtils.isNotBlank(this.participantIndexES);
    }

    public static DDPInstance from(DDPInstanceDto ddpInstanceDto) {
        return DDPInstance.builder()
                .withDdpInstanceId(String.valueOf(ddpInstanceDto.getDdpInstanceId()))
                .withName(ddpInstanceDto.getInstanceName())
                .withBaseUrl(ddpInstanceDto.getBaseUrl())
                .withCollaboratorIdPrefix(ddpInstanceDto.getCollaboratorIdPrefix())
                .withDaysMrAttentionNeeded(ddpInstanceDto.getMrAttentionFlagD())
                .withDaysTissueAttentionNeeded(ddpInstanceDto.getTissueAttentionFlagD())
                .withHasAuth0Token(ddpInstanceDto.getAuth0Token() != null)
                .withNotificationRecipient(ddpInstanceDto.getNotificationRecipients())
                .withBillingReference(ddpInstanceDto.getBillingReference())
                .withParticipantIndexES(ddpInstanceDto.getEsParticipantIndex())
                .withActivityDefinitionIndexES(ddpInstanceDto.getEsActivityDefinitionIndex())
                .withUsersIndexES(ddpInstanceDto.getEsUsersIndex())
                .withResearchProject(ddpInstanceDto.getResearchProject().orElse(null))
                .withMercuryOrderCreator(ddpInstanceDto.getMercuryOrderCreator().orElse(null))
                .withDisplayName(ddpInstanceDto.getDisplayName())
                .build();
    }
}
