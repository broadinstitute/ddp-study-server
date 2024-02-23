package org.broadinstitute.dsm.service.adminoperation;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.broadinstitute.dsm.util.ParticipantDataTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ParticipantInitServiceTest extends DbAndElasticBaseTest {
    private static final String TEST_USER = "TEST_USER";
    private static final String instanceName = "rgpinit";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstance ddpInstance;
    private static final ParticipantDataDao dataDao = new ParticipantDataDao();
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static final List<Integer> fieldSettingsIds = new ArrayList<>();
    private static final Gson gson = new Gson();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
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
        ParticipantDataDao participantDataDao = new ParticipantDataDao();
        List<ParticipantData> participantDataList =
                participantDataDao.getParticipantDataByInstanceId(ddpInstanceDto.getDdpInstanceId());
        participantDataList.forEach(participantData ->
                participantDataDao.delete(participantData.getParticipantDataId()));

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow()));
        participants.clear();
        fieldSettingsIds.forEach(FieldSettingsTestUtil::deleteFieldSettings);
        fieldSettingsIds.clear();
    }

    @Test
    public void testInitParticipant() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        ElasticTestUtil.addParticipantActivities(esIndex, ParticipantDataTestUtil.getRgpActivities(), ddpParticipantId);

        ParticipantInitService.initParticipant()
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto, esIndex);
        participants.add(participant);
        return participant;
    }

}
