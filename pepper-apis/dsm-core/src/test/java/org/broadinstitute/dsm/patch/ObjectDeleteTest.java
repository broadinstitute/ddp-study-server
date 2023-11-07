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
import org.broadinstitute.dsm.util.DSMOncHistoryCreatorUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ObjectDeleteTest extends DbAndElasticBaseTest {
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "patch_instance";
    private static final String groupName = "patch_group";
    static DSMOncHistoryCreatorUtil dsmOncHistoryCreatorUtil;
    static String userEmail = "patchTestUser1@unittest.dev";
    static String guid = "PATCH_TEST_PARTICIPANT";
    static String guid2 = "PATCH_PARTICIPANT";
    static String guid3 = "PATCH2_PARTICIPANT";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static ParticipantDto testParticipant = null;

    @BeforeClass
    public static void doFirst() {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/lmsMappings.json", null);
        dsmOncHistoryCreatorUtil = new DSMOncHistoryCreatorUtil(instanceName, instanceName, userEmail, groupName, "lmsPrefix", esIndex);
        dsmOncHistoryCreatorUtil.initialize();
        ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(instanceName).orElseThrow();
    }

    @AfterClass
    public static void cleanUpAfter() {
        dsmOncHistoryCreatorUtil.deleteEverything();
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
            ParticipantDto ParticipantDto = dsmOncHistoryCreatorUtil.createParticipant(guid, ddpInstanceDto);
            guid = ParticipantDto.getDdpParticipantIdOrThrow();
            int participantId = ParticipantDto.getParticipantIdOrThrow();
            Map<String, Object> response =
                    (Map<String, Object>) dsmOncHistoryCreatorUtil.createOncHistory(ParticipantDto.getDdpParticipantIdOrThrow(),
                            participantId, instanceName, userEmail);
            int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
            OncHistoryDetail oncHistoryDetail =
                    OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
            Assert.assertNotNull(oncHistoryDetail);
            dsmOncHistoryCreatorUtil.deleteOncHistory(ParticipantDto.getDdpParticipantIdOrThrow(), participantId, instanceName, userEmail,
                    oncHistoryDetailId);
            dsmOncHistoryCreatorUtil.assertOncHistoryIsDeleted(guid, oncHistoryDetail, oncHistoryDetailId, ddpInstanceDto, true, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void deleteOncHistoryWithTissue() {
        try {
            ParticipantDto ParticipantDto = dsmOncHistoryCreatorUtil.createParticipant(guid2, ddpInstanceDto);
            guid2 = ParticipantDto.getDdpParticipantIdOrThrow();
            int participantId = ParticipantDto.getParticipantIdOrThrow();
            Map<String, Object> response =
                    (Map<String, Object>) dsmOncHistoryCreatorUtil.createOncHistory(ParticipantDto.getDdpParticipantIdOrThrow(),
                            participantId, instanceName, userEmail);
            int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
            OncHistoryDetail oncHistoryDetail =
                    OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
            Assert.assertNotNull(oncHistoryDetail);
            response = (Map<String, Object>) dsmOncHistoryCreatorUtil.createTissue(guid2, oncHistoryDetailId, instanceName, userEmail);

            int tissueId = Integer.parseInt((String) response.get("tissueId"));
            Optional<Tissue> maybeCreatedTissue = new TissueDao().get((long)tissueId);
            Assert.assertTrue(maybeCreatedTissue.isPresent());
            Tissue createdTissue = maybeCreatedTissue.get();
            dsmOncHistoryCreatorUtil.deleteOncHistory(ParticipantDto.getDdpParticipantIdOrThrow(), participantId, instanceName, userEmail,
                    oncHistoryDetailId);
            dsmOncHistoryCreatorUtil.assertTissueIsDeleted(guid2, createdTissue, tissueId, ddpInstanceDto, true);
            dsmOncHistoryCreatorUtil.assertOncHistoryIsDeleted(guid2, oncHistoryDetail, oncHistoryDetailId, ddpInstanceDto, false, true);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void deleteOncHistoryWithTissueAndSmId() {
        try {
            ParticipantDto ParticipantDto = dsmOncHistoryCreatorUtil.createParticipant(guid3, ddpInstanceDto);
            guid3 = ParticipantDto.getDdpParticipantIdOrThrow();
            int participantId = ParticipantDto.getParticipantIdOrThrow();
            Map<String, Object> response =
                    (Map<String, Object>) dsmOncHistoryCreatorUtil.createOncHistory(ParticipantDto.getDdpParticipantIdOrThrow(),
                            participantId, instanceName, userEmail);
            int oncHistoryDetailId = Integer.parseInt((String) response.get("oncHistoryDetailId"));
            OncHistoryDetail oncHistoryDetail =
                    OncHistoryDetail.getOncHistoryDetail(oncHistoryDetailId, ddpInstanceDto.getInstanceName());
            Assert.assertNotNull(oncHistoryDetail);
            response = (Map<String, Object>) dsmOncHistoryCreatorUtil.createTissue(guid3, oncHistoryDetailId, instanceName, userEmail);
            int tissueId = Integer.parseInt((String) response.get("tissueId"));

            response = (Map<String, Object>) dsmOncHistoryCreatorUtil.createSmId(guid3, tissueId, instanceName, userEmail);
            int smIdPk = (int) response.get("smIdPk");
            SmId smId = new TissueSMIDDao().get(smIdPk);
            Optional<Tissue> maybeCreatedTissue = new TissueDao().get((long)tissueId);
            Assert.assertTrue(maybeCreatedTissue.isPresent());
            Tissue createdTissue = maybeCreatedTissue.get();

            dsmOncHistoryCreatorUtil.deleteOncHistory(ParticipantDto.getDdpParticipantIdOrThrow(), participantId, instanceName, userEmail,
                    oncHistoryDetailId);
            dsmOncHistoryCreatorUtil.assertSmIdIsDeleted(guid3, tissueId, smIdPk, ddpInstanceDto, smId);
            dsmOncHistoryCreatorUtil.assertTissueIsDeleted(guid3, createdTissue, tissueId, ddpInstanceDto, false);
            dsmOncHistoryCreatorUtil.assertOncHistoryIsDeleted(guid3, oncHistoryDetail, oncHistoryDetailId, ddpInstanceDto, false, false);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
