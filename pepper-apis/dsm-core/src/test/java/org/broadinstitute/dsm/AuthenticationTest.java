package org.broadinstitute.dsm;

import static org.broadinstitute.dsm.TestHelper.cfg;
import static org.broadinstitute.dsm.TestHelper.setupDB;

import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.UserUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class AuthenticationTest {
    public static final String UNIT_TESTER_EMAIL = "testUsersEmails.unitTesterEmail";
    public static final String GP_UNIT_TESTER_EMAIL = "testUsersEmails.gpUnitTesterEmail";


    @Before
    public void first() {
        setupDB();

    }
    @Test
    @Ignore
    public void GPPatchKitAccessTest(){
        String realm = "osteo2";

        String gpUserId = new UserDao().getUserByEmail(cfg.getString(GP_UNIT_TESTER_EMAIL)).orElse(new UserDto()).getId() + "";

        Patch patch = new Patch("0",  "dsmKitRequestId", "0", gpUserId , new NameValue("kit.collectionDate",  "2023-04-24"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("kit");

        Assert.assertTrue(UserUtil.checkGPUserAccessForPatch(realm, gpUserId, null, patch));
    }

    @Test
    @Ignore
    public void GPPatchOtherAccessTest(){
        String realm = "osteo2";

        String gpUserId = new UserDao().getUserByEmail(cfg.getString(GP_UNIT_TESTER_EMAIL)).orElse(new UserDto()).getId() + "";

        Patch patch = new Patch("0", "participantId", "0", gpUserId , new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("oD");

        Assert.assertFalse(UserUtil.checkGPUserAccessForPatch(realm, gpUserId, null, patch));
    }

    @Test
    @Ignore
    public void mismatchAccessTest(){
        String realm = "osteo2";

        String userId = new UserDao().getUserByEmail(cfg.getString(UNIT_TESTER_EMAIL)).orElse(new UserDto()).getId() +"";
        String gpUserId = new UserDao().getUserByEmail(cfg.getString(GP_UNIT_TESTER_EMAIL)).orElse(new UserDto()).getId() + "";

        Patch patch1 = new Patch("0", "participantId", "0", userId , new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch1.setTableAlias("oD");

        try {
            Assert.assertFalse(UserUtil.checkUserAccessForPatch(realm, gpUserId, DBConstants.PT_LIST_VIEW, null, patch1));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }
        try {
            Assert.assertFalse(UserUtil.checkUserAccessForPatch(realm, gpUserId, DBConstants.MR_VIEW, null, patch1));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }
        try {
            Assert.assertFalse(UserUtil.checkUserAccessForPatch(realm, gpUserId, DBConstants.MR_ABSTRACTER, null, patch1));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }

        Patch gpPatch = new Patch("0",  "dsmKitRequestId", "0", gpUserId , new NameValue("kit.collectionDate",  "2023-04-24"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        gpPatch.setTableAlias("kit");
        try {
            Assert.assertFalse(UserUtil.checkGPUserAccessForPatch(realm, userId, null, gpPatch));
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("User id in patch did not match the one in token"));
        }

    }

    @Test
    @Ignore
    public void userPatchAccessTest(){
        String realm = "osteo2";

        String userId = new UserDao().getUserByEmail(cfg.getString(UNIT_TESTER_EMAIL)).orElse(new UserDto()).getId() + "";

        Patch patch = new Patch("0", "participantId", "0", userId , new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("oD");

        UserUtil.checkUserAccessForPatch(realm, userId, DBConstants.PT_LIST_VIEW, null, patch);

        Patch patch2 = new Patch("0", "participantId", "0", UNIT_TESTER_EMAIL , new NameValue("oD.locationPx",  "location"), null, "XSZSRS1MS3D4OAEK2DPM") ;
        patch.setTableAlias("m");

        UserUtil.checkUserAccessForPatch(realm, userId, DBConstants.PT_LIST_VIEW, null, patch2);
    }



}
