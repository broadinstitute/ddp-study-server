package org.broadinstitute.dsm;


import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.service.admin.SetUserRoleRequest;
import org.broadinstitute.dsm.service.admin.UserAdminService;
import org.broadinstitute.dsm.service.admin.UserAdminServiceTest;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.UserUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.KIT_SHIPPING;
import static org.broadinstitute.dsm.statics.DBConstants.PT_LIST_VIEW;

public class AuthenticationTest extends DbTxnBaseTest {

    private static UserDto dsmAdminUser;

    private static UserDto cmiKitShippingOnlyUser;

    private static DDPInstanceDto ddpInstance;

    private static int pecgsStudyGroupId;

    private static int cmiStudyGroupId;

    private static final UserDao userDao = new UserDao();

    private static final Set<Integer> groupRoleIdsToDelete = new HashSet<>();

    private static UserDto generateUser() {
        UserDto user = new UserDto("AuthTest." + System.currentTimeMillis(), "fake+" + System.currentTimeMillis() + "@broad.dev", "5555551212");
        int userId = userDao.create(user);
        user.setId(userId);
        return user;
    }

    /**
     * Create new groups, a new ddp_instance, associated roles,
     * a few new users and set their permissions.
     */
    @BeforeClass
    public static void setup() {
        dsmAdminUser = generateUser();
        cmiKitShippingOnlyUser = generateUser();

        String nameAppend = "." + System.currentTimeMillis();
        String pecgsStudyGroup = "pecgs" + nameAppend;
        String studyInstanceName = "instance." + nameAppend;
        pecgsStudyGroupId = UserAdminService.addStudyGroup(pecgsStudyGroup);

        String cmiStudyGroup = "cmi" + nameAppend;
        cmiStudyGroupId = UserAdminService.addStudyGroup(cmiStudyGroup);

        int ddpInstanceId = UserAdminServiceTest.createTestInstance(studyInstanceName, cmiStudyGroupId);
        ddpInstance = new DDPInstanceDto.Builder().withDdpInstanceId(ddpInstanceId).withInstanceName(studyInstanceName).build();

        UserAdminService adminServiceForDsmAdminUser = new UserAdminService(dsmAdminUser.getIdAsString(), pecgsStudyGroup);
        UserAdminService adminServiceForKitShippingUser = new UserAdminService(dsmAdminUser.getIdAsString(), cmiStudyGroup);

        int adminRoleId = UserAdminService.getRoleId(USER_ADMIN_ROLE);
        int kitShippingRoleId = UserAdminService.getRoleId(KIT_SHIPPING);
        int participantListRoleId = UserAdminService.getRoleId(PT_LIST_VIEW);

        groupRoleIdsToDelete.add(UserAdminService.addGroupRole(cmiStudyGroupId, kitShippingRoleId, adminRoleId));
        groupRoleIdsToDelete.add(UserAdminService.addGroupRole(cmiStudyGroupId, participantListRoleId, adminRoleId));
        groupRoleIdsToDelete.add(UserAdminService.addGroupRole(pecgsStudyGroupId, kitShippingRoleId, adminRoleId));
        groupRoleIdsToDelete.add(UserAdminService.addGroupRole(pecgsStudyGroupId, participantListRoleId, adminRoleId));

        adminServiceForDsmAdminUser.setGroupAdminRole(dsmAdminUser.getId(), pecgsStudyGroupId);
        adminServiceForKitShippingUser.setGroupAdminRole(dsmAdminUser.getId(), cmiStudyGroupId);

        adminServiceForKitShippingUser.setUserRoles(new SetUserRoleRequest(dsmAdminUser.getEmailOrThrow(), Arrays.asList(KIT_SHIPPING, PT_LIST_VIEW)));
        adminServiceForKitShippingUser.setUserRoles(new SetUserRoleRequest(cmiKitShippingOnlyUser.getEmailOrThrow(), KIT_SHIPPING));
    }

    @Test
    public void testKitShipperRoleCanChangeKitInfo() {
        String realm = ddpInstance.getInstanceName(); // should be a ddp instance in the pe-cgs group

        Patch patch = new Patch("0",  "dsmKitRequestId", "0", cmiKitShippingOnlyUser.getIdAsString() , new NameValue("kit.collectionDate",  "2023-04-24"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("kit");

        Assert.assertTrue(UserUtil.checkKitShippingAccessForPatch(realm, cmiKitShippingOnlyUser.getIdAsString(), null, patch));
    }

    @Test
    public void getKitShipperRoleCannotChangeParticipantInfo(){
        String realm = ddpInstance.getInstanceName();

        Patch patch = new Patch("0", "participantId", "0", cmiKitShippingOnlyUser.getIdAsString() , new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("oD");

        Assert.assertFalse(UserUtil.checkKitShippingAccessForPatch(realm, cmiKitShippingOnlyUser.getIdAsString(), null, patch));
    }

    /**
     * Verifies that users who don't have permission to make
     * patch requests can't make patch requests, and that
     * users who do have said permission can make the requests.
     */
    @Test
    public void mismatchAccessTest(){
        String realm = ddpInstance.getInstanceName();

        Patch patch1 = new Patch("0", "participantId", "0", dsmAdminUser.getIdAsString() , new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch1.setTableAlias("oD");

        try {
            UserUtil.checkUserAccessForPatch(realm, cmiKitShippingOnlyUser.getIdAsString(), PT_LIST_VIEW, null, patch1);
            Assert.fail("Did not throw expected exception");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }
        try {
            UserUtil.checkUserAccessForPatch(realm, cmiKitShippingOnlyUser.getIdAsString(), DBConstants.MR_VIEW, null, patch1);
            Assert.fail("Did not throw expected exception");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }
        try {
            UserUtil.checkUserAccessForPatch(realm, cmiKitShippingOnlyUser.getIdAsString(), DBConstants.MR_ABSTRACTER, null, patch1);
            Assert.fail("Did not throw expected exception");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }

        Patch gpPatch = new Patch("0",  "dsmKitRequestId", "0", cmiKitShippingOnlyUser.getIdAsString() , new NameValue("kit.collectionDate",  "2023-04-24"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        gpPatch.setTableAlias("kit");

        try {
            UserUtil.checkKitShippingAccessForPatch(realm, dsmAdminUser.getIdAsString(), null, gpPatch);
            Assert.fail("Did not throw expected exception");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }
    }

    /**
     * Verifies that a user who has access to interact
     * with the participant list can update participant
     * information
     */
    @Test
    public void userPatchAccessTest(){
        String realm = ddpInstance.getInstanceName();

        Patch patch = new Patch("0", "participantId", "0", dsmAdminUser.getIdAsString() , new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("oD");

        Assert.assertTrue(UserUtil.checkUserAccessForPatch(realm, dsmAdminUser.getIdAsString(), PT_LIST_VIEW, null, patch));

        Patch patch2 = new Patch("0", "participantId", "0", dsmAdminUser.getIdAsString() , new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("m");

        Assert.assertTrue(UserUtil.checkUserAccessForPatch(realm, dsmAdminUser.getIdAsString(), PT_LIST_VIEW, null, patch2));
    }

    @AfterClass
    public static void teardown() {
        UserAdminService.deleteUserRoles(dsmAdminUser.getId());
        UserAdminService.deleteUserRoles(cmiKitShippingOnlyUser.getId());
        for (Integer groupRoleId : groupRoleIdsToDelete) {
            UserAdminService.deleteGroupRole(groupRoleId);
        }
        UserAdminServiceTest.deleteInstance(ddpInstance.getDdpInstanceId());
        UserAdminService.deleteStudyGroup(pecgsStudyGroupId);
        UserAdminService.deleteStudyGroup(cmiStudyGroupId);

        userDao.delete(dsmAdminUser.getId());
        userDao.delete(cmiKitShippingOnlyUser.getId());
    }
}
