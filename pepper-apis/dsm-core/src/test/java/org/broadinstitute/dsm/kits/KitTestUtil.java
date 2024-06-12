package org.broadinstitute.dsm.kits;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.KIT_SHIPPING;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.easypost.model.Address;
import com.easypost.model.Parcel;
import com.easypost.model.PostageLabel;
import com.easypost.model.Shipment;
import com.easypost.model.Tracker;
import com.typesafe.config.Config;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.dsm.db.DDPInstance;
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
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.mockito.Mock;

@Slf4j
@Getter
public class KitTestUtil {

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

    private static final String INSERT_KIT_TYPE_ID = "INSERT into kit_type (kit_type_name , display_name) VALUES (?, ?)";
    private static final String SELECT_BY_DISPLAY_NAME = " and display_name = ? ";

    private static final String INSERT_SUB_KITS_SETTINGS =
            "INSERT INTO  sub_kits_settings (ddp_kit_request_settings_id, kit_type_id, kit_count, hide_on_sample_pages, external_name) "
                    + " VALUES (?, ?, 1, ?, '') ;";

    private static final String INSERT_EVENT_TYPE =
            "INSERT INTO event_type (ddp_instance_id, event_name, event_description, kit_type_id, event_type) "
                    + " VALUES (?, ?, ?, ?, ?) ;";

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
    private String kitTypeDisplayName;
    public List<Integer> ddpInstanceRoleIdList = new ArrayList<>();
    List<String> createdKitIds = new ArrayList<>();
    private boolean isJuniperStudy = false;
    private String kitTypeName;
    private String collaboratorPrefix;
    private String esIndex;
    private Integer instanceRoleId;
    private Integer ddpInstanceRoleId;

    @Getter
    @Mock
    EasyPostUtil mockEasyPostUtil = mock(EasyPostUtil.class);
    @Mock
    Address mockEasyPostAddress = mock(Address.class);
    @Mock static Shipment mockEasyPostShipment = mock(Shipment.class);
    @Mock
    Parcel mockEasyPostParcel = mock(Parcel.class);
    @Mock
    PostageLabel mockParticipantLabel = mock(PostageLabel.class);
    @Mock
    Tracker mockShipmentTracker = mock(Tracker.class);

    private KitDao kitDao = new KitDao();

    /**
     * Constructor for KitTestUtil, after creating an instance of this class, call {@link #setupInstanceAndSettings}
     * If creating a Juniper study, set the  isJuniperStudy to true and the esIndex will be set null.
     * If deleting the data, call {@link #deleteGeneratedData}
     *
     * @param instanceName name of the instance
     * @param studyGuid study-guid for study
     * @param collaboratorPrefix prefix for collaborator id for kits
     * @param groupName name of the study group
     * @param kitTypeName name of the kit type, for example "SALIVA"
     * @param kitTypeDisplayName display name of the kit type, for example "Saliva"
     * @param esIndex ES index for the study
     * @param isJuniperStudy boolean to indicate if the study is a Juniper study and will have features specific to Juniper studies
     * */
    public KitTestUtil(String instanceName, String studyGuid, String collaboratorPrefix, String groupName,
                       String kitTypeName, String kitTypeDisplayName, String esIndex, boolean isJuniperStudy) {
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.groupName = groupName;
        this.collaboratorPrefix = collaboratorPrefix;
        this.kitTypeName = kitTypeName;
        this.kitTypeDisplayName = kitTypeDisplayName;

        this.isJuniperStudy = isJuniperStudy;
        if (!this.isJuniperStudy) {
            this.esIndex = esIndex;
        } else {
            this.esIndex = null;
            log.info("Juniper study, not setting ES index");
        }
    }

    /**
     * Will delete all the data that was created for the instance and settings
     * Call this after deleting the created participants and kits and all the participant data that belongs to the study
     * */
    public void deleteGeneratedData() {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                delete(conn, "event_type", "ddp_instance_id", ddpInstanceId);
                delete(conn, "EVENT_QUEUE", "ddp_instance_id", ddpInstanceId);
                delete(conn, "kit_return_information", "kit_return_id", kitReturnId);
                delete(conn, "ddp_kit_request_settings", "ddp_kit_request_settings_id", ddpKitRequestSettingsId);
                delete(conn, "carrier_service", "carrier_service_id", carrierId);
                delete(conn, "kit_dimension", "kit_dimension_id", kitDimensionId);
                ddpInstanceRoleIdList.forEach(ddpInstanceRoleId -> {
                    if (ddpInstanceRoleId != null) {
                        delete(conn, "ddp_instance_role", "ddp_instance_role_id", ddpInstanceRoleId);
                    }
                });
                adminUtil.deleteGeneratedData();
            } catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new DsmInternalError("Error deleting instance and settings", results.resultException);
        }

    }

    /**
     * Will set up the instance and settings for the study
     * Call this for creating the study and the study group
     * */
    public void setupInstanceAndSettings() {
        //everything should get inserted in one transaction
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
            try {
                adminUtil.createRealmAndStudyGroup(instanceName, studyGuid, collaboratorPrefix, groupName, esIndex);
                ddpInstanceId = adminUtil.getDdpInstanceId();
                ddpGroupId = adminUtil.getStudyGroupId();
                instanceRoleId = getKitRequestInstanceRole(conn);
                ddpInstanceRoleIdList.add(createDdpInstanceRole(conn, instanceRoleId));
                if (!isJuniperStudy) {
                    Integer ptInstanceRoleId = getPtNotifInstanceRole(conn);
                    ddpInstanceRoleIdList.add(createDdpInstanceRole(conn, ptInstanceRoleId));
                }
                kitTypeId = getKitTypeId(conn, kitTypeName, kitTypeDisplayName, true);
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
            log.error("Error creating  data ", results.resultException);
            deleteGeneratedData();
        }
    }


    private String generateUserEmail() {
        return "Test-" + System.currentTimeMillis() + "@broad.dev";
    }

    private Integer getKitRequestInstanceRole(Connection conn) throws SQLException {
        if (this.isJuniperStudy) {
            return getInstanceRoleId(conn, "juniper_study");
        } else {
            return getInstanceRoleId(conn, "kit_request_activated");
        }
    }

    private Integer getPtNotifInstanceRole(Connection conn) throws SQLException {
        if (isJuniperStudy) {
            return null;
        }
        return getInstanceRoleId(conn, "kit_participant_notifications_activated");
    }

    private Integer getInstanceRoleId(Connection conn, String instanceRoleName)throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(SELECT_INSTANCE_ROLE);
        stmt.setString(1, instanceRoleName);
        ResultSet rs = stmt.executeQuery();
        return getPrimaryKey(rs, "instance_role");
    }

    private Integer getKitTypeId(Connection conn, String kitTypeName, String displayName, boolean isExistingKit)
            throws SQLException {
        if (kitTypeId != null && isExistingKit) {
            return kitTypeId;
        }
        String query = SELECT_KIT_TYPE_ID;
        if (StringUtils.isNotBlank(displayName)) {
            query = query.concat(SELECT_BY_DISPLAY_NAME);
        }
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, kitTypeName);
        if (StringUtils.isNotBlank(displayName)) {
            stmt.setString(2, displayName);
        }
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getInt(1);
        }
        if (kitTypeId == null) {
            kitTypeId = createKitType(conn, kitTypeName, displayName);
        }
        return kitTypeId;
    }

    private Integer createKitType(Connection conn, String kitTypeName, String displayName) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_TYPE_ID, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, kitTypeName);
        if (displayName == null) {
            stmt.setNull(2, Types.VARCHAR);
        } else {
            stmt.setString(2, displayName);
        }
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_type");
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

    protected void delete(Connection conn, String tableName, String primaryColumn, Integer id) {
        if (id == null) {
            log.warn("Id is null, not deleting from " + tableName);
            return;
        }
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

    protected Integer createDdpInstanceRole(Connection conn, Integer instanceRoleId) throws SQLException {
        if (instanceRoleId == null) {
            return null;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_INSTANCE_ROLE, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, ddpInstanceId);
        stmt.setInt(2, instanceRoleId);
        stmt.setInt(3, instanceRoleId);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_instance_role");
    }

    public String createKitRequestShipping(KitRequestShipping kitRequestShipping, DDPInstance ddpInstance,
                                           String userId) {

        return KitRequestShipping.writeRequest(ddpInstance.getDdpInstanceId(), kitRequestShipping.getDdpKitRequestId(), kitTypeId,
                kitRequestShipping.getDdpParticipantId(), kitRequestShipping.getBspCollaboratorParticipantId(),
                kitRequestShipping.getBspCollaboratorSampleId(), userId, null, null, null, false, null,
                ddpInstance, kitTypeName, null);
    }

    public void createEventsForDDPInstance(String eventName, String eventType, String eventDescription, boolean nullKitType) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
            try {
                PreparedStatement stmt = conn.prepareStatement(INSERT_EVENT_TYPE, Statement.RETURN_GENERATED_KEYS);
                stmt.setInt(1, ddpInstanceId);
                stmt.setString(2, eventName);
                stmt.setString(3, eventDescription);
                if (nullKitType) {
                    stmt.setNull(4, Types.INTEGER);
                } else {
                    stmt.setInt(4, kitTypeId);
                }
                stmt.setString(5, eventType);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new DsmInternalError("Error creating event ", e);
            }
            return simpleResult;
        });
    }

    public List<ScanResult> changeKitRequestShippingToSent(KitRequestShipping kitRequestShipping, String kitLabel) {
        List<SentAndFinalScanPayload> scanPayloads = new ArrayList<>();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow();
        SentAndFinalScanPayload sentAndFinalScanPayload = new SentAndFinalScanPayload(kitRequestShipping.getDdpLabel(), kitLabel);
        scanPayloads.add(sentAndFinalScanPayload);
        List<ScanResult> scanResultList = new ArrayList<>();
        KitPayload kitPayload = new KitPayload(scanPayloads, Integer.parseInt(userWithKitShippingAccess), ddpInstanceDto);
        KitFinalScanUseCase kitFinalScanUseCase = new KitFinalScanUseCase(kitPayload, new KitDao());
        scanResultList.addAll(kitFinalScanUseCase.get());
        return scanResultList;
    }

    public void setEsIndex(String esIndex) {
        this.esIndex = esIndex;
        new DDPInstanceDao().updateEsParticipantIndex(ddpInstanceId, esIndex);
    }


    /**
     * reads the test config and sets it as what dsm uses as config.
     * This is needed for the tests that use values from config/secret to run
     * */
    public static void setDsmConfig() {
        ConfigManager configManager = ConfigManager.getInstance();
        Config cfg = configManager.getConfig();
        new DSMConfig(cfg);
    }

}

