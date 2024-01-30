package org.broadinstitute.dsm.service;

import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.AT_PARTICIPANT_EXIT;
import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.EXIT_STATUS;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues;
import org.broadinstitute.dsm.model.defaultvalues.ReferralSourceServiceTest;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.dsm.util.tools.FieldSettingsTestUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ParticipantDataFixupServiceTest extends DbAndElasticBaseTest {
    private static final String instanceName = "atdefault";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static final List<Integer> fieldSettingsIds = new ArrayList<>();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
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
    public void testSetDefaultValues() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getDdpParticipantIdOrThrow();

        int fieldSettingsId = FieldSettingsTestUtil.createRadioFieldSettings(AT_PARTICIPANT_EXIT,
                EXIT_STATUS,
                "[{\"value\":\"0\",\"name\":\"Not Exited\",\"default\":true},"
                        + "{\"value\":\"1\",\"name\":\"Exited\"}]",
                ddpInstanceDto.getDdpInstanceId());
        fieldSettingsIds.add(fieldSettingsId);

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

    private void updateESParticipantData(String ddpParticipantId, List<ParticipantData> participantDataList,
                                         DDPInstanceDto instance) {
        ElasticTestUtil.updateParticipantData(esIndex, ddpParticipantId, participantDataList, instance);
    }
}
