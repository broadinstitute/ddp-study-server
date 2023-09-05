package org.broadinstitute.dsm;

import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.UserUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.KIT_SHIPPING;
import static org.broadinstitute.dsm.statics.DBConstants.PT_LIST_VIEW;

public class AuthenticationTest extends DbTxnBaseTest {

    private static String dsmAdminUserId;

    private static String cmiKitShippingOnlyUserId;

    private static final UserAdminTestUtil cmiAdminUtil = new UserAdminTestUtil();

    private static String studyInstanceName;

    private static String generateUserEmail() {
        return "AuthTest-" + System.currentTimeMillis() + "@broad.dev";
    }

    /**
     * Create new groups, a new ddp_instance, associated roles,
     * a few new users and set their permissions.
     */
    @BeforeClass
    public static void setup() {
        String nameAppend = "." + System.currentTimeMillis();
        String studyInstanceName = "instance." + nameAppend;
        String cmiStudyGroup = "cmi" + nameAppend;

        cmiAdminUtil.createRealmAndStudyGroup(studyInstanceName, cmiStudyGroup);
        cmiAdminUtil.setStudyAdminAndRoles(generateUserEmail(), USER_ADMIN_ROLE, Arrays.asList(KIT_SHIPPING, PT_LIST_VIEW));

        dsmAdminUserId = Integer.toString(cmiAdminUtil.createTestUser(generateUserEmail(), Arrays.asList(KIT_SHIPPING, PT_LIST_VIEW)));
        cmiKitShippingOnlyUserId = Integer.toString(cmiAdminUtil.createTestUser(generateUserEmail(), Collections.singletonList(KIT_SHIPPING)));
    }

    @Test
    public void testKitShipperRoleCanChangeKitInfo() {
        Patch patch = new Patch("0",  "dsmKitRequestId", "0", cmiKitShippingOnlyUserId , new NameValue("kit.collectionDate",  "2023-04-24"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("kit");

        Assert.assertTrue(UserUtil.checkKitShippingAccessForPatch(studyInstanceName, cmiKitShippingOnlyUserId, null, patch));
    }

    @Test
    public void getKitShipperRoleCannotChangeParticipantInfo(){
        Patch patch = new Patch("0", "participantId", "0", cmiKitShippingOnlyUserId, new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("oD");

        Assert.assertFalse(UserUtil.checkKitShippingAccessForPatch(studyInstanceName, cmiKitShippingOnlyUserId, null, patch));
    }

    /**
     * Verifies that users who don't have permission to make
     * patch requests can't make patch requests, and that
     * users who do have said permission can make the requests.
     */
    @Test
    public void mismatchAccessTest(){
        Patch patch1 = new Patch("0", "participantId", "0", dsmAdminUserId , new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch1.setTableAlias("oD");

        try {
            UserUtil.checkUserAccessForPatch(studyInstanceName, cmiKitShippingOnlyUserId, PT_LIST_VIEW, null, patch1);
            Assert.fail("Did not throw expected exception");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }
        try {
            UserUtil.checkUserAccessForPatch(studyInstanceName, cmiKitShippingOnlyUserId, DBConstants.MR_VIEW, null, patch1);
            Assert.fail("Did not throw expected exception");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }
        try {
            UserUtil.checkUserAccessForPatch(studyInstanceName, cmiKitShippingOnlyUserId, DBConstants.MR_ABSTRACTER, null, patch1);
            Assert.fail("Did not throw expected exception");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }

        Patch gpPatch = new Patch("0",  "dsmKitRequestId", "0", cmiKitShippingOnlyUserId, new NameValue("kit.collectionDate",  "2023-04-24"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        gpPatch.setTableAlias("kit");

        try {
            UserUtil.checkKitShippingAccessForPatch(studyInstanceName, dsmAdminUserId, null, gpPatch);
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
        Patch patch = new Patch("0", "participantId", "0", dsmAdminUserId, new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("oD");

        Assert.assertTrue(UserUtil.checkUserAccessForPatch(studyInstanceName, dsmAdminUserId, PT_LIST_VIEW, null, patch));

        Patch patch2 = new Patch("0", "participantId", "0", dsmAdminUserId, new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("m");

        Assert.assertTrue(UserUtil.checkUserAccessForPatch(studyInstanceName, dsmAdminUserId, PT_LIST_VIEW, null, patch2));
    }

    @AfterClass
    public static void teardown() {
        cmiAdminUtil.deleteGeneratedData();
    }
}
