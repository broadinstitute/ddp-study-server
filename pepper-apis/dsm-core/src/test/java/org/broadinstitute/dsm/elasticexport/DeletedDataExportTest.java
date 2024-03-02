package org.broadinstitute.dsm.elasticexport;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.export.ExportToES;
import org.broadinstitute.dsm.pubsub.DSMtasksSubscription;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.OncHistoryTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class DeletedDataExportTest extends DbAndElasticBaseTest {
    private static final String instanceName = "delete_instance";
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
    private static final String groupName = "delete_group";
    static OncHistoryTestUtil oncHistoryTestUtil;
    static String userEmail = "deleteTestUser1@unittest.dev";
    static String adminUserEmail = "adminUserDeleteExport@unittest.dev";
    private static String esIndex;
    private static int remainingOncHistoryDetailId;
    private static DDPInstanceDto ddpInstanceDto;
    String guid = "DELETE_PARTICIPANT";
    String guid0 = "DELETE0_PARTICIPANT";
    String guid1 = "DELETE1_PARTICIPANT";
    String guid2 = "DELETE2_PARTICIPANT";
    String guid3 = "DELETE3_PARTICIPANT";

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        oncHistoryTestUtil = new OncHistoryTestUtil(instanceName, instanceName, userEmail, adminUserEmail, groupName,
                "lmsPrefix", esIndex);
        oncHistoryTestUtil.initialize();
        ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow();
    }

    @AfterClass
    public static void cleanUpAfter() {
        oncHistoryDetailDao.delete(remainingOncHistoryDetailId);
        oncHistoryTestUtil.deleteEverything();
        try {
            ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
        ElasticTestUtil.deleteIndex(esIndex);
    }


    // TODO: remove or modify this test. We do not want to have export/migrate fixup onc history records that
    // were not deleted properly (that is, the onc histories were not deleted from the ES).
    // There was a one-time need for this capability, but we cannot depend on it long-term without making significant
    // changes to the export/migrate code. -DC
    @Ignore
    //this test checks if a deleted record from database will also be removed from ES after running an export
    @Test
    public void deleteAllOncHistories() throws Exception {
        ParticipantDto participantDto = oncHistoryTestUtil.createParticipant(guid, ddpInstanceDto);
        guid = participantDto.getDdpParticipantIdOrThrow();
        int participantId = participantDto.getParticipantIdOrThrow();
        Map<String, Object> response =
                (Map<String, Object>) oncHistoryTestUtil.createOncHistory(guid, participantId, instanceName, userEmail);
        int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
        OncHistoryDetail oncHistoryDetail =
                OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
        Assert.assertNotNull(oncHistoryDetail);
        Map<String, Object> response2 =
                (Map<String, Object>) oncHistoryTestUtil.createOncHistory(guid, participantId, instanceName, userEmail);
        int oncHistoryDetailId2 = Integer.parseInt((String) response2.get("oncHistoryDetailId"));
        OncHistoryDetail oncHistoryDetail2 =
                OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
        Assert.assertNotNull(oncHistoryDetail2);
        oncHistoryDetailDao.delete(oncHistoryDetailId);
        oncHistoryDetailDao.delete(oncHistoryDetailId2);
        Assert.assertNull(OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName()));
        Assert.assertNull(OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId2, ddpInstanceDto.getInstanceName()));
        ExportToES.ExportPayload exportPayload = new ExportToES.ExportPayload();
        exportPayload.setStudy(instanceName);
        exportPayload.setIsMigration(true);
        DSMtasksSubscription.migrateToES(exportPayload);
        Map<String, Object> esDsmMap = ElasticSearchUtil.getObjectsMap(ddpInstanceDto.getEsParticipantIndex(), guid, ESObjectConstants.DSM);
        List<Map<String, Object>> oncHistoryDetails =
                (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM))
                        .get(ESObjectConstants.ONC_HISTORY_DETAIL);
        Assert.assertTrue(oncHistoryDetails.isEmpty());
    }

    // This test is for making sure export still exports the undeleted data correctly
    @Test
    public void deleteOnlyOneOncHistory() throws Exception {
        String participantGuid = guid0;
        ParticipantDto participantDto = oncHistoryTestUtil.createParticipant(participantGuid, ddpInstanceDto);
        participantGuid = participantDto.getDdpParticipantIdOrThrow();
        int participantId = participantDto.getParticipantIdOrThrow();
        OncHistoryDetail oncHistoryDetail = oncHistoryTestUtil.createOncHistoryAndParseResponse(participantGuid, participantId,
                ddpInstanceDto);
        int oncHistoryDetailId = oncHistoryDetail.getOncHistoryDetailId();
        OncHistoryDetail oncHistoryDetail2 = oncHistoryTestUtil.createOncHistoryAndParseResponse(participantGuid, participantId,
                ddpInstanceDto);
        int oncHistoryDetailId2 = oncHistoryDetail2.getOncHistoryDetailId();
        remainingOncHistoryDetailId = oncHistoryDetailId2;
        Assert.assertNotEquals(oncHistoryDetailId2, oncHistoryDetailId);
        Assert.assertNotNull(oncHistoryDetail2);
        //only delete one of the onc histories first
        oncHistoryDetailDao.delete(oncHistoryDetailId);
        Assert.assertNull(OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName()));
        //making sure second one is not deleted
        Assert.assertNotNull(OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId2, ddpInstanceDto.getInstanceName()));
        ExportToES.ExportPayload exportPayload = new ExportToES.ExportPayload();
        exportPayload.setStudy(instanceName);
        exportPayload.setIsMigration(true);
        DSMtasksSubscription.migrateToES(exportPayload);
        Map<String, Object> esDsmMap = ElasticSearchUtil.getObjectsMap(ddpInstanceDto.getEsParticipantIndex(), participantGuid,
                ESObjectConstants.DSM);
        List<Map<String, Object>> oncHistoryDetails =
                (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM))
                        .get(ESObjectConstants.ONC_HISTORY_DETAIL);
        Assert.assertEquals(1, oncHistoryDetails.size());
        Assert.assertEquals(oncHistoryDetailId2, oncHistoryDetails.get(0).get("oncHistoryDetailId"));
    }

    @Test
    public void getAllParticipantIdsFromIndexTest() {
        ElasticSearchService elasticSearchService = new ElasticSearchService();
        List<String> participantsInTheStudy = elasticSearchService.getAllParticipantGuids(ddpInstanceDto.getEsParticipantIndex());
        int size = participantsInTheStudy.size();
        ParticipantDto participantDto1 = oncHistoryTestUtil.createParticipant(guid1, ddpInstanceDto);
        ParticipantDto participantDto2 = oncHistoryTestUtil.createParticipant(guid2, ddpInstanceDto);
        ParticipantDto participantDto3 = oncHistoryTestUtil.createParticipant(guid3, ddpInstanceDto);
        participantsInTheStudy = elasticSearchService.getAllParticipantGuids(ddpInstanceDto.getEsParticipantIndex());
        Assert.assertEquals(3, participantsInTheStudy.size() - size);
        Assert.assertTrue(participantsInTheStudy.contains(participantDto1.getDdpParticipantIdOrThrow()));
        Assert.assertTrue(participantsInTheStudy.contains(participantDto2.getDdpParticipantIdOrThrow()));
        Assert.assertTrue(participantsInTheStudy.contains(participantDto3.getDdpParticipantIdOrThrow()));
    }
}
