package org.broadinstitute.dsm.service.participant;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.bookmark.Bookmark;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.CohortTagTestUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class OsteoParticipantServiceTest extends DbAndElasticBaseTest {
    private static final String instanceName = "osteoservice";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static CohortTagTestUtil cohortTagTestUtil;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/osteo1Mappings.json",
                "elastic/osteo1Settings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        cohortTagTestUtil = new CohortTagTestUtil();
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        cohortTagTestUtil.tearDown();

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow()));
        participants.clear();
    }

    @Test
    public void testCreateDefaultData() {
        String ddpParticipantId = createParticipant();

        ElasticSearchParticipantDto esParticipantDto =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);

        OsteoParticipantService osteoParticipantService = new OsteoParticipantService();
        osteoParticipantService.setOsteo2DefaultData(ddpParticipantId, esParticipantDto);
    }

    private String createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return ddpParticipantId;
    }
}
