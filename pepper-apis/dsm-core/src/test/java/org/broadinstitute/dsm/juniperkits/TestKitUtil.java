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
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.junit.Assert;
import org.mockito.Mock;

/**
 * This class has methods to set up a study that uses kits in DSM database.
 * It creates the instance, group, instance_role, kit_type, carrier,
 * kit_dimensions, kit_return and ddp_kit_request_settings for the
 * newly created study.
 * It also contains methods to delete what was set up after tests are complete
 * <p>
 * The usage is by first creating an instance of this class by declaring the desired instance name and study guid,
 * display name and collaborator prefix.
 * Then call setupStudyWithKitsInstanceAndSettings() for initiating all the config in database.
 * <p>
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
    private static final UserAdminTestUtil cmiAdminUtil = new UserAdminTestUtil();
    public static String ddpGroupId;
    public static String ddpInstanceId;
    public static String ddpInstanceGroupId;
    public static String instanceRoleId;
    public static String ddpInstanceRoleId;
    static List<String> createdKitIds = new ArrayList<>();
    private static String kitTypeId;
    private static String kitDimensionId;
    private static String kitReturnId;
    private static String carrierId;
    private static String ddpKitRequestSettingsId;
    private static String instanceName;
    private static String groupName;
    private static String kitTypeName;
    private static String studyGuid;
    private static String collaboratorPrefix;
    private static String userWithKitShippingAccess;
    @Getter
    @Mock
    static EasyPostUtil mockEasyPostUtil = mock(EasyPostUtil.class);
    @Mock
    static Address mockEasyPostAddress = mock(Address.class);
    @Mock static  Shipment mockEasyPostShipment = mock(Shipment.class);
    @Mock
    static Parcel mockEasyPostParcel = mock(Parcel.class);
    @Mock
    static PostageLabel mockParticipantLabel = mock(PostageLabel.class);
    @Mock
    static Tracker mockShipmentTracker = mock(Tracker.class);

    public TestKitUtil(String instanceName, String studyGuid, String collaboratorPrefix, String groupName,
                                  String kitTypeName) {
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.collaboratorPrefix = collaboratorPrefix;
        this.groupName = groupName;
        this.kitTypeName = kitTypeName;
    }

    public static void deleteInstanceAndSettings() {
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
                cmiAdminUtil.deleteGeneratedData();
            } catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

    }

    public static void deleteSubKitSettings(String[] subKitSettingIds) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            for (String subkitSettingId : subKitSettingIds) {
                try {
                    delete(conn, "sub_kits_settings", "sub_kits_settings_id", subkitSettingId);
                } catch (Exception e) {
                    throw e;
                }

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
    private static void delete(Connection conn, String tableName, String primaryColumn, List<String> ids) {
        for(String id: ids){
            delete(conn, tableName, primaryColumn, id);
        }

    }

    public static void deleteKit(String ddpKitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            List<String> dsmKitRequestId = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(SELECT_DSM_KIT_REQUEST_ID)) {
                stmt.setString(1, ddpKitRequestId.concat("\\_%"));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        dsmKitRequestId.add(rs.getString("dsm_kit_request_id"));
                    }
                    if (dsmKitRequestId.isEmpty()) {
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

    public static void deleteKitsArray() {
        for (String kitId : createdKitIds) {
            try {
                deleteKit(kitId);
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
            log.error("Unable to set up data in {} , going to role back transaction", table);
            return null;
        }
    }

    private static String generateUserEmail() {
        return "Test-" + System.currentTimeMillis() + "@broad.dev";
    }
    public void setupInstanceAndSettings() {
        //everything should get inserted in one transaction
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
            try {
                cmiAdminUtil.createRealmAndStudyGroup(instanceName, studyGuid, collaboratorPrefix, groupName);
                ddpInstanceId = String.valueOf(cmiAdminUtil.getDdpInstanceId());
                ddpGroupId = String.valueOf(cmiAdminUtil.getStudyGroupId());
                instanceRoleId = getInstanceRole(conn);
                ddpInstanceRoleId = createDdpInstanceRole(conn);
                kitTypeId = getKitTypeId(conn, kitTypeName, null, true);
                kitDimensionId = createKitDimension(conn);
                kitReturnId = createKitReturnInformation(conn);
                carrierId = createCarrierInformation(conn);
                ddpKitRequestSettingsId = createKitRequestSettingsInformation(conn);
                cmiAdminUtil.setStudyAdminAndRoles(generateUserEmail(), USER_ADMIN_ROLE,
                        Arrays.asList(KIT_SHIPPING));

                userWithKitShippingAccess = Integer.toString(cmiAdminUtil.createTestUser(generateUserEmail(),
                        Collections.singletonList(KIT_SHIPPING)));
            } catch (SQLException e) {
                simpleResult.resultException = e;
            }
            return simpleResult;
        });
        if (results.resultException != null) {
            log.error("Error creating  data ", results.resultException);
            deleteInstanceAndSettings();
        }
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

    private String getKitTypeId(Connection conn, String kitTypeName, String displayName, boolean isExistingKit) throws SQLException {
        if (StringUtils.isNotBlank(kitTypeId)&& isExistingKit) {
            return kitTypeId;
        }
        String query = SELECT_KIT_TYPE_ID;
        if (StringUtils.isNotBlank(displayName)){
            query = query.concat(SELECT_BY_DISPLAY_NAME);
        }
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, kitTypeName);
        if (StringUtils.isNotBlank(displayName)) {
            stmt.setString(2, displayName);
        }
        ResultSet rs = stmt.executeQuery();
        String kitTypeId = getPrimaryKey(rs, "kit_type");
        if (StringUtils.isBlank(kitTypeId)) {
            kitTypeId = createKitType(conn, kitTypeName, displayName);
        }
        return kitTypeId;
    }

    private String createKitType(Connection conn, String kitTypeName, String displayName) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_KIT_TYPE_ID, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, kitTypeName);
        if (displayName == null) {
            stmt.setNull(2, Types.VARCHAR);
        } else {
            stmt.setString(2, displayName);
        }
        stmt.executeUpdate();
        ResultSet rs = stmt.getGeneratedKeys();
        return getPrimaryKey(rs, "instance_role");
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

    public String[] createSubKitsForTheStudy(String subkitName1, String subkitDisplayName1, int hideSample1, String subkitName2,
                                             String subkitDisplayName2, int hideSample2) {
        String[] ids = new String[2];
        String[] subKitIds = new String[2];
        String subKitId2;
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
            try {
                subKitIds[0] = getKitTypeId(conn, subkitName1, subkitDisplayName1, false);
            }catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
         results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
            try {
                subKitIds[1] = getKitTypeId(conn, subkitName2, subkitDisplayName2, false);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
         results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
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

    public String insertSubKitsSettingsForStudy(String ddpKitRequestSettingsId, String subKitTypeId, int hideSample, Connection conn)
            throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(INSERT_SUB_KITS_SETTINGS, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, ddpKitRequestSettingsId);
        stmt.setString(2, subKitTypeId);
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
     ***/

    public static void createNonPepperTestKit(JuniperKitRequest juniperTestKitRequest, NonPepperKitCreationService nonPepperKitCreationService,
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

    public static JuniperKitRequest generateKitRequestJson(){
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

}
