package org.broadinstitute.dsm.juniperkits;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.KIT_SHIPPING;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestCreateLabel;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.gp.BSPKit;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitStatus;
import org.broadinstitute.dsm.model.kit.KitFinalScanUseCase;
import org.broadinstitute.dsm.model.kit.ScanResult;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.SentAndFinalScanPayload;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.lddp.db.SimpleResult;

/**
 * This class has methods to set up a Juniper study in DSM database.
 * It creates the instance, group, instance_role, kit_type, carrier,
 * kit_dimensions, kit_return and ddp_kit_request_settings for the
 * newly created study.
 * It also contains methods to delete what was set up after tests are complete
 *
 * The usage is by first creating an instance of this class by declaring the desired instance name and study guid,
 * display name and collaborator prefix.
 * Then call setupJuniperInstanceAndSettings() for initiating all the config in database.
 *
 *When done, call deleteJuniperInstanceAndSettings() to delete everything
 */
@Slf4j
public class JuniperSetupUtil {
    private static final String SELECT_INSTANCE_ROLE = "SELECT instance_role_id FROM instance_role WHERE name = 'juniper_study';";
    private static final String INSERT_DDP_INSTANCE_ROLE = "INSERT INTO ddp_instance_role (ddp_instance_id, instance_role_id) "
            + " VALUES (?, ?) ON DUPLICATE KEY UPDATE instance_role_id = ?;";
    private static final String SELECT_KIT_TYPE_ID = "SELECT kit_type_id FROM kit_type WHERE kit_type_name = ?";
    private static final String INSERT_KIT_DIMENSION = "INSERT INTO kit_dimension (kit_width, kit_height, kit_length, kit_weight) "
            + " VALUES ('6.9', '1.3', '5.2', '3.2') ON DUPLICATE KEY UPDATE kit_width = '6.9';";
    private static final String INSERT_KIT_RETURN = "INSERT INTO kit_return_information (return_address_name, return_address_phone) "
            + " VALUES ('Broad Institute', '1111112222') ON DUPLICATE KEY UPDATE return_address_name = 'Broad Institute';";
    private static final String INSERT_CARRIER = "INSERT INTO carrier_service (carrier, service) "
            + " VALUES ('FedEx', 'FEDEX_2_DAY') ON DUPLICATE KEY UPDATE carrier = 'FedEx';";
    private static final String INSERT_DDP_KIT_REQUEST_SETTINGS =
            "INSERT INTO ddp_kit_request_settings (ddp_instance_id, kit_type_id, kit_return_id, carrier_service_to_id, kit_dimension_id) "
                    + " VALUES (?, ?, ?, ?, ?) ;";
    private static final String SELECT_DSM_KIT_REQUEST_ID = "SELECT dsm_kit_request_id from ddp_kit_request where ddp_kit_request_id = ?";
    private static final UserAdminTestUtil adminUtil = new UserAdminTestUtil();
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
    private static String groupName;
    private static String studyGuid;
    private static String displayName;
    private static String collaboratorPrefix;
    private static String userWithKitShippingAccess;

    private static NotificationUtil notificationUtil;
    public JuniperSetupUtil(String instanceName, String studyGuid, String displayName, String collaboratorPrefix, String groupName) {
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.displayName = displayName;
        this.collaboratorPrefix = collaboratorPrefix;
        this.groupName = groupName;
    }

    public void setupJuniperInstanceAndSettings() {
        //everything should get inserted in one transaction
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
            try {
                adminUtil.createRealmAndStudyGroup(instanceName, studyGuid, collaboratorPrefix, groupName, null);
                ddpInstanceId = String.valueOf(adminUtil.getDdpInstanceId());
                ddpGroupId = String.valueOf(adminUtil.getStudyGroupId());
                instanceRoleId = getInstanceRole(conn);
                ddpInstanceRoleId = createDdpInstanceRole(conn);
                kitTypeId = getKitTypeId(conn);
                kitDimensionId = createKitDimension(conn);
                kitReturnId = createKitReturnInformation(conn);
                carrierId = createCarrierInformation(conn);
                ddpKitRequestSettingsId = createKitRequestSettingsInformation(conn);
                adminUtil.setStudyAdminAndRoles(generateUserEmail(), USER_ADMIN_ROLE,
                        Arrays.asList(KIT_SHIPPING));

                userWithKitShippingAccess = Integer.toString(adminUtil.createTestUser(generateUserEmail(),
                        Collections.singletonList(KIT_SHIPPING)));
            } catch (SQLException e) {
                simpleResult.resultException = e;
            }
            return simpleResult;
        });
        if (results.resultException != null) {
            log.error("Error creating juniper data ", results.resultException);
            deleteJuniperInstanceAndSettings();
        }
    }

    public static void deleteJuniperInstanceAndSettings() {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                delete(conn, "kit_dimension", "kit_dimension_id", kitDimensionId);
                delete(conn, "kit_return_information", "kit_return_id", kitReturnId);
                delete(conn, "carrier_service", "carrier_service_id", carrierId);
                delete(conn, "ddp_kit_request_settings", "ddp_kit_request_settings_id", ddpKitRequestSettingsId);
                delete(conn, "ddp_instance_role", "ddp_instance_role_id", ddpInstanceRoleId);
                delete(conn, "ddp_instance_group", "instance_group_id", ddpInstanceGroupId);
                delete(conn, "ddp_instance", "ddp_instance_id", ddpInstanceId);
                delete(conn, "ddp_group", "group_id", ddpGroupId);
                adminUtil.deleteGeneratedData();
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

    public static void changeKitToQueue(JuniperKitRequest juniperKitRequest, EasyPostUtil mockEasyPostUtil) {
        KitRequestShipping[] kitRequests =
                KitRequestShipping.getKitRequestsByRealm(instanceName, "uploaded", "SALIVA").toArray(new KitRequestShipping[1]);
        Optional<KitRequestShipping> kitWeWantToChange = Arrays.stream(kitRequests)
                .filter(kitRequestShipping -> kitRequestShipping.getParticipantId().equals(juniperKitRequest.getJuniperParticipantID()))
                .findFirst();
        if (kitWeWantToChange.isPresent()) {
            KitRequestCreateLabel.updateKitLabelRequested(new KitRequestShipping[] {kitWeWantToChange.get()}, userWithKitShippingAccess,
                    new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow());
            List<KitRequestCreateLabel> kitsLabelTriggered = KitUtil.getListOfKitsLabelTriggered();
            if (!kitsLabelTriggered.isEmpty()) {
                KitUtil.createLabel(kitsLabelTriggered, mockEasyPostUtil);
            }
        }

    }

    public static List<ScanResult> changeKitToSent(JuniperKitRequest juniperTestKit) {
        List<SentAndFinalScanPayload> scanPayloads = new ArrayList<>();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow();
        SentAndFinalScanPayload sentAndFinalScanPayload = new SentAndFinalScanPayload(juniperTestKit.getDdpLabel(), "SOME_RANDOM_KIT_LABEL");
        scanPayloads.add(sentAndFinalScanPayload);
        List<ScanResult> scanResultList = new ArrayList<>();
        KitPayload kitPayload = new KitPayload(scanPayloads, Integer.parseInt(userWithKitShippingAccess), ddpInstanceDto);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, new KitDaoImpl());
        scanResultList.addAll(kitFinalScanUseCase.get());
        return scanResultList;
    }

    private static String generateUserEmail() {
        return "JuniperTest-" + System.currentTimeMillis() + "@broad.dev";
    }

    public static void changeKitToReceived() {
        BSPKit bspKit = new BSPKit();
        String kitLabel = "SOME_RANDOM_KIT_LABEL";
        Optional<BSPKitDto> optionalBSPKitDto = bspKit.canReceiveKit(kitLabel);
        //kit does not exist in ddp_kit table
        if (optionalBSPKitDto.isEmpty()) {
           return;
        }
        //check if kit is from a pt which is withdrawn
        Optional<BSPKitStatus> result = bspKit.getKitStatus(optionalBSPKitDto.get(), notificationUtil);
        if (!result.isEmpty()) {
            return;
        }
        //kit found in ddp_kit table
        bspKit.receiveKit(kitLabel, optionalBSPKitDto.get(), notificationUtil, "BSP").orElseThrow();
    }

    private String createKitRequestSettingsInformation(Connection conn) throws SQLException {
        if (StringUtils.isNotBlank(ddpKitRequestSettingsId)) {
            return ddpKitRequestSettingsId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_KIT_REQUEST_SETTINGS, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, ddpInstanceId);
        stmt.setString(2, kitTypeId);
        stmt.setString(3, kitReturnId);
        stmt.setString(4, carrierId);
        stmt.setString(5, kitDimensionId);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_kit_request_settings");
    }

    private String createCarrierInformation(Connection conn) throws SQLException {
        if (StringUtils.isNotBlank(carrierId)) {
            return carrierId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_CARRIER, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "carrier_service");
    }

    private String createKitReturnInformation(Connection conn) throws SQLException {
        if (StringUtils.isNotBlank(kitReturnId)) {
            return kitReturnId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_RETURN, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_return_information");
    }

    private String createKitDimension(Connection conn) throws SQLException {
        if (StringUtils.isNotBlank(kitDimensionId)) {
            return kitDimensionId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_DIMENSION, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_dimension");
    }

    private String getKitTypeId(Connection conn) throws SQLException {
        if (StringUtils.isNotBlank(kitTypeId)) {
            return kitTypeId;
        }
        PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_TYPE_ID);
        stmt.setString(1, "SALIVA");
        ResultSet rs = stmt.executeQuery();
        return getPrimaryKey(rs, "kit_type");
    }

    private String getInstanceRole(Connection conn) throws SQLException {
        if (StringUtils.isNotBlank(instanceRoleId)) {
            return instanceRoleId;
        }
        PreparedStatement stmt = conn.prepareStatement(SELECT_INSTANCE_ROLE);
        ResultSet rs = stmt.executeQuery();
        return getPrimaryKey(rs, "instance_role");
    }

    private String createDdpInstanceRole(Connection conn) throws SQLException {
        if (StringUtils.isNotBlank(ddpInstanceRoleId)) {
            return ddpInstanceRoleId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_INSTANCE_ROLE, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, ddpInstanceId);
        stmt.setString(2, instanceRoleId);
        stmt.setString(3, instanceRoleId);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_instance_role");
    }

}
