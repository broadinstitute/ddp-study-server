package org.broadinstitute.dsm.service.adminoperation;

import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.AT_PARTICIPANT_EXIT;
import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.GENOME_STUDY_FIELD_TYPE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues;
import org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.broadinstitute.dsm.util.ParticipantDataTestUtil;
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
        ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_MISCELLANEOUS", instanceId, TEST_USER);

        dataMap.clear();
        dataMap.put("ELIGIBILITY", "1");
        ParticipantDataTestUtil.createParticipantData(ddpParticipantId,
                dataMap, "AT_GROUP_ELIGIBILITY", instanceId, TEST_USER);

        List<ParticipantData> ptpData = dataDao.getParticipantData(ddpParticipantId);
        Assert.assertEquals(2, ptpData.size());
        WorkflowStatusUpdate.updateEsParticipantData(ddpParticipantId, ptpData, ddpInstance);

        ParticipantListRequest req = new ParticipantListRequest(List.of(ddpParticipantId));
        String reqJson = gson.toJson(req);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("fixupType", "atcpGenomicId");

        ParticipantDataFixupService fixupService = new ParticipantDataFixupService();
        // there is nothing to fixup in this run
        fixupService.validRealms = List.of(instanceName);
        fixupService.initialize(TEST_USER, instanceName, attributes, reqJson);
        UpdateLog updateLog = fixupService.updateParticipant(ddpParticipantId, ptpData);
        Assert.assertEquals(UpdateLog.UpdateStatus.NOT_UPDATED.name(), updateLog.getStatus());

        ATDefaultValues.insertGenomicIdForParticipant(ddpParticipantId, "GUID_1", instanceId);
        ATDefaultValues.insertExitStatusForParticipant(ddpParticipantId, instanceId);

        // now have the correct number of genomic IDs and exit statuses
        updateLog = fixupService.updateParticipant(ddpParticipantId,
                dataDao.getParticipantData(ddpParticipantId));
        Assert.assertEquals(UpdateLog.UpdateStatus.NOT_UPDATED.name(), updateLog.getStatus());
        verifyParticipantData(ddpParticipantId);

        ATDefaultValues.insertGenomicIdForParticipant(ddpParticipantId, "GUID_2", instanceId);
        ATDefaultValues.insertExitStatusForParticipant(ddpParticipantId, instanceId);

        // now have extra genomic IDs and exit statuses
        updateLog = fixupService.updateParticipant(ddpParticipantId,
                dataDao.getParticipantData(ddpParticipantId));
        Assert.assertEquals(UpdateLog.UpdateStatus.UPDATED.name(), updateLog.getStatus());
        verifyParticipantData(ddpParticipantId);
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        ParticipantDto participant = TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto, esIndex);
        participants.add(participant);
        return participant;
    }

    private void verifyParticipantData(String ddpParticipantId) {
        ParticipantDataDao dataDao = new ParticipantDataDao();
        List<ParticipantData> ptpDataList = dataDao.getParticipantData(ddpParticipantId);
        // 1 for exit status, 1 for genomic id, 2 for other data
        Assert.assertEquals(4, ptpDataList.size());

        Set<String> expectedFieldTypes =
                Set.of(AT_PARTICIPANT_EXIT, GENOME_STUDY_FIELD_TYPE, "AT_GROUP_MISCELLANEOUS", "AT_GROUP_ELIGIBILITY");
        Set<String> foundFieldTypes = ptpDataList.stream()
                .map(ParticipantData::getRequiredFieldTypeId).collect(Collectors.toSet());
        Assert.assertEquals(expectedFieldTypes, foundFieldTypes);
    }
}
