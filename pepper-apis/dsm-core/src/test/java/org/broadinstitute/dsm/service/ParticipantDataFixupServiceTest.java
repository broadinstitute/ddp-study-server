package org.broadinstitute.dsm.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ParticipantDataFixupServiceTest extends DbAndElasticBaseTest {
    private static final String TEST_USER = "TEST_USER";
    private static final String instanceName = "atdefault";
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
    public void testFixupGenomicId() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getDdpParticipantIdOrThrow();
        int instanceId = ddpInstanceDto.getDdpInstanceId();

        int fieldSettingsId = FieldSettingsTestUtil.createExitStatusFieldSetting(ddpInstanceDto.getDdpInstanceId());
        fieldSettingsIds.add(fieldSettingsId);

        // create ptp data that is neither genomic id nor exit status
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("REGISTRATION_TYPE", "Self");
        TestParticipantUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_MISCELLANEOUS", instanceId, TEST_USER);

        dataMap.clear();
        dataMap.put("ELIGIBILITY", "1");
        TestParticipantUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_ELIGIBILITY", instanceId, TEST_USER);

        List<ParticipantData> ptpData = dataDao.getParticipantDataByParticipantId(ddpParticipantId);
        Assert.assertEquals(2, ptpData.size());
        ATDefaultValues.updateEsParticipantData(ddpParticipantId, ptpData, ddpInstance);

        ParticipantDataFixupService fixupService = new ParticipantDataFixupService();
        ParticipantDataFixupService.ParticipantListRequest req =
                new ParticipantDataFixupService.ParticipantListRequest();
        req.setParticipants(List.of(ddpParticipantId));
        String reqJson = gson.toJson(req);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("fixupType", "atcpGenomicId");

        fixupService.validRealms = List.of(instanceName);
        fixupService.initialize(TEST_USER, instanceName, attributes, reqJson);
        fixupService.run(42);
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        participants.add(participant);

        ElasticTestUtil.createParticipant(esIndex, participant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return participant;
    }
}
