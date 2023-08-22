package org.broadinstitute.dsm.juniperkits;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.lddp.db.SimpleResult;

/**
 * This class has methods to set up a Juniper study in DSM database.
 * It creates the instance, group, instance_role, kit_type, carrier,
 * kit_dimensions, kit_return and ddp_kit_request_settings for the
 * newly created study.
 * It also contains methods to delete what was set up after tests are complete
 */
@Slf4j
public class JuniperSetupUtil {
    private static final String INSERT_JUNIPER_GROUP =
            "INSERT INTO ddp_group (name) VALUES ('juniper test') ON DUPLICATE KEY UPDATE name = 'juniper test';";
    private static final String INSERT_JUNIPER_INSTANCE =
            "INSERT INTO ddp_instance (instance_name, study_guid, display_name, is_active, bsp_organism, "
                    + " collaborator_id_prefix, auth0_token) VALUES (?, ?, ?, 1, 1, ?, 0) ON DUPLICATE KEY UPDATE auth0_token = 0;";
    private static final String INSERT_DDP_INSTANCE_GROUP = "INSERT INTO ddp_instance_group (ddp_instance_id, ddp_group_id) "
            + " VALUES (?, ?) ON DUPLICATE KEY UPDATE ddp_group_id = ?;";
    private static final String INSERT_INSTANCE_ROLE = "INSERT INTO instance_role (name) "
            + " VALUES ('juniper_study') ON DUPLICATE KEY UPDATE name = 'juniper_study';";
    private static final String INSERT_DDP_INSTANCE_ROLE = "INSERT INTO ddp_instance_role (ddp_instance_id, instance_role_id) "
            + " VALUES (?, ?) ON DUPLICATE KEY UPDATE instance_role_id = ?;";
    private static final String INSERT_KIT_TYPE = "INSERT INTO kit_type (kit_type_name, bsp_material_type, bsp_receptacle_type) "
            + " VALUES ('SALIVA', 'Saliva', 'Oragene Kit') ON DUPLICATE KEY UPDATE kit_type_name = 'SALIVA';";
    private static final String INSERT_KIT_DIMENSION = "INSERT INTO kit_dimension (kit_width, kit_height, kit_length, kit_weight) "
            + " VALUES ('6.9', '1.3', '5.2', '3.2') ON DUPLICATE KEY UPDATE kit_width = '6.9';";
    private static final String INSERT_KIT_RETURN = "INSERT INTO kit_return_information (return_address_name, return_address_phone) "
            + " VALUES ('Broad Institute', '1111112222') ON DUPLICATE KEY UPDATE return_address_name = 'Broad Institute';";
    private static final String INSERT_CARRIER = "INSERT INTO carrier_service (carrier, service) "
            + " VALUES ('FedEx', 'FEDEX_2_DAY') ON DUPLICATE KEY UPDATE carrier = 'FedEx';";
    private static final String INSERT_DDP_KIT_REQUEST_SETTINGS =
            "INSERT INTO ddp_kit_request_settings (ddp_instance_id, kit_type_id, kit_return_id, carrier_service_to_id, kit_dimension_id) "
                    + " VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE ddp_instance_id = ?;";
    private static final String SELECT_DSM_KIT_REQUEST_ID = "SELECT dsm_kit_request_id from ddp_kit_request where ddp_kit_request_id = ?";
    public static String ddpGroupId;
    public static String ddpInstanceId;
    public static String ddpInstanceGroupId;
    public static String instanceRoleId;
    public static String ddpInstanceRoleId;
    private static String kitTypeId;
    private static String kitDimensionId;
    private static String kitReturnId;
    private static String carrierId;
    private static String ddpKitRequestSettingsId;

    private static String instanceName;
    private static String studyGuid;
    private static String displayName;
    private static String collaboratorPrefix;

    public JuniperSetupUtil(String instanceName, String studyGuid, String displayName, String collaboratorPrefix) {
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.displayName = displayName;
        this.collaboratorPrefix = collaboratorPrefix;

    }

    private static String createDdpGroupForJuniper(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_JUNIPER_GROUP, Statement.RETURN_GENERATED_KEYS);
        int result = stmt.executeUpdate();
        if (result != 1) {
            throw new DsmInternalError("More than 1 row updated");
        }
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_group");
    }

    public static void deleteJuniperInstanceAndSettings() {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                delete(conn, "kit_type", "kit_type_id", kitTypeId);
                delete(conn, "kit_dimension", "kit_dimension_id", kitDimensionId);
                delete(conn, "kit_return_information", "kit_return_id", kitReturnId);
                delete(conn, "carrier_service", "carrier_service_id", carrierId);
                delete(conn, "ddp_kit_request_settings", "ddp_kit_request_settings_id", ddpKitRequestSettingsId);
                delete(conn, "ddp_instance_role", "ddp_instance_role_id", ddpInstanceRoleId);
                delete(conn, "instance_role", "instance_role_id", instanceRoleId);
                delete(conn, "ddp_instance_group", "instance_group_id", ddpInstanceGroupId);
                delete(conn, "ddp_instance", "ddp_instance_id", ddpInstanceId);
                delete(conn, "ddp_group", "group_id", ddpGroupId);
            } catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

    }

    private static void delete(Connection conn, String tableName, String primaryColumn, String id) {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE from " + tableName + " WHERE " + primaryColumn + " = ? ;")) {
            stmt.setString(1, id);
            try {
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error deleting from " + tableName, e);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error deleting ", ex);
        }
    }

    public static void deleteJuniperKit(String ddpKitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            String dsmKitRequestId;
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_DSM_KIT_REQUEST_ID)) {
                stmt.setString(1, ddpKitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dsmKitRequestId = rs.getString("dsm_kit_request_id");
                    } else {
                        log.warn("Kit Not Found " + ddpKitRequestId);
                        return null;
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error selecting dsm_kit_request_id", e);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Error deleting ", ex);
            }
            try {
                delete(conn, "ddp_kit", "dsm_kit_request_id", dsmKitRequestId);
                delete(conn, "ddp_kit_request", "dsm_kit_request_id", dsmKitRequestId);
            } catch (Exception ex) {
                throw new RuntimeException("Error deleting kits", ex);
            }
            return null;
        });
    }

    public static void deleteKitsArray(List<String> kitIds) {
        for (String kitId : kitIds) {
            try {
                deleteJuniperKit(kitId);
            } catch (Exception e) {
                log.error("unable to delete kitId {}", kitId, e);
            } finally {
                continue;
            }
        }

    }

    private static String getPrimaryKey(ResultSet rs, String table) throws SQLException {
        if (rs.next()) {
            return rs.getString(1);
        } else {
            throw new DsmInternalError(String.format("Unable to set up data in %s for juniper, going to role back transaction", table));
        }
    }

    public void setupJuniperInstanceAndSettings() {

        //everything should get inserted in one transaction
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
            try {
                ddpGroupId = createDdpGroupForJuniper(conn);
                ddpInstanceId = createDdpInstanceForJuniper(conn);
                if (ddpGroupId == null || ddpInstanceId == null) {
                    throw new DsmInternalError("Something went wrong");
                }
                ddpInstanceGroupId = createDdpInstanceGroup(conn);
                instanceRoleId = createInstanceRole(conn);
                ddpInstanceRoleId = createDdpInstanceRole(conn);
                kitTypeId = createKitType(conn);
                kitDimensionId = createKitDimension(conn);
                kitReturnId = createKitReturnInformation(conn);
                carrierId = createCarrierInformation(conn);
                ddpKitRequestSettingsId = createKitRequestSettingsInformations(conn);
            } catch (SQLException e) {
                simpleResult.resultException = e;
            }
            return simpleResult;
        });
        if (results.resultException != null) {
            log.error("Error creating juniper data ", results.resultException);
        }
    }

    private String createKitRequestSettingsInformations(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_KIT_REQUEST_SETTINGS, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, ddpInstanceId);
        stmt.setString(2, kitTypeId);
        stmt.setString(3, kitReturnId);
        stmt.setString(4, carrierId);
        stmt.setString(5, kitDimensionId);
        stmt.setString(6, ddpInstanceId);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_kit_request_settings");
    }

    private String createCarrierInformation(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_CARRIER, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "carrier_service");
    }

    private String createKitReturnInformation(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_RETURN, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_return_information");
    }

    private String createKitDimension(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_DIMENSION, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_dimension");
    }

    private String createKitType(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_TYPE, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_type");
    }

    private String createInstanceRole(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_INSTANCE_ROLE, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "instance_role");
    }

    private String createDdpInstanceRole(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_INSTANCE_ROLE, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, ddpInstanceId);
        stmt.setString(2, instanceRoleId);
        stmt.setString(3, instanceRoleId);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_instance_role");
    }

    private String createDdpInstanceGroup(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_INSTANCE_GROUP, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, ddpInstanceId);
        stmt.setString(2, ddpGroupId);
        stmt.setString(3, ddpGroupId);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_instance_group");
    }

    public String createDdpInstanceForJuniper(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_JUNIPER_INSTANCE, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, instanceName);
        stmt.setString(2, studyGuid);
        stmt.setString(3, displayName);
        stmt.setString(4, collaboratorPrefix);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_instance");
    }

}
