package org.broadinstitute.dsm.util;


import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.MR_VIEW;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.DeletedObjectDao;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.patch.BasePatch;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.model.patch.PatchFactory;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.junit.Assert;
import org.mockito.Mock;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

/**
 * This class is to create participants with Medical Record and Onc Histories in DSM
 * It utilises the UserAdminTestUtil to create a ddpInstance and a user with privileges
 * and to use that to be
 **/
@Slf4j
public class DSMOncHistoryCreatorUtil {

    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final DeletedObjectDao deletedObjectDao = new DeletedObjectDao();
    private List<Integer> medicalRecordIds = new ArrayList<Integer>();
    List<Integer> participantIds = new ArrayList<>();
    DDPInstitutionDao ddpInstitutionDao = new DDPInstitutionDao();
    MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
    DdpInstanceGroupTestUtil ddpInstanceGroupTestUtil = new DdpInstanceGroupTestUtil();
    private UserAdminTestUtil userAdminUtil;
    private String instanceName;
    private String studyGuid;
    private String groupName;
    private String esIndex;
    private String userEmail;
    private String userId;
    private String collabPrefix;
    @Mock
    private NotificationUtil notificationUtil = mock(NotificationUtil.class);

    public DSMOncHistoryCreatorUtil(String instanceName, String studyGuid, String userEmail, String groupName, String collabPrefix,
                                    String esIndex) {
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.userEmail = userEmail;
        this.collabPrefix = collabPrefix;
        this.groupName = groupName;
        this.esIndex = esIndex;
        userAdminUtil = new UserAdminTestUtil();
    }

    public void initialize() {
        ddpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        userAdminUtil.createRealmAndStudyGroup(instanceName, studyGuid, collabPrefix, groupName, esIndex);
        userAdminUtil.setStudyAdminAndRoles("adminUserPatchTest@unittest.dev", USER_ADMIN_ROLE, Arrays.asList(MR_VIEW));
        userId = Integer.toString(userAdminUtil.createTestUser(userEmail, Collections.singletonList("mr_view")));
        //todo create instance with role
    }

    public void deleteEverything() {
        //this bit is failing now for foreign keys
        //delete participants, medical records and institutions
        deleteParticipants();
        //delete everything else
        userAdminUtil.deleteStudyAdminAndRoles();
        userAdminUtil.deleteGeneratedData();
    }

    public int getDdpInstanceId() {
        return userAdminUtil.getDdpInstanceId();
    }

    public ParticipantDto createParticipant(String guid, DDPInstanceDto ddpInstanceDto) {
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(guid);
        ParticipantDto testParticipant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        ElasticTestUtil.createParticipant(esIndex, testParticipant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json", ddpParticipantId);
        participantIds.add(testParticipant.getParticipantId().get());
        log.debug("ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        return testParticipant;
    }

    public void deleteParticipants() {
        medicalRecordIds.forEach(integer -> medicalRecordDao.delete(integer));
        participantIds.forEach(id -> TestParticipantUtil.deleteParticipantAndInstitution(id));
    }

    public Object createOncHistory(String guid, int participantId, String realm, String userEmail) throws Exception {
        String newOncHistoryPatchJson = TestUtil.readFile("patchRequests/newOncHistoryPatchRequest.json");
        newOncHistoryPatchJson = newOncHistoryPatchJson.replace("<userEmail>", userEmail)
                .replace("<GUID>", guid)
                .replace("<participantId>", String.valueOf(participantId))
                .replace("<instanceName>", realm);
        Patch newOncHistoryPatch = new Gson().fromJson(newOncHistoryPatchJson, Patch.class);
        BasePatch patcher = PatchFactory.makePatch(newOncHistoryPatch, notificationUtil);
        Map<String, Object> response = (Map<String, Object>) patcher.doPatch();
        int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
        OncHistoryDetail oncHistoryDetail =
                OncHistoryDetail.getOncHistoryDetail(String.valueOf(oncHistoryDetailId), realm);
        medicalRecordIds.add(oncHistoryDetail.getMedicalRecordId());
        return response;
    }

    public Object createTissue(String guid, int onHistoryDetailId, String realm, String userEmail) throws Exception {
        String newTissuePatchJson = TestUtil.readFile("patchRequests/newTissuePatch.json");
        newTissuePatchJson = newTissuePatchJson.replace("<userEmail>", userEmail)
                .replace("<GUID>", guid)
                .replace("<oncHistoryDetailId>", String.valueOf(onHistoryDetailId))
                .replace("<instanceName>", realm);
        Patch tissuePatch = new Gson().fromJson(newTissuePatchJson, Patch.class);
        BasePatch patcher = PatchFactory.makePatch(tissuePatch, notificationUtil);
        return patcher.doPatch();
    }

    public void deleteOncHistory(String guid, int participantId, String realm, String userEmail, int oncHistoryDetailId) throws Exception {
        List<String> guids = new ArrayList<>();
        guids.add(guid);
        OncHistoryDetail oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(String.valueOf(oncHistoryDetailId), realm);
        Assert.assertNotNull(oncHistoryDetail);
        String deleteOncHistoryPatchJson = TestUtil.readFile("patchRequests/deleteOncHistoryPlaceHolderPatch.json");
        deleteOncHistoryPatchJson = deleteOncHistoryPatchJson.replace("<userEmail>", userEmail)
                .replace("<GUID>", guid)
                .replace("<participantId>", String.valueOf(participantId))
                .replace("<instanceName>", realm)
                .replace("<oncHistoryDetailId>", String.valueOf(oncHistoryDetailId));
        Patch deleteOncHistoryPatch = new Gson().fromJson(deleteOncHistoryPatchJson, Patch.class);
        BasePatch patcher = PatchFactory.makePatch(deleteOncHistoryPatch, notificationUtil);
        patcher.doPatch();
    }

    /**
     * Call this method to check if an oncHistoryDeleted was deleted propery
     *
     * @param participantGuid    the ddpParticipantId
     * @param oncHistoryDetail   the OncHistoryDetail object of the OncHistory before getting deleted
     * @param oncHistoryDetailId the id of the OncHistoryDetail
     * @param ddpInstanceDto     the instance where the onc history belonged
     * @param expectZeroTissue   mark this as true if the participant originally only had tissues belonging to this onchistory, and so after
     *                           deleting the onc history we expect there to be no more
     * @param expectZeroSmId     mark this as true if the participant originally only had sm ids belonging to this onchistory, and so after
     *                           deleting the onc history we expect there to be no more
     */
    public void assertOncHistoryIsDeleted(String participantGuid, OncHistoryDetail oncHistoryDetail, int oncHistoryDetailId,
                                          DDPInstanceDto ddpInstanceDto, boolean expectZeroTissue, boolean expectZeroSmId) {
        try {
            OncHistoryDetail deletedOncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(String.valueOf(oncHistoryDetailId),
                    ddpInstanceDto.getInstanceName());
            Assert.assertNull(deletedOncHistoryDetail);
            String deletedData = deletedObjectDao.getDeletedDataByPKAndTable(oncHistoryDetailId, DBConstants.DDP_ONC_HISTORY_DETAIL);
            Assert.assertNotNull(deletedData);
            Assert.assertFalse(StringUtils.isBlank(deletedData));
            JSONObject oncHistoryDetailFromData = new Gson().fromJson(deletedData, JSONObject.class);
            Assert.assertEquals((int) ((Double) oncHistoryDetailFromData.get(DBConstants.ONC_HISTORY_DETAIL_ID)).doubleValue(),
                    oncHistoryDetail.getOncHistoryDetailId());
            Assert.assertEquals((int) ((Double) oncHistoryDetailFromData.get(DBConstants.MEDICAL_RECORD_ID)).doubleValue(),
                    oncHistoryDetail.getMedicalRecordId());
            Assert.assertEquals(oncHistoryDetailFromData.get(DBConstants.DATE_PX), oncHistoryDetail.getDatePx());
            Map<String, Object> esDsmMap = ElasticSearchUtil.getObjectsMap(ddpInstanceDto.getEsParticipantIndex(), participantGuid,
                    ESObjectConstants.DSM);
            List<Map<String, Object>> oncHistoryDetails =
                    (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM)).get(ESObjectConstants.ONC_HISTORY_DETAIL);
            long countNumberOfOncHistoriesInEs = oncHistoryDetails.stream().filter(stringObjectMap ->
                    (int) stringObjectMap.get("oncHistoryDetailId") == oncHistoryDetailId).count();
            Assert.assertEquals(0L, countNumberOfOncHistoriesInEs);

            List<Map<String, Object>> tissuesFromES = (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM)).
                    getOrDefault(ESObjectConstants.TISSUE, null);
            if (tissuesFromES != null) {
                Assert.assertFalse(expectZeroTissue);
                long countNumberOfTissues = tissuesFromES.stream().filter(tissueMap ->
                        (int) tissueMap.get("oncHistoryDetailId") == oncHistoryDetailId).count();
                Assert.assertEquals(0L, countNumberOfTissues);
                List<Map<String, Object>> smIdsFromEs = (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM)).
                        getOrDefault(ESObjectConstants.SMID, null);
                if (smIdsFromEs != null) {
                    Assert.assertFalse(expectZeroSmId);
                    long countSmIds = smIdsFromEs.stream().filter(smIdMap ->
                            !tissuesFromES.stream().anyMatch(tissue -> tissue.get("tissueId") == smIdMap.get("tissueId"))).count();
                    Assert.assertEquals(0L, countSmIds);
                } else {
                    Assert.assertTrue(expectZeroSmId);
                }
            } else {
                Assert.assertTrue(expectZeroTissue);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception ", e);
        }

    }

    /**
     * Call this method to check if a deleted oncHistory was deleted propery
     * checks both DB and ES index
     * @param participantGuid  the ddpParticipantId
     * @param tissue           the OncHistoryDetail object of the OncHistory before getting deleted
     * @param tissueId         the id of the OncHistoryDetail
     * @param ddpInstanceDto   the instance where the onc history belonged
     * @param expectZeroSmId   mark this as true if the participant originally only had sm ids belonging to this onchistory, and so after
     *                         deleting the onc history we expect there to be no more
     */
    public void assertTissueIsDeleted(String participantGuid, Tissue tissue, int tissueId, DDPInstanceDto ddpInstanceDto,
                                      boolean expectZeroSmId) {
        try {
            Optional<Tissue> deletedTissue = new TissueDao().get(tissueId);
            Assert.assertTrue(deletedTissue.isEmpty());
            String deletedData = deletedObjectDao.getDeletedDataByPKAndTable(tissueId, DBConstants.DDP_TISSUE);
            Assert.assertNotNull(deletedData);
            Assert.assertFalse(StringUtils.isBlank(deletedData));
            JSONObject tissueFromData = new Gson().fromJson(deletedData, JSONObject.class);
            Assert.assertEquals((int)((Double) tissueFromData.get(DBConstants.TISSUE_ID)).doubleValue(), (int) tissue.getTissueId());
            Assert.assertEquals((int)((Double)tissueFromData.get(DBConstants.ONC_HISTORY_DETAIL_ID)).doubleValue(), (int) tissue.getOncHistoryDetailId());
            Assert.assertEquals(tissueFromData.get(DBConstants.NOTES), tissue.getNotes());
            Map<String, Object> esDsmMap = ElasticSearchUtil.getObjectsMap(ddpInstanceDto.getEsParticipantIndex(), participantGuid,
                    ESObjectConstants.DSM);
            List<Map<String, Object>> tissues =
                    (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get("dsm")).get(ESObjectConstants.TISSUE);
            long countNumberOfOncHistoriesInEs = tissues.stream().filter(stringObjectMap ->
                    (int) stringObjectMap.get("tissueId") == tissueId).count();
            Assert.assertEquals(0L, countNumberOfOncHistoriesInEs);
            List<Map<String, Object>> smIdsFromEs = (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get("dsm")).
                    getOrDefault(ESObjectConstants.SMID, null);
            if (smIdsFromEs != null) {
                Assert.assertFalse(expectZeroSmId);
                long countSmIds = smIdsFromEs.stream().filter(smIdMap ->
                        !tissues.stream().anyMatch(tissue1 -> tissue1.get("tissueId") == smIdMap.get("tissueId"))).count();
                Assert.assertEquals(0L, countSmIds);
            } else {
                Assert.assertTrue(expectZeroSmId);
            }

        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception ", e);
        }

    }
}
