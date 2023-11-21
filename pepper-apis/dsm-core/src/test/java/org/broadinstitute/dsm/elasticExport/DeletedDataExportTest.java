package org.broadinstitute.dsm.elasticExport;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.export.ExportToES;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.pubsub.DSMtasksSubscription;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.OncHistoryTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DeletedDataExportTest extends DbAndElasticBaseTest {
    private static final String instanceName = "delete_instance";
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String groupName = "delete_group";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static ParticipantDto testParticipant = null;
    static OncHistoryTestUtil oncHistoryTestUtil;
    static String userEmail = "deleteTestUser1@unittest.dev";
    String guid = "DELETE_PARTICIPANT";
    String guid1 = "DELETE1_PARTICIPANT";
    String guid2 = "DELETE2_PARTICIPANT";
    String guid3 = "DELETE3_PARTICIPANT";

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        oncHistoryTestUtil = new OncHistoryTestUtil(instanceName, instanceName, userEmail, groupName, "lmsPrefix", esIndex);
        oncHistoryTestUtil.initialize();
        ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow();
    }

    @AfterClass
    public static void cleanUpAfter() {
        oncHistoryTestUtil.deleteEverything();
        try {
            ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
        ElasticTestUtil.deleteIndex(esIndex);
    }


    @Test
    public void deleteFromDbAndRunExport() throws Exception {
        ParticipantDto participantDto = oncHistoryTestUtil.createParticipant(guid, ddpInstanceDto);
        guid = participantDto.getDdpParticipantIdOrThrow();
        int participantId = participantDto.getParticipantIdOrThrow();
        Map<String, Object> response =
                (Map<String, Object>) oncHistoryTestUtil.createOncHistory(guid, participantId, instanceName, userEmail);
        int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
        OncHistoryDetail oncHistoryDetail =
                OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
        Assert.assertNotNull(oncHistoryDetail);
        oncHistoryTestUtil.deleteOncHistoryDirectlyFromDB(oncHistoryDetailId);
        OncHistoryDetail deletedOncHistoryDetail =
                OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
        Assert.assertNull(deletedOncHistoryDetail);
        ExportToES.ExportPayload exportPayload = new ExportToES.ExportPayload();
        exportPayload.setStudy(instanceName);
        exportPayload.setIsMigration(true);
        DSMtasksSubscription.migrateToES(exportPayload);
        Map<String, Object> esDsmMap = ElasticSearchUtil.getObjectsMap(ddpInstanceDto.getEsParticipantIndex(), guid, ESObjectConstants.DSM);
        List<Map<String, Object>> oncHistoryDetails =
                (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM)).get(ESObjectConstants.ONC_HISTORY_DETAIL);
        long countNumberOfOncHistoriesInEs = oncHistoryDetails.stream().filter(stringObjectMap ->
                (int) stringObjectMap.get("oncHistoryDetailId") == oncHistoryDetailId).count();
        Assert.assertEquals(0L, countNumberOfOncHistoriesInEs);// Currently the test is failing here because the value is still in ES
    }

    @Test
    public void getAllParticipantIdsFromIndexTest() {
        List<String> participantsInTheStudy = new ElasticSearch().getAllParticipantsInIndex(ddpInstanceDto.getEsParticipantIndex());
        int size = participantsInTheStudy.size();
        ParticipantDto participantDto1 = oncHistoryTestUtil.createParticipant(guid1, ddpInstanceDto);
        ParticipantDto participantDto2 = oncHistoryTestUtil.createParticipant(guid2, ddpInstanceDto);
        ParticipantDto participantDto3 = oncHistoryTestUtil.createParticipant(guid3, ddpInstanceDto);
        participantsInTheStudy = new ElasticSearch().getAllParticipantsInIndex(ddpInstanceDto.getEsParticipantIndex());
        Assert.assertEquals(3, participantsInTheStudy.size() - size);
        Assert.assertTrue(participantsInTheStudy.contains(participantDto1.getDdpParticipantIdOrThrow()));
        Assert.assertTrue(participantsInTheStudy.contains(participantDto2.getDdpParticipantIdOrThrow()));
        Assert.assertTrue(participantsInTheStudy.contains(participantDto3.getDdpParticipantIdOrThrow()));
    }
}
