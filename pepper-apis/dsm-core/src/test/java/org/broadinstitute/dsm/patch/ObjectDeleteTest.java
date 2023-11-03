package org.broadinstitute.dsm.patch;

import com.google.gson.Gson;
import net.minidev.json.JSONObject;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.model.patch.DeletePatch;
import org.broadinstitute.dsm.model.patch.DeletePatchFactory;
import org.broadinstitute.dsm.util.DSMOncHistoryCreatorUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ObjectDeleteTest  extends DbTxnBaseTest {
    static DSMOncHistoryCreatorUtil dsmOncHistoryCreatorUtil;

    static int participantId;
    static String realm = "patch_instance";
    static String userEmail = "patchTestUser1@unittest.dev";
    static String guid = "PATCH_TEST_PARTICIPANT";
    @BeforeClass
    public static void doFirst() {
        dsmOncHistoryCreatorUtil =
                new DSMOncHistoryCreatorUtil(realm, realm, userEmail, "anything", "patch_test_group");
        dsmOncHistoryCreatorUtil.initialize();
        participantId = dsmOncHistoryCreatorUtil.createParticipant(guid);
    }

    @AfterClass
    public static void cleanUpAfter() {
        dsmOncHistoryCreatorUtil.deleteParticipant(participantId);
        dsmOncHistoryCreatorUtil.deleteEverything();
    }

    @Test
    public void deleteObject() {
        //this is expected to fail at this point because of not implementation and also fake realms not having an ES index.
        // that should get handled in the DSMOncHistoryCreatorUtil
        try {
            String response = (String) dsmOncHistoryCreatorUtil.createOncHistory(guid, participantId, realm, userEmail);
            JSONObject responseObject = new Gson().fromJson(response, JSONObject.class);
            String oncHistoryDetailId = (String) responseObject.get("oncHistoryDetailId");
            dsmOncHistoryCreatorUtil.deleteOncHistory(guid, participantId, realm, userEmail, oncHistoryDetailId);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

}
