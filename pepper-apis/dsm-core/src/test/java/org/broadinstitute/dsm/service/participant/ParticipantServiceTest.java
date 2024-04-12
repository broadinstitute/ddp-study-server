package org.broadinstitute.dsm.service.participant;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ParticipantServiceTest extends DbAndElasticBaseTest {
    private static final String instanceName = "ptpservice";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstance ddpInstance;
    private static int participantCounter = 0;
    private static final ParticipantDao participantDao = new ParticipantDao();
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/osteo1Mappings.json",
                "elastic/osteo1Settings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        ddpInstance = DDPInstance.getDDPInstanceById(ddpInstanceDto.getDdpInstanceId());
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        TestParticipantUtil.deleteInstanceParticipants(ddpInstanceDto);
    }

    @Test
    public void testCreateAndUpdateParticipant() {
        String ddpParticipantId = TestParticipantUtil.createMinimalParticipant(ddpInstanceDto, participantCounter++);
        Dsm dsm = new Dsm();
        dsm.setHasConsentedToBloodDraw(true);
        ElasticTestUtil.addParticipantDsm(esIndex, dsm, ddpParticipantId);

        long lastVersion = 123;
        String lastVersionDate = "2024-03-01";

        try {
            ParticipantDto participantDto =
                    ParticipantService.createParticipant(ddpParticipantId, lastVersion, lastVersionDate, ddpInstance);
            verifyParticipant(participantDto, ddpInstanceDto, lastVersion, lastVersionDate);

            lastVersion = 124;
            lastVersionDate = "2024-03-02";
            ParticipantService.updateLastVersion(participantDto.getRequiredParticipantId(), lastVersion, lastVersionDate);
            verifyParticipant(participantDto, ddpInstanceDto, lastVersion, lastVersionDate);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
    }

    private void verifyParticipant(ParticipantDto participantDto, DDPInstanceDto ddpInstanceDto, long lastVersion,
                                   String lastVersionDate) {
        String ddpParticipantId = participantDto.getRequiredDdpParticipantId();
        String esIndex = ddpInstanceDto.getEsParticipantIndex();
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        Optional<Participant> ptp = dsm.getParticipant();
        Assert.assertTrue(ptp.isPresent());
        Participant participant = ptp.get();

        Assert.assertEquals(ddpParticipantId, participant.getDdpParticipantId());
        Assert.assertEquals(ddpInstanceDto.getDdpInstanceId(), participant.getDdpInstanceId());

        Optional<ParticipantDto> participantDto1 = participantDao.get(participantDto.getRequiredParticipantId());
        Assert.assertTrue(participantDto1.isPresent());
        ParticipantDto dbPtp = participantDto1.get();
        Assert.assertEquals(lastVersion, dbPtp.getLastVersion().get().longValue());
        Assert.assertEquals(lastVersionDate, dbPtp.getLastVersionDate().get());
    }
}
