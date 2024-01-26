package org.broadinstitute.dsm.patch;

import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.OncHistoryTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ObjectDeleteTest extends DbAndElasticBaseTest {
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "patch_instance";
    private static final String groupName = "patch_group";
    static OncHistoryTestUtil oncHistoryTestUtil;
    static String userEmail = "patchTestUser1@unittest.dev";
    static String adminUserEmail = "patchTestAdmin@unittest.dev";
    static String guid = "PATCH1_PARTICIPANT";
    static String guid2 = "PATCH2_PARTICIPANT";
    static String guid3 = "PATCH3_PARTICIPANT";
    static String guid4 = "PATCH4_PARTICIPANT";
    static String guid5 = "PATCH5_PARTICIPANT";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static ParticipantDto testParticipant = null;

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        oncHistoryTestUtil = new OncHistoryTestUtil(instanceName, instanceName, userEmail, adminUserEmail,
                groupName, "lmsPrefix", esIndex);
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
    public void deleteObject() {
        try {
            ParticipantDto participantDto = oncHistoryTestUtil.createParticipant(guid, ddpInstanceDto);
            guid = participantDto.getDdpParticipantIdOrThrow();
            int participantId = participantDto.getParticipantIdOrThrow();
            Map<String, Object> response =
                    (Map<String, Object>) oncHistoryTestUtil.createOncHistory(guid, participantId, instanceName, userEmail);
            int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
            OncHistoryDetail oncHistoryDetail =
                    OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
            Assert.assertNotNull(oncHistoryDetail);
            oncHistoryTestUtil.deleteOncHistory(guid, participantId, instanceName, userEmail,
                    oncHistoryDetailId);
            oncHistoryTestUtil.assertOncHistoryIsDeleted(guid, oncHistoryDetail, oncHistoryDetailId, ddpInstanceDto, true, true);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception ", e);
        }

    }

    @Test
    public void deleteOncHistoryWithTissue() {
        try {
            ParticipantDto participantDto = oncHistoryTestUtil.createParticipant(guid2, ddpInstanceDto);
            guid2 = participantDto.getDdpParticipantIdOrThrow();
            int participantId = participantDto.getParticipantIdOrThrow();
            Map<String, Object> response =
                    (Map<String, Object>) oncHistoryTestUtil.createOncHistory(guid2, participantId, instanceName, userEmail);
            int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
            OncHistoryDetail oncHistoryDetail =
                    OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
            Assert.assertNotNull(oncHistoryDetail);
            response = (Map<String, Object>) oncHistoryTestUtil.createTissue(guid2, oncHistoryDetailId, instanceName, userEmail);

            int tissueId = Integer.parseInt((String) response.get("tissueId"));
            Optional<Tissue> maybeCreatedTissue = new TissueDao().get((long) tissueId);
            Assert.assertTrue(maybeCreatedTissue.isPresent());
            Tissue createdTissue = maybeCreatedTissue.get();
            oncHistoryTestUtil.deleteOncHistory(guid2, participantId, instanceName, userEmail, oncHistoryDetailId);
            oncHistoryTestUtil.assertTissueIsDeleted(guid2, createdTissue, tissueId, ddpInstanceDto, true);
            oncHistoryTestUtil.assertOncHistoryIsDeleted(guid2, oncHistoryDetail, oncHistoryDetailId, ddpInstanceDto, false, true);

        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception ", e);
        }

    }

    @Test
    public void deleteOncHistoryWithTissueAndSmId() {
        try {
            ParticipantDto participantDto = oncHistoryTestUtil.createParticipant(guid3, ddpInstanceDto);
            guid3 = participantDto.getDdpParticipantIdOrThrow();
            int participantId = participantDto.getParticipantIdOrThrow();
            Map<String, Object> response =
                    (Map<String, Object>) oncHistoryTestUtil.createOncHistory(guid3, participantId, instanceName, userEmail);
            int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
            OncHistoryDetail oncHistoryDetail =
                    OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
            Assert.assertNotNull(oncHistoryDetail);
            response = (Map<String, Object>) oncHistoryTestUtil.createTissue(guid3, oncHistoryDetailId, instanceName, userEmail);
            int tissueId = Integer.parseInt((String) response.get("tissueId"));

            response = (Map<String, Object>) oncHistoryTestUtil.createSmId(guid3, tissueId, instanceName, userEmail, "value-test1");
            int smIdPk = (int) response.get("smIdPk");
            SmId smId = new TissueSMIDDao().getBySmIdPk(smIdPk);
            Optional<Tissue> maybeCreatedTissue = new TissueDao().get((long) tissueId);
            Assert.assertTrue(maybeCreatedTissue.isPresent());
            Tissue createdTissue = maybeCreatedTissue.get();

            oncHistoryTestUtil.deleteOncHistory(guid3, participantId, instanceName, userEmail, oncHistoryDetailId);
            oncHistoryTestUtil.assertSmIdIsDeleted(guid3, tissueId, smIdPk, ddpInstanceDto, smId);
            oncHistoryTestUtil.assertTissueIsDeleted(guid3, createdTissue, tissueId, ddpInstanceDto, false);
            oncHistoryTestUtil.assertOncHistoryIsDeleted(guid3, oncHistoryDetail, oncHistoryDetailId, ddpInstanceDto, false, false);

        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception ", e);
        }

    }

    @Test
    public void deleteJustTheSmId() {
        try {
            //create participant
            ParticipantDto participantDto = oncHistoryTestUtil.createParticipant(guid4, ddpInstanceDto);
            guid4 = participantDto.getDdpParticipantIdOrThrow();
            int participantId = participantDto.getParticipantIdOrThrow();
            //create oncHistoryDetailId
            Map<String, Object> response =
                    (Map<String, Object>) oncHistoryTestUtil.createOncHistory(guid4, participantId, instanceName, userEmail);
            int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
            OncHistoryDetail oncHistoryDetail =
                    OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
            Assert.assertNotNull(oncHistoryDetail);
            // create Tissue for Onc History
            response = (Map<String, Object>) oncHistoryTestUtil.createTissue(guid4, oncHistoryDetailId, instanceName, userEmail);
            int tissueId = Integer.parseInt((String) response.get("tissueId"));
            // create SM ID for the tissue
            response = (Map<String, Object>) oncHistoryTestUtil.createSmId(guid4, tissueId, instanceName, userEmail, "value1");
            int smIdPk = (int) response.get("smIdPk");
            SmId smId = new TissueSMIDDao().getBySmIdPk(smIdPk);
            response = (Map<String, Object>) oncHistoryTestUtil.createSmId(guid4, tissueId, instanceName, userEmail, "value2");
            int smIdPk2 = (int) response.get("smIdPk");
            SmId smId2 = new TissueSMIDDao().getBySmIdPk(smIdPk2);
            //delete only the first SMID
            oncHistoryTestUtil.deleteSMId(guid4, instanceName, userEmail, smIdPk, tissueId);
            //making sure only sm id got deleted
            oncHistoryTestUtil.assertSmIdIsDeleted(guid4, tissueId, smIdPk, ddpInstanceDto, smId);
            //make sure everything else is not deleted
            oncHistoryTestUtil.assertSmIdIsNOTDeleted(guid4, tissueId, smIdPk2, ddpInstanceDto, smId2);
            oncHistoryTestUtil.assertTissueIsNOTDeleted(guid4, tissueId, ddpInstanceDto, false);
            oncHistoryTestUtil.assertOncHistoryIsNOTDeleted(guid4, oncHistoryDetailId, 1L, 1L, ddpInstanceDto, false, false);
            //for the sake of clean up as well as testing delete, delete the rest and make sure they got deleted
            Optional<Tissue> maybeCreatedTissue = new TissueDao().get((long) tissueId);
            Assert.assertTrue(maybeCreatedTissue.isPresent());
            Tissue createdTissue = maybeCreatedTissue.get();
            oncHistoryTestUtil.deleteOncHistory(guid4, participantId, instanceName, userEmail, oncHistoryDetailId);
            oncHistoryTestUtil.assertSmIdIsDeleted(guid4, tissueId, smIdPk, ddpInstanceDto, smId);
            oncHistoryTestUtil.assertSmIdIsDeleted(guid4, tissueId, smIdPk2, ddpInstanceDto, smId2);
            oncHistoryTestUtil.assertTissueIsDeleted(guid4, createdTissue, tissueId, ddpInstanceDto, false);
            oncHistoryTestUtil.assertOncHistoryIsDeleted(guid4, oncHistoryDetail, oncHistoryDetailId, ddpInstanceDto, false, false);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception ", e);
        }
    }

    @Test
    public void deleteJustTheTissue() {
        try {
            //create participant
            ParticipantDto participantDto = oncHistoryTestUtil.createParticipant(guid5, ddpInstanceDto);
            guid5 = participantDto.getDdpParticipantIdOrThrow();
            int participantId = participantDto.getParticipantIdOrThrow();
            //create oncHistoryDetailId
            Map<String, Object> response =
                    (Map<String, Object>) oncHistoryTestUtil.createOncHistory(guid5, participantId, instanceName, userEmail);
            int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
            OncHistoryDetail oncHistoryDetail =
                    OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
            Assert.assertNotNull(oncHistoryDetail);
            // create Tissue for Onc History
            response = (Map<String, Object>) oncHistoryTestUtil.createTissue(guid5, oncHistoryDetailId, instanceName, userEmail);
            int tissueId = Integer.parseInt((String) response.get("tissueId"));
            Optional<Tissue> maybeCreatedTissue = new TissueDao().get((long) tissueId);
            Assert.assertTrue(maybeCreatedTissue.isPresent());
            Tissue createdTissue = maybeCreatedTissue.get();
            // create SM ID for the tissue
            response = (Map<String, Object>) oncHistoryTestUtil.createSmId(guid5, tissueId, instanceName, userEmail, "value4");
            int smIdPk = (int) response.get("smIdPk");
            SmId smId = new TissueSMIDDao().getBySmIdPk(smIdPk);
            response = (Map<String, Object>) oncHistoryTestUtil.createSmId(guid5, tissueId, instanceName, userEmail, "value5");
            int smIdPk2 = (int) response.get("smIdPk");
            SmId smId2 = new TissueSMIDDao().getBySmIdPk(smIdPk2);
            //delete only the first SMID
            oncHistoryTestUtil.deleteTissue(guid5, instanceName, userEmail, tissueId, oncHistoryDetailId);
            //making sure tissue and all of its sm ids got deleted
            oncHistoryTestUtil.assertSmIdIsDeleted(guid5, tissueId, smIdPk, ddpInstanceDto, smId);
            oncHistoryTestUtil.assertSmIdIsDeleted(guid5, tissueId, smIdPk2, ddpInstanceDto, smId2);
            oncHistoryTestUtil.assertTissueIsDeleted(guid5, createdTissue, tissueId, ddpInstanceDto, false);
            //make sure onc history is not deleted
            oncHistoryTestUtil.assertOncHistoryIsNOTDeleted(guid5, oncHistoryDetailId, 0L, 0L, ddpInstanceDto, false, false);
            //for the sake of clean up as well as testing delete, delete the rest and make sure they got deleted
            oncHistoryTestUtil.deleteOncHistory(guid5, participantId, instanceName, userEmail, oncHistoryDetailId);
            oncHistoryTestUtil.assertSmIdIsDeleted(guid5, tissueId, smIdPk, ddpInstanceDto, smId);
            oncHistoryTestUtil.assertSmIdIsDeleted(guid5, tissueId, smIdPk2, ddpInstanceDto, smId2);
            oncHistoryTestUtil.assertTissueIsDeleted(guid5, createdTissue, tissueId, ddpInstanceDto, false);
            oncHistoryTestUtil.assertOncHistoryIsDeleted(guid5, oncHistoryDetail, oncHistoryDetailId, ddpInstanceDto, false, false);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception ", e);
        }
    }

}
