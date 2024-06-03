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

import com.easypost.model.Address;
import com.easypost.model.Parcel;
import com.easypost.model.PostageLabel;
import com.easypost.model.Shipment;
import com.easypost.model.Tracker;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.kit.KitFinalScanUseCase;
import org.broadinstitute.dsm.model.kit.ScanResult;
import org.broadinstitute.dsm.route.kit.KitPayload;
import org.broadinstitute.dsm.route.kit.SentAndFinalScanPayload;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.mockito.Mock;

/**
 * <p>
 * This class has methods to set up a study that uses kits in DSM database.
 * It creates the instance, group, instance_role, kit_type, carrier,
 * kit_dimensions, kit_return and ddp_kit_request_settings for the
 * newly created study.
 * It also contains methods to delete what was set up after tests are complete
 * </p><p>
 * The usage is by first creating an instance of this class by declaring the desired instance name and study guid,
 * display name and collaborator prefix.
 * Then call setupStudyWithKitsInstanceAndSettings() for initiating all the config in database.
 * </p><p>
 * When done, call deleteJuniperInstanceAndSettings() to delete everything
 * </p>
 */

@Slf4j
@Getter
@Setter
public class PepperKitUtil extends BaseKitTestUtil {

    private static final String SELECT_PT_NOTIF_INSTANCE_ROLE =
            "SELECT instance_role_id FROM instance_role WHERE name = 'kit_participant_notifications_activated';";
    private static final String INSERT_DDP_INSTANCE_ROLE = "INSERT INTO ddp_instance_role (ddp_instance_id, instance_role_id) "
            + " VALUES (?, ?) ON DUPLICATE KEY UPDATE instance_role_id = ?;";

    private static final String INSERT_KIT_TYPE_ID = "INSERT into kit_type (kit_type_name , display_name) VALUES (?, ?)";
    private static final String SELECT_KIT_TYPE_ID = "SELECT kit_type_id FROM kit_type WHERE kit_type_name = ? ";
    private static final String SELECT_BY_DISPLAY_NAME = " and display_name = ? ";
    private static final String INSERT_KIT_DIMENSION = "INSERT INTO kit_dimension (kit_width, kit_height, kit_length, kit_weight) "
            + " VALUES ('6.9', '1.3', '5.2', '3.2') ON DUPLICATE KEY UPDATE kit_width = '6.9';";
    private static final String INSERT_KIT_RETURN = "INSERT INTO kit_return_information (return_address_name, return_address_phone) "
            + " VALUES ('Broad Institute', '1111112222') ON DUPLICATE KEY UPDATE return_address_name = 'Broad Institute';";
    private static final String INSERT_CARRIER = "INSERT INTO carrier_service (carrier, service) "
            + " VALUES ('FedEx', 'FEDEX_2_DAY') ON DUPLICATE KEY UPDATE carrier = 'FedEx';";
    private static final String INSERT_DDP_KIT_REQUEST_SETTINGS =
            "INSERT INTO ddp_kit_request_settings (ddp_instance_id, kit_type_id, kit_return_id, carrier_service_to_id, kit_dimension_id) "
                    + " VALUES (?, ?, ?, ?, ?) ;";

    private static final String INSERT_SUB_KITS_SETTINGS =
            "INSERT INTO  sub_kits_settings (ddp_kit_request_settings_id, kit_type_id, kit_count, hide_on_sample_pages, external_name) "
                    + " VALUES (?, ?, 1, ?, '') ;";
    private static final String SELECT_DSM_KIT_REQUEST_ID =
            "SELECT dsm_kit_request_id from ddp_kit_request where ddp_kit_request_id like ? ";

    private static final String INSERT_EVENT_TYPE =
            "INSERT INTO event_type (ddp_instance_id, event_name, event_description, kit_type_id, event_type) "
                    + " VALUES (?, ?, ?, ?, ?) ;";
    private String kitTypeDisplayName;
    public Integer instanceRoleId;
    public List<Integer> ddpInstanceRoleIdList = new ArrayList<>();
    List<String> createdKitIds = new ArrayList<>();
    private String kitTypeName;
    private String collaboratorPrefix;
    private String esIndex;
    @Getter
    @Mock
    EasyPostUtil mockEasyPostUtil = mock(EasyPostUtil.class);
    @Mock
    Address mockEasyPostAddress = mock(Address.class);
    @Mock static  Shipment mockEasyPostShipment = mock(Shipment.class);
    @Mock
    Parcel mockEasyPostParcel = mock(Parcel.class);
    @Mock
    PostageLabel mockParticipantLabel = mock(PostageLabel.class);
    @Mock
    Tracker mockShipmentTracker = mock(Tracker.class);

    public PepperKitUtil(String instanceName, String studyGuid, String collaboratorPrefix, String groupName,
                         String kitTypeName, String kitTypeDisplayName, String esIndex, String userName) {
        super(instanceName, studyGuid, groupName);
        this.collaboratorPrefix = collaboratorPrefix;
        this.kitTypeName = kitTypeName;
        this.kitTypeDisplayName = kitTypeDisplayName;
        this.esIndex = esIndex;
    }

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
                ddpInstanceRoleIdList.forEach(ddpInstanceRoleId -> delete(conn, "ddp_instance_role",
                        "ddp_instance_role_id", ddpInstanceRoleId));
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

    public void deleteSubKitSettings(Integer[] subKitSettingIds) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            for (int subkitSettingId : subKitSettingIds) {
                try {
                    delete(conn, "sub_kits_settings", "sub_kits_settings_id", subkitSettingId);
                } catch (Exception e) {
                    throw e;
                }

            }
            return dbVals;
        });
    }

    private String generateUserEmail() {
        return "Test-" + System.currentTimeMillis() + "@broad.dev";
    }

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
                int ptInstanceRoleId = getPtNotifInstanceRole(conn);
                ddpInstanceRoleIdList.add(createDdpInstanceRole(conn, ptInstanceRoleId));
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

    public Integer getKitTypeId(Connection conn, String kitTypeName, String displayName, boolean isExistingKit)
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

    private Integer getKitRequestInstanceRole(Connection conn) throws SQLException {
        if (instanceRoleId != null) {
            return instanceRoleId;
        }
        PreparedStatement stmt = conn.prepareStatement(SELECT_INSTANCE_ROLE);
        stmt.setString(1, "kit_request_activated");
        ResultSet rs = stmt.executeQuery();
        return getPrimaryKey(rs, "instance_role");
    }

    private Integer getPtNotifInstanceRole(Connection conn) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(SELECT_PT_NOTIF_INSTANCE_ROLE);
        ResultSet rs = stmt.executeQuery();
        return getPrimaryKey(rs, "instance_role");
    }

    public Integer[] createSubKitsForTheStudy(String subkitName1, String subkitDisplayName1, int hideSample1, String subkitName2,
                                             String subkitDisplayName2, int hideSample2) {
        Integer[] ids = new Integer[2];
        Integer[] subKitIds = new Integer[2];
        inTransaction((conn) -> {
            try {
                subKitIds[0] = getKitTypeId(conn, subkitName1, subkitDisplayName1, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
        inTransaction((conn) -> {
            try {
                subKitIds[1] = getKitTypeId(conn, subkitName2, subkitDisplayName2, false);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
        inTransaction((conn) -> {
            try {
                ids[0] = insertSubKitsSettingsForStudy(ddpKitRequestSettingsId, subKitIds[0], hideSample1, conn);
                ids[1] = insertSubKitsSettingsForStudy(ddpKitRequestSettingsId, subKitIds[1], hideSample2, conn);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
        return ids;
    }

    public Integer insertSubKitsSettingsForStudy(int ddpKitRequestSettingsId, int subKitTypeId, int hideSample, Connection conn)
            throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_SUB_KITS_SETTINGS, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, ddpKitRequestSettingsId);
        stmt.setInt(2, subKitTypeId);
        stmt.setInt(3, hideSample);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "sub_kits_settings");
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


}
