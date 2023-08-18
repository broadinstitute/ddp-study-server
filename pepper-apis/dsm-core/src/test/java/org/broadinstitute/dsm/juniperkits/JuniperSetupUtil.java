package org.broadinstitute.dsm.juniperkits;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.lddp.db.SimpleResult;

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
    static Config cfg;
    private static String kitTypeId;
    private static String kitDimensionId;
    private static String kitReturnId;
    private static String carrierId;
    private static String ddpKitRequestSettingsId;

    public static void setupUpAJuniperInstance(String instanceName, String studyGuid, String displayName, String collaboratorPrefix) {
        //everything should get inserted in one transaction
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_JUNIPER_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                try {
                    int result = stmt.executeUpdate();
                    if (result != 1) {
                        throw new RuntimeException("More than 1 row updated");
                    }
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            ddpGroupId = rs.getString(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting juniper group in ddp_group ", e);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Error inserting juniper group in ddp_group ", ex);
            }
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_JUNIPER_INSTANCE, Statement.RETURN_GENERATED_KEYS)) {
                try {
                    stmt.setString(1, instanceName);
                    stmt.setString(2, studyGuid);
                    stmt.setString(3, displayName);
                    stmt.setString(4, collaboratorPrefix);
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            ddpInstanceId = rs.getString(1);
                        }

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting ddp_instance for juniper group in ddp_instance ", e);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Error inserting ddp_instance for juniper group in ddp_instance ", ex);
            }
            if (ddpGroupId == null || ddpInstanceId == null) {
                throw new RuntimeException("Something went wrong");
            }
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_INSTANCE_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                try {
                    stmt.setString(1, ddpInstanceId);
                    stmt.setString(2, ddpGroupId);
                    stmt.setString(3, ddpGroupId);
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            ddpInstanceGroupId = rs.getString(1);
                        }

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting in ddp_instance_group ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            try (PreparedStatement stmt = conn.prepareStatement(INSERT_INSTANCE_ROLE, Statement.RETURN_GENERATED_KEYS)) {
                try {
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            instanceRoleId = rs.getString(1);
                        }

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting in ddp_instance_role ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_INSTANCE_ROLE, Statement.RETURN_GENERATED_KEYS)) {
                try {
                    stmt.setString(1, ddpInstanceId);
                    stmt.setString(2, instanceRoleId);
                    stmt.setString(3, instanceRoleId);
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            ddpInstanceRoleId = rs.getString(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting in ddp_instance_group ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_TYPE, Statement.RETURN_GENERATED_KEYS)) {
                try {

                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            kitTypeId = rs.getString(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting in kit_type ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_DIMENSION, Statement.RETURN_GENERATED_KEYS)) {
                try {

                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            kitDimensionId = rs.getString(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting in kit_dimension ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_RETURN, Statement.RETURN_GENERATED_KEYS)) {
                try {

                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            kitReturnId = rs.getString(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting in kit_return ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_CARRIER, Statement.RETURN_GENERATED_KEYS)) {
                try {

                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            carrierId = rs.getString(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting in kit_return ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_KIT_REQUEST_SETTINGS, Statement.RETURN_GENERATED_KEYS)) {
                try {
                    stmt.setString(1, ddpInstanceId);
                    stmt.setString(2, kitTypeId);
                    stmt.setString(3, kitReturnId);
                    stmt.setString(4, carrierId);
                    stmt.setString(5, kitDimensionId);
                    stmt.setString(6, ddpInstanceId);
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            ddpKitRequestSettingsId = rs.getString(1);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error inserting in kit_request_settings ", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
    }

    public static void deleteJuniperTestStudies() {
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
        for(String kitId: kitIds){
            try{
                deleteJuniperKit(kitId);
            }catch (Exception e){
                log.error("unable to delete kitId {}",kitId, e);
            }finally {
                continue;
            }
        }

    }

}
