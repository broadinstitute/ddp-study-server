package org.broadinstitute.dsm.kits;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.KitRequestCreateLabel;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.gp.BSPKit;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitStatus;
import org.broadinstitute.dsm.model.kit.KitFinalScanUseCase;
import org.broadinstitute.dsm.model.kit.ScanResult;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.SentAndFinalScanPayload;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;

@Slf4j
@Getter
public class BaseKitTestUtil {

    protected static final String SELECT_INSTANCE_ROLE = "SELECT instance_role_id FROM instance_role WHERE name = ?;";
    protected static final String INSERT_DDP_INSTANCE_ROLE = "INSERT INTO ddp_instance_role (ddp_instance_id, instance_role_id) "
            + " VALUES (?, ?) ON DUPLICATE KEY UPDATE instance_role_id = ?;";
    protected static final String SELECT_KIT_TYPE_ID = "SELECT kit_type_id FROM kit_type WHERE kit_type_name = ?";
    protected static final String INSERT_KIT_DIMENSION = "INSERT INTO kit_dimension (kit_width, kit_height, kit_length, kit_weight) "
            + " VALUES ('6.9', '1.3', '5.2', '3.2') ON DUPLICATE KEY UPDATE kit_width = '6.9';";
    protected static final String INSERT_KIT_RETURN = "INSERT INTO kit_return_information (return_address_name, return_address_phone) "
            + " VALUES ('Broad Institute', '1111112222') ON DUPLICATE KEY UPDATE return_address_name = 'Broad Institute';";
    protected static final String INSERT_CARRIER = "INSERT INTO carrier_service (carrier, service) "
            + " VALUES ('FedEx', 'FEDEX_2_DAY') ON DUPLICATE KEY UPDATE carrier = 'FedEx';";
    protected static final String INSERT_DDP_KIT_REQUEST_SETTINGS =
            "INSERT INTO ddp_kit_request_settings (ddp_instance_id, kit_type_id, kit_return_id, carrier_service_to_id, kit_dimension_id) "
                    + " VALUES (?, ?, ?, ?, ?) ;";
    private static final String SELECT_DSM_KIT_REQUEST_ID =
            "SELECT dsm_kit_request_id from ddp_kit_request where ddp_kit_request_id like ? ";

    protected UserAdminTestUtil adminUtil = new UserAdminTestUtil();
    protected Integer ddpInstanceId;
    protected Integer ddpGroupId;
    protected Integer ddpInstanceGroupId;
    protected Integer kitTypeId;
    protected Integer kitDimensionId;
    protected Integer kitReturnId;
    protected  Integer carrierId;
    protected Integer ddpKitRequestSettingsId;
    protected String instanceName;
    protected  String groupName;
    protected String studyGuid;
    protected String userWithKitShippingAccess;
    protected NotificationUtil notificationUtil;
    private KitDao kitDao = new KitDao();

    public BaseKitTestUtil(String instanceName, String studyGuid,  String groupName) {
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.groupName = groupName;
    }

    public void changeKitToReceived(NotificationUtil notificationUtil, String kitLabel) {
        BSPKit bspKit = new BSPKit();
        Optional<BSPKitDto> optionalBSPKitDto = bspKit.canReceiveKit(kitLabel);
        //kit does not exist in ddp_kit table
        if (!optionalBSPKitDto.isEmpty()) {
            //check if kit is from a pt which is withdrawn
            Optional<BSPKitStatus> result = bspKit.getKitStatus(optionalBSPKitDto.get(), notificationUtil);
            if (result.isEmpty()) {
                //kit found in ddp_kit table
                bspKit.receiveKit(kitLabel, optionalBSPKitDto.get(), notificationUtil, "BSP").orElseThrow();
            }
        }
    }

    protected Integer getPrimaryKey(ResultSet rs, String table) throws SQLException {
        if (rs.next()) {
            return rs.getInt(1);
        } else {
            throw new DsmInternalError(String.format("Unable to set up data in %s, going to role back transaction", table));
        }
    }

    protected int getKitTypeId(Connection conn) throws SQLException {
        if (kitTypeId != null) {
            return kitTypeId;
        }
        PreparedStatement stmt = conn.prepareStatement(SELECT_KIT_TYPE_ID);
        stmt.setString(1, "SALIVA");
        ResultSet rs = stmt.executeQuery();
        return getPrimaryKey(rs, "kit_type");
    }

    protected int createKitDimension(Connection conn) throws SQLException {
        if (kitDimensionId != null) {
            return kitDimensionId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_DIMENSION, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_dimension");
    }

    protected int createKitReturnInformation(Connection conn) throws SQLException {
        if (kitReturnId != null) {
            return kitReturnId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_RETURN, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_return_information");
    }

    protected int createCarrierInformation(Connection conn) throws SQLException {
        if (carrierId != null) {
            return carrierId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_CARRIER, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "carrier_service");
    }

    protected Integer createKitRequestSettingsInformation(Connection conn) throws SQLException {
        if (ddpKitRequestSettingsId != null) {
            return ddpKitRequestSettingsId;
        }
        if (ddpInstanceId == null || kitTypeId == null || kitReturnId == null || carrierId == null || kitDimensionId == null) {
            throw new DsmInternalError("required settings have not been set up");
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_KIT_REQUEST_SETTINGS, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, ddpInstanceId);
        stmt.setInt(2, kitTypeId);
        stmt.setInt(3, kitReturnId);
        stmt.setInt(4, carrierId);
        stmt.setInt(5, kitDimensionId);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_kit_request_settings");
    }

    public void changeKitToQueue(String participantId, EasyPostUtil mockEasyPostUtil) {
        KitRequestShipping[] kitRequests =
                KitRequestShipping.getKitRequestsByRealm(instanceName, "uploaded", "SALIVA").toArray(new KitRequestShipping[1]);
        Optional<KitRequestShipping> kitWeWantToChange = Arrays.stream(kitRequests)
                .filter(kitRequestShipping -> kitRequestShipping.getParticipantId().equals(participantId))
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

    public List<ScanResult> changeKitToSent(String ddpLabel, String kitLabel) {
        List<SentAndFinalScanPayload> scanPayloads = new ArrayList<>();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow();
        SentAndFinalScanPayload sentAndFinalScanPayload = new SentAndFinalScanPayload(ddpLabel, kitLabel);
        scanPayloads.add(sentAndFinalScanPayload);
        List<ScanResult> scanResultList = new ArrayList<>();
        KitPayload kitPayload = new KitPayload(scanPayloads, Integer.parseInt(userWithKitShippingAccess), ddpInstanceDto);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, new KitDao());
        scanResultList.addAll(kitFinalScanUseCase.get());
        return scanResultList;
    }



    public void deleteKitsArray(List<String> kitIds) {
        for (String kitId : kitIds) {
            try {
                deleteKitByDdpKitRequestId(kitId);
            } catch (Exception e) {
                log.error("unable to delete kitId {}", kitId, e);
            } finally {
                continue;
            }
        }
    }

    public void deleteKitByDdpKitRequestId(String ddpKitRequestId) {
        List<Integer> dsmKitRequestIds = new ArrayList<>();
        inTransaction((conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_DSM_KIT_REQUEST_ID)) {
                stmt.setString(1, ddpKitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        dsmKitRequestIds.add(rs.getInt("dsm_kit_request_id"));
                    }
                    if (dsmKitRequestIds.isEmpty()) {
                        throw new DsmInternalError(
                                String.format("Could not find kit %s to delete", ddpKitRequestId));
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error selecting dsm_kit_request_id", e);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Error deleting kit request " + ddpKitRequestId, ex);
            }
            return null;
        });
        dsmKitRequestIds.forEach(this::deleteKitRequestShipping);
    }

    public void deleteKitRequestShipping(int dsmKitRequestId) {
        kitDao.deleteKitRequestShipping(dsmKitRequestId);
    }

    protected void delete(Connection conn, String tableName, String primaryColumn, String id) {
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

    protected void delete(Connection conn, String tableName, String primaryColumn, int id) {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE from " + tableName + " WHERE " + primaryColumn + " = ? ;")) {
            stmt.setInt(1, id);
            try {
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error deleting from " + tableName, e);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error deleting ", ex);
        }
    }

    protected Integer createDdpInstanceRole(Connection conn, int instanceRoleId) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_INSTANCE_ROLE, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, ddpInstanceId);
        stmt.setInt(2, instanceRoleId);
        stmt.setInt(3, instanceRoleId);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_instance_role");
    }

}

