package org.broadinstitute.dsm.juniperkits;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.service.admin.UserAdminService.PEPPER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.KIT_SHIPPING;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.kits.BaseKitTestUtil;
import org.broadinstitute.lddp.db.SimpleResult;

/**
 * This class creates Juniper instance and settings for testing kits. It is used to create all the necessary data for testing kits.
 * First initialize the class and then call setupJuniperInstanceAndSettings() for initiating all the config in database.
 * When done, call deleteInstanceAndSettings() to delete everything
 * </p>
 */
@Slf4j
@Getter
public class JuniperKitUtil extends BaseKitTestUtil {
    private Integer instanceRoleId;
    private Integer ddpInstanceRoleId;
    private String displayName;
    private String collaboratorPrefix;
    private static String esIndex = null;

    public JuniperKitUtil(String instanceName, String studyGuid, String displayName, String collaboratorPrefix, String groupName) {
        super(instanceName, studyGuid, groupName);
        this.displayName = displayName;
        this.collaboratorPrefix = collaboratorPrefix;
    }

    public void setupJuniperInstanceAndSettings() {
        //everything should get inserted in one transaction
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult simpleResult = new SimpleResult();
            try {
                adminUtil.createRealmAndStudyGroup(instanceName, studyGuid, collaboratorPrefix, groupName, esIndex);
                ddpInstanceId = adminUtil.getDdpInstanceId();
                ddpGroupId = adminUtil.getStudyGroupId();
                instanceRoleId = getJuniperStudyInstanceRole(conn);
                ddpInstanceRoleId = createDdpInstanceRole(conn, instanceRoleId);
                kitTypeId = getKitTypeId(conn);
                kitDimensionId = createKitDimension(conn);
                kitReturnId = createKitReturnInformation(conn);
                carrierId = createCarrierInformation(conn);
                ddpKitRequestSettingsId = createKitRequestSettingsInformation(conn);
                adminUtil.setStudyAdminAndRoles(generateUserEmail(), PEPPER_ADMIN_ROLE,
                        Arrays.asList(KIT_SHIPPING));

                userWithKitShippingAccess = Integer.toString(adminUtil.createTestUser(generateUserEmail(),
                        Collections.singletonList(KIT_SHIPPING)));
            } catch (SQLException e) {
                simpleResult.resultException = e;
            }
            return simpleResult;
        });
        if (results.resultException != null) {
            log.error("Error creating data ", results.resultException);
            deleteInstanceAndSettings();
        }
    }

    public void deleteInstanceAndSettings() {
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

    private String generateUserEmail() {
        return "JuniperTest-" + System.currentTimeMillis() + "@broad.dev";
    }

    private Integer getJuniperStudyInstanceRole(Connection conn) throws SQLException {
        if (instanceRoleId != null) {
            return instanceRoleId;
        }
        PreparedStatement stmt = conn.prepareStatement(SELECT_INSTANCE_ROLE);
        stmt.setString(1, "juniper_study");
        ResultSet rs = stmt.executeQuery();
        return getPrimaryKey(rs, "instance_role");
    }




}
