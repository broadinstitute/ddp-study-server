package org.broadinstitute.dsm.db.dao.ddp.instance;

import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;


public class DDPInstanceDao implements Dao<DDPInstanceDto> {

    private static final String SQL_INSERT_DDP_INSTANCE = "INSERT INTO ddp_instance SET " +
            "instance_name = ?, " +
            "study_guid = ?," +
            "display_name = ?," +
            "base_url = ?," +
            "is_active = ?," +
            "bsp_group = ?," +
            "bsp_collection = ?," +
            "bsp_organism = ?," +
            "collaborator_id_prefix = ?," +
            "reminder_notification_wks = ?," +
            "mr_attention_flag_d = ?," +
            "tissue_attention_flag_d = ?," +
            "auth0_token = ?," +
            "notification_recipients = ?," +
            "migrated_ddp = ?," +
            "billing_reference = ?," +
            "es_participant_index = ?," +
            "es_activity_definition_index = ?," +
            "es_users_index = ?";

    private static final String SQL_DELETE_DDP_INSTANCE = "DELETE FROM ddp_instance WHERE ddp_instance_id = ?";

    private static final String SQL_SELECT_INSTANCE_WITH_ROLE = "SELECT ddp_instance_id, instance_name, base_url, collaborator_id_prefix, migrated_ddp, billing_reference, " +
            "es_participant_index, es_activity_definition_index, es_users_index, (SELECT count(role.name) " +
            "FROM ddp_instance realm, ddp_instance_role inRol, instance_role role WHERE realm.ddp_instance_id = inRol.ddp_instance_id AND inRol.instance_role_id = role.instance_role_id AND role.name = ? " +
            "AND realm.ddp_instance_id = main.ddp_instance_id) AS 'has_role', mr_attention_flag_d, tissue_attention_flag_d, auth0_token, notification_recipients FROM ddp_instance main " +
            "WHERE is_active = 1";

    private static final String SQL_GET_INSTANCE_ID_BY_GUID = "SELECT ddp_instance_id " +
            "FROM ddp_instance " +
            "WHERE " +
            "study_guid = ? ";

    private static final String SQL_GET_PARTICIPANT_ES_INDEX_BY_ID = "SELECT es_participant_index " +
            "FROM ddp_instance " +
            "WHERE ddp_instance_id = ?";

    private static final String SQL_GET_PARTICIPANT_ES_INDEX_BY_STUDY_GUID = "SELECT es_participant_index " +
            "FROM ddp_instance " +
            "WHERE study_guid = ?";

    private static final String SQL_GET_COLLABORATOR_ID_PREFIX_BY_STUDY_GUID = "SELECT collaborator_id_prefix " +
            "FROM ddp_instance " +
            "WHERE study_guid = ?";
    public static final String COLLABORATOR_ID_PREFIX = "collaborator_id_prefix";

    private static final String SQL_BASE_SELECT = "SELECT " +
            "ddp_instance_id," +
            "instance_name," +
            "study_guid," +
            "display_name," +
            "base_url," +
            "is_active," +
            "bsp_group," +
            "bsp_collection," +
            "bsp_organism," +
            "collaborator_id_prefix," +
            "reminder_notification_wks," +
            "mr_attention_flag_d," +
            "tissue_attention_flag_d," +
            "auth0_token," +
            "notification_recipients," +
            "migrated_ddp," +
            "billing_reference," +
            "es_participant_index," +
            "es_activity_definition_index," +
            "es_users_index " +
            "FROM ddp_instance ";

    private static final String SQL_SELECT_DDP_INSTANCE_BY_GUID = SQL_BASE_SELECT +
            "WHERE study_guid = ? ";

    private static final String SQL_SELECT_DDP_INSTANCE_BY_INSTANCE_NAME = SQL_BASE_SELECT +
            "WHERE instance_name = ? ";

    private static final String SQL_SELECT_DDP_INSTANCE_BY_INSTANCE_ID = SQL_BASE_SELECT +
            "WHERE ddp_instance_id = ? ";



    public static final String DDP_INSTANCE_ID = "ddp_instance_id";
    public static final String INSTANCE_NAME = "instance_name";
    public static final String STUDY_GUID = "study_guid";
    public static final String DISPLAY_NAME = "display_name";
    public static final String BASE_URL = "base_url";
    public static final String IS_ACTIVE = "is_active";
    public static final String BSP_GROUP = "bsp_group";
    public static final String BSP_COLLECTION = "bsp_collection";
    public static final String BSP_ORGANISM = "bsp_organism";
    public static final String COLLABORATOR_ID_PREFIX1 = "collaborator_id_prefix";
    public static final String REMINDER_NOTIFICATION_WKS = "reminder_notification_wks";
    public static final String MR_ATTENTION_FLAG_D = "mr_attention_flag_d";
    public static final String TISSUE_ATTENTION_FLAG_D = "tissue_attention_flag_d";
    public static final String AUTH0_TOKEN = "auth0_token";
    public static final String NOTIFICATION_RECIPIENTS = "notification_recipients";
    public static final String MIGRATED_DDP = "migrated_ddp";
    public static final String BILLING_REFERENCE = "billing_reference";
    public static final String ES_PARTICIPANT_INDEX = "es_participant_index";
    public static final String ES_ACTIVITY_DEFINITION_INDEX = "es_activity_definition_index";
    public static final String ES_USERS_INDEX = "es_users_index";

    public static boolean getRole(@NonNull String realm, @NonNull String role) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = Boolean.FALSE;
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

    @Override
    public int create(DDPInstanceDto ddpInstanceDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DDP_INSTANCE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, ddpInstanceDto.getInstanceName());
                stmt.setString(2, ddpInstanceDto.getStudyGuid());
                stmt.setString(3, ddpInstanceDto.getDisplayName());
                stmt.setString(4, ddpInstanceDto.getBaseUrl());
                stmt.setBoolean(5, ddpInstanceDto.getIsActive());
                stmt.setString(6, ddpInstanceDto.getBspGroup());
                stmt.setString(7, ddpInstanceDto.getBspCollection());
                stmt.setString(8, ddpInstanceDto.getBspOrganism());
                stmt.setString(9, ddpInstanceDto.getCollaboratorIdPrefix());
                stmt.setObject(10, ddpInstanceDto.getReminderNotificationWks());
                stmt.setObject(11, ddpInstanceDto.getMrAttentionFlagD());
                stmt.setObject(12, ddpInstanceDto.getTissueAttentionFlagD());
                stmt.setBoolean(13, ddpInstanceDto.getAuth0Token());
                stmt.setString(14, getNotificationsAsSequence(ddpInstanceDto));
                stmt.setBoolean(15, ddpInstanceDto.getMigratedDdp());
                stmt.setString(16, ddpInstanceDto.getBillingReference());
                stmt.setString(17, ddpInstanceDto.getEsParticipantIndex());
                stmt.setString(18, ddpInstanceDto.getEsActivityDefinitionIndex());
                stmt.setString(19, ddpInstanceDto.getEsUsersIndex());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error inserting ddp instance ", simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    private String getNotificationsAsSequence(DDPInstanceDto ddpInstanceDto) {
        return ddpInstanceDto.getNotificationRecipients().toString().replace("[", "").replace("]", "").replace("\\s", "");
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_DDP_INSTANCE)) {
                stmt.setInt(1, id);
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException("Error deleting ddp instance ", simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<DDPInstanceDto> get(long id) {
        return Optional.empty();
    }

    public int getDDPInstanceIdByGuid(@NonNull String studyGuid) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_INSTANCE_ID_BY_GUID)) {
                stmt.setString(1, studyGuid);
                try (ResultSet instanceIdRs = stmt.executeQuery()) {
                    if (instanceIdRs.next()) {
                        dbVals.resultValue = instanceIdRs.getInt(DBConstants.DDP_INSTANCE_ID);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting information for " + studyGuid, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get realm information for " + studyGuid, results.resultException);
        }
        return (int) results.resultValue;
    }

    public Optional<DDPInstanceDto> getDDPInstanceByGuid(@NonNull String studyGuid) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DDP_INSTANCE_BY_GUID)) {
                stmt.setString(1, studyGuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDdpInstanceDtoFromResultSet(rs);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting ddp instance for " + studyGuid, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get ddp instance for " + studyGuid, results.resultException);
        }
        return Optional.ofNullable((DDPInstanceDto) results.resultValue);
    }


    public Optional<DDPInstanceDto> getDDPInstanceByInstanceName(@NonNull String instanceName) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DDP_INSTANCE_BY_INSTANCE_NAME)) {
                stmt.setString(1, instanceName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDdpInstanceDtoFromResultSet(rs);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting ddp instance for " + instanceName, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get ddp instance for " + instanceName, results.resultException);
        }
        return Optional.ofNullable((DDPInstanceDto) results.resultValue);
    }

    private DDPInstanceDto getDdpInstanceDtoFromResultSet(ResultSet rs) throws SQLException {
        return new DDPInstanceDto.Builder()
                .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                .withInstanceName(rs.getString(INSTANCE_NAME))
                .withStudyGuid(rs.getString(STUDY_GUID))
                .withDisplayName(rs.getString(DISPLAY_NAME))
                .withBaseUrl(rs.getString(BASE_URL))
                .withIsActive(rs.getBoolean(IS_ACTIVE))
                .withBspGroup(rs.getString(BSP_GROUP))
                .withBspCollection(rs.getString(BSP_COLLECTION))
                .withBspOrganims(rs.getString(BSP_ORGANISM))
                .withCollaboratorIdPrefix(rs.getString(COLLABORATOR_ID_PREFIX1))
                .withReminderNotificationWks(rs.getInt(REMINDER_NOTIFICATION_WKS))
                .withMrAttentionFlagD(rs.getInt(MR_ATTENTION_FLAG_D))
                .withTissueAttentionFlagD(rs.getInt(TISSUE_ATTENTION_FLAG_D))
                .withAuth0Token(rs.getBoolean(AUTH0_TOKEN))
                .withNotificationRecipient(rs.getString(NOTIFICATION_RECIPIENTS))
                .withMigratedDdp(rs.getBoolean(MIGRATED_DDP))
                .withBillingReference(rs.getString(BILLING_REFERENCE))
                .withEsParticipantIndex(rs.getString(ES_PARTICIPANT_INDEX))
                .withEsActivityDefinitionIndex(rs.getString(ES_ACTIVITY_DEFINITION_INDEX))
                .withEsUsersIndex(rs.getString(ES_USERS_INDEX))
                .build();
    }

    public Optional<String> getEsParticipantIndexByInstanceId(int instanceId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_PARTICIPANT_ES_INDEX_BY_ID)) {
                stmt.setInt(1, instanceId);
                try (ResultSet rsInstanceId = stmt.executeQuery()) {
                    if (rsInstanceId.next()) {
                        dbVals.resultValue = rsInstanceId.getString(DBConstants.ES_PARTICIPANT_INDEX);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting participant es index with instance id: " + instanceId, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get participant es index with instance id: " + instanceId, results.resultException);
        }
        return Optional.ofNullable((String) results.resultValue);
    }

    public Optional<String> getEsParticipantIndexByStudyGuid(String studyGuid) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_PARTICIPANT_ES_INDEX_BY_STUDY_GUID)) {
                stmt.setString(1, studyGuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.ES_PARTICIPANT_INDEX);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting participant es index with study guid: " + studyGuid, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get participant es index with study guid: " + studyGuid, results.resultException);
        }
        return Optional.ofNullable((String) results.resultValue);
    }

    public Optional<String> getCollaboratorIdPrefixByStudyGuid(String studyGuid) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_COLLABORATOR_ID_PREFIX_BY_STUDY_GUID)) {
                stmt.setString(1, studyGuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(COLLABORATOR_ID_PREFIX);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting collaborator id prefix with study guid: " + studyGuid, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get collaborator id prefix with study guid: " + studyGuid, results.resultException);
        }
        return Optional.ofNullable((String) results.resultValue);
    }

    public Optional<DDPInstanceDto> getDDPInstanceByInstanceId(Integer ddpInstanceId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_DDP_INSTANCE_BY_INSTANCE_ID)) {
                stmt.setInt(1, ddpInstanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getDdpInstanceDtoFromResultSet(rs);
                    }
                }
                catch (SQLException e) {
                    throw new RuntimeException("Error getting ddp instance for " + ddpInstanceId, e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get ddp instance for " + ddpInstanceId, results.resultException);
        }
        return Optional.ofNullable((DDPInstanceDto) results.resultValue);
    }
}
