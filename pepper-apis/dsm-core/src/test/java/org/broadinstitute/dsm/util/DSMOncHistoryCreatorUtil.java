package org.broadinstitute.dsm.util;


import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.MR_VIEW;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collections;

import com.google.gson.Gson;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.patch.BasePatch;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.model.patch.PatchFactory;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;
import org.mockito.Mock;

/**
 * This class is to create participants with Medical Record and Onc Histories in DSM
 * It utilises the UserAdminTestUtil to create a ddpInstance and a user with privileges
 * and to use that to be
 *
 * **/
public class DSMOncHistoryCreatorUtil {

    private final UserAdminTestUtil userAdminUtil = new UserAdminTestUtil();
    ParticipantDao participantDao = new ParticipantDao();
    private String instanceName;
    private  String studyGuid;
    private  String groupName;
    private  String userEmail;
    private  String userId;
    private  String collabPrefix;

    @Mock
    private NotificationUtil notificationUtil = mock(NotificationUtil.class);


    public DSMOncHistoryCreatorUtil(String instanceName, String studyGuid, String userEmail, String collabPrefix, String groupName){
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.userEmail = userEmail;
        this.collabPrefix = collabPrefix;
        this.groupName = groupName;
    }

    public void initialize() {
        userAdminUtil.createRealmAndStudyGroup(this.instanceName, this.studyGuid, this.collabPrefix, this.groupName);
        userAdminUtil.setStudyAdminAndRoles("adminUserPatchTest@unittest.dev", USER_ADMIN_ROLE, Arrays.asList(MR_VIEW));
        userId = Integer.toString(userAdminUtil.createTestUser(userEmail, Collections.singletonList("mr_view")));
        //todo create instance with role
    }

    public void deleteEverything() {
        //delete participant and onc histories
        userAdminUtil.deleteStudyAdminAndRoles();
        userAdminUtil.deleteGeneratedData();
    }

    public int getDdpInstanceId(){
        return userAdminUtil.getDdpInstanceId();
    }

    public int createParticipant(String guid) {
        ParticipantDto participantDto = new ParticipantDto.Builder()
                .withDdpParticipantId(guid)
                .withLastChanged(System.currentTimeMillis())
                .withDdpInstanceId(this.getDdpInstanceId())
                .withLastVersionDate(String.valueOf(System.currentTimeMillis()))
                .build();
        int participantId = participantDao.create(participantDto);
        return participantId;
    }

    public void deleteParticipant (int participantId) {
        participantDao.delete(participantId);
    }

    public Object createOncHistory(String guid, int participantId, String realm, String userEmail) throws Exception {
        String newOncHistoryPatchJson = TestUtil.readFile("patchRequests/newOncHistoryPatchRequest.json");
        newOncHistoryPatchJson = newOncHistoryPatchJson.replace("<userEmail>", userEmail)
                .replace("<GUID>", guid)
                .replace("<participantId>", String.valueOf(participantId))
                .replace("<instanceName>", realm);
        Patch newOncHistoryPatch = new Gson().fromJson(newOncHistoryPatchJson, Patch.class);
        BasePatch patcher = PatchFactory.makePatch(newOncHistoryPatch, notificationUtil);
        return patcher.doPatch();
    }

    public Object deleteOncHistory(String guid, int participantId, String realm, String userEmail, String oncHistoryDetailId) throws Exception {
        String newOncHistoryPatchJson = TestUtil.readFile("patchRequests/deleteOncHistoryPlaceHolderPatch.json");
        newOncHistoryPatchJson = newOncHistoryPatchJson.replace("<userEmail>", userEmail)
                .replace("<GUID>", guid)
                .replace("<participantId>", String.valueOf(participantId))
                .replace("<instanceName>", realm)
                .replace("<oncHistoryDetailId>", oncHistoryDetailId);
        Patch deleteOncHistoryPatch = new Gson().fromJson(newOncHistoryPatchJson, Patch.class);
        BasePatch patcher = PatchFactory.makePatch(deleteOncHistoryPatch, notificationUtil);
        return patcher.doPatch();
    }
}
