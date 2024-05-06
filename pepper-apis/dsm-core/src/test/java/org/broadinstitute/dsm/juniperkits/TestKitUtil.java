package org.broadinstitute.dsm.juniperkits;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.KIT_SHIPPING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.easypost.model.Parcel;
import com.easypost.model.PostageLabel;
import com.easypost.model.Shipment;
import com.easypost.model.Tracker;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.kit.nonPepperKit.NonPepperKitStatusDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.junit.Assert;
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
 * </p>
 * When done, call deleteJuniperInstanceAndSettings() to delete everything
 */

@Slf4j
public class TestKitUtil {

    private static final String SELECT_INSTANCE_ROLE = "SELECT instance_role_id FROM instance_role WHERE name = 'juniper_study';";
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
    private static final String SELECT_DSM_KIT_REQUEST_ID = "SELECT dsm_kit_request_id from ddp_kit_request where ddp_kit_request_id like ? ";
    public final UserAdminTestUtil adminUtil = new UserAdminTestUtil();
    private String kitTypeDisplayName;
    public Integer ddpGroupId;
    public Integer ddpInstanceId;
    public Integer instanceRoleId;
    public Integer ddpInstanceRoleId;
    List<String> createdKitIds = new ArrayList<>();
    public Integer kitTypeId;
    private Integer kitDimensionId;
    private Integer kitReturnId;
    private Integer carrierId;
    private Integer ddpKitRequestSettingsId;
    private String instanceName;
    private String groupName;
    private String kitTypeName;
    private String studyGuid;
    private String collaboratorPrefix;
    private String userWithKitShippingAccess;
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

    public TestKitUtil(String instanceName, String studyGuid, String collaboratorPrefix, String groupName,
                                  String kitTypeName, String kitTypeDisplayName) {
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.collaboratorPrefix = collaboratorPrefix;
        this.groupName = groupName;
        this.kitTypeName = kitTypeName;
        this.kitTypeDisplayName = kitTypeDisplayName;
    }

    public void deleteGeneratedData() {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                delete(conn, "kit_return_information", "kit_return_id", kitReturnId);
                delete(conn, "ddp_kit_request_settings", "ddp_kit_request_settings_id", ddpKitRequestSettingsId);
                delete(conn, "carrier_service", "carrier_service_id", carrierId);
                delete(conn, "kit_dimension", "kit_dimension_id", kitDimensionId);
                delete(conn, "ddp_instance_role", "ddp_instance_role_id", ddpInstanceRoleId);
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

    private void delete(Connection conn, String tableName, String primaryColumn, int id) {
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

    private void delete(Connection conn, String tableName, String primaryColumn, List<Integer> ids) {
        for (int id: ids) {
            delete(conn, tableName, primaryColumn, id);
        }
    }

    public void deleteKit(String ddpKitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            List<Integer> dsmKitRequestId = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_DSM_KIT_REQUEST_ID)) {
                stmt.setString(1, ddpKitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        dsmKitRequestId.add(rs.getInt("dsm_kit_request_id"));
                    }
                    if (dsmKitRequestId.isEmpty()) {
                        throw new DsmInternalError(
                                String.format("Could not find kit %s to delete", ddpKitRequestId));
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Error selecting dsm_kit_request_id", e);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Error deleting kit request " + ddpKitRequestId, ex);
            }
            try {
                delete(conn, "ddp_kit", "dsm_kit_request_id", dsmKitRequestId);
                delete(conn, "ddp_kit_request", "dsm_kit_request_id", dsmKitRequestId);
            } catch (Exception ex) {
                throw new RuntimeException("Error deleting kit request " + ddpKitRequestId, ex);
            }
            return null;
        });
    }

    public void deleteKitsArray() {
        for (String kitId : createdKitIds) {
            try {
                deleteKit(kitId);
            } catch (Exception e) {
                throw new DsmInternalError("Could not delete kit " + kitId, e);
            }
        }
    }

    private Integer getPrimaryKey(ResultSet rs, String table) throws SQLException {
        if (rs.next()) {
            return rs.getInt(1);
        } else {
            throw new DsmInternalError(String.format("Unable to get primary key for %s", table));
        }
    }

    private String generateUserEmail() {
        return "Test-" + System.currentTimeMillis() + "@broad.dev";
    }

    public void setupInstanceAndSettings() {
        //everything should get inserted in one transaction
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
            try {
                adminUtil.createRealmAndStudyGroup(instanceName, studyGuid, collaboratorPrefix, groupName, null);
                ddpInstanceId = adminUtil.getDdpInstanceId();
                ddpGroupId = adminUtil.getStudyGroupId();
                instanceRoleId = getInstanceRole(conn);
                ddpInstanceRoleId = createDdpInstanceRole(conn);
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

    private Integer createKitRequestSettingsInformation(Connection conn) throws SQLException {
        if (ddpKitRequestSettingsId != null) {
            return ddpKitRequestSettingsId;
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

    private Integer createCarrierInformation(Connection conn) throws SQLException {
        if (carrierId != null) {
            return carrierId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_CARRIER, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "carrier_service");
    }

    private Integer createKitReturnInformation(Connection conn) throws SQLException {
        if (kitReturnId != null) {
            return kitReturnId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_RETURN, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_return_information");
    }

    private Integer createKitDimension(Connection conn) throws SQLException {
        if (kitDimensionId != null) {
            return kitDimensionId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_DIMENSION, Statement.RETURN_GENERATED_KEYS);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "kit_dimension");
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
        Integer kitTypeId = getPrimaryKey(rs, "kit_type");
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

    private Integer getInstanceRole(Connection conn) throws SQLException {
        if (instanceRoleId != null) {
            return instanceRoleId;
        }
        PreparedStatement stmt = conn.prepareStatement(SELECT_INSTANCE_ROLE);
        ResultSet rs = stmt.executeQuery();
        return getPrimaryKey(rs, "instance_role");
    }

    private Integer createDdpInstanceRole(Connection conn) throws SQLException {
        if (ddpInstanceRoleId != null) {
            return ddpInstanceRoleId;
        }
        PreparedStatement stmt = conn.prepareStatement(INSERT_DDP_INSTANCE_ROLE, Statement.RETURN_GENERATED_KEYS);
        stmt.setInt(1, ddpInstanceId);
        stmt.setInt(2, instanceRoleId);
        stmt.setInt(3, instanceRoleId);
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "ddp_instance_role");
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

    /**
     * this method creates the test KitRequest in the database  by calling
     * `NonPepperKitCreationService.createNonPepperKit` and verifies the response is as expected
     *
     * @param juniperTestKitRequest a JuniperKitRequest that can be passed to the kti creation service
     **/

    public void createNonPepperTestKit(JuniperKitRequest juniperTestKitRequest, NonPepperKitCreationService nonPepperKitCreationService,
                                       DDPInstance ddpInstance) {
        createdKitIds.add(juniperTestKitRequest.getJuniperKitId());
        when(mockEasyPostShipment.getPostageLabel()).thenReturn(mockParticipantLabel);
        when(mockEasyPostShipment.getTracker()).thenReturn(mockShipmentTracker);
        when(mockShipmentTracker.getPublicUrl()).thenReturn("PUBLIC_URL");
        when(mockParticipantLabel.getLabelUrl()).thenReturn("MOCK_LABEL_URL");
        when(mockEasyPostAddress.getId()).thenReturn("SOME_STRING");
        when(mockEasyPostUtil.getEasyPostAddressId(any(), any(), any())).thenReturn("SOME_STRING");
        try {
            when(mockEasyPostUtil.createParcel(any(), any(), any(), any())).thenReturn(mockEasyPostParcel);
            when(mockEasyPostUtil.getAddress(any())).thenReturn(mockEasyPostAddress);
            when(mockEasyPostUtil.createAddressWithoutValidation(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(
                    mockEasyPostAddress);
            when(mockEasyPostUtil.buyShipment(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(mockEasyPostShipment);
        } catch (EasyPostException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        List<KitRequestShipping> oldKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitTypeName);
        KitResponse kitResponse =
                nonPepperKitCreationService.createNonPepperKit(juniperTestKitRequest, kitTypeName, mockEasyPostUtil, ddpInstance);
        Assert.assertFalse(kitResponse.isError());
        Assert.assertNotNull(kitResponse.getKits());
        Assert.assertEquals(1, kitResponse.getKits().size());
        NonPepperKitStatusDto kitStatus = kitResponse.getKits().get(0);
        Assert.assertEquals(juniperTestKitRequest.getJuniperKitId(), kitStatus.getJuniperKitId());
        Assert.assertNotNull(kitStatus.getDsmShippingLabel());
        Assert.assertNotNull(kitStatus.getCollaboratorSampleId());
        Assert.assertNotNull(kitStatus.getCollaboratorParticipantId());
        List<KitRequestShipping> newKits = KitRequestShipping.getKitRequestsByRealm(instanceName, "overview", kitTypeName);
        Assert.assertEquals(oldKits.size() + 1, newKits.size());
        KitRequestShipping newKit =
                newKits.stream().filter(kitRequestShipping -> kitRequestShipping.getDdpParticipantId()
                                .equals(juniperTestKitRequest.getJuniperParticipantID()))
                        .findAny().get();
        Assert.assertEquals(newKit.getBspCollaboratorParticipantId(),
                collaboratorPrefix + "_" + juniperTestKitRequest.getJuniperParticipantID());
        Assert.assertEquals(newKit.getBspCollaboratorSampleId(),
                collaboratorPrefix + "_" + juniperTestKitRequest.getJuniperParticipantID() + "_" + kitTypeName);
    }

    public JuniperKitRequest generateKitRequestJson() {
        String participantId = "TEST_PARTICIPANT";

        String json = "{ \"firstName\":\"P\","
                + "\"lastName\":\"T\","
                + "\"street1\":\"415 Main st\","
                + "\"street2\":null,"
                + "\"city\":\"Cambridge\","
                + "\"state\":\"MA\","
                + "\"postalCode\":\"02142\","
                + "\"country\":\"USA\","
                + "\"phoneNumber\":\" 111 - 222 - 3344\","
                + "\"juniperKitId\":\"kitId_\","
                + "\"juniperParticipantID\":\"" + participantId  + "\","
                + "\"skipAddressValidation\":false,"
                + "\"juniperStudyID\":\"Juniper-test-guid\"}";

        return new Gson().fromJson(json, JuniperKitRequest.class);
    }

    public String createKitRequestShipping(String ddpParticipantId, String collaboratorSampleId, String collaboratorParticipantId,
                                        String ddpLabel, String ddpKitRequestId, String kitType, DDPInstance ddpInstance, String userId) {
        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setDdpParticipantId(ddpParticipantId);
        kitRequestShipping.setBspCollaboratorSampleId(collaboratorSampleId);
        kitRequestShipping.setBspCollaboratorParticipantId(collaboratorParticipantId);
        kitRequestShipping.setDdpLabel(ddpLabel);
        kitRequestShipping.setDdpKitRequestId(ddpKitRequestId);
        kitRequestShipping.setKitTypeName(kitType);
        kitRequestShipping.setKitTypeId(String.valueOf(kitTypeId));

        return KitRequestShipping.writeRequest(ddpInstance.getDdpInstanceId(), ddpKitRequestId, kitTypeId, ddpParticipantId, collaboratorParticipantId,
                collaboratorSampleId, userId, null, null, null, false, null,
                ddpInstance, kitTypeName, null);
    }

}
