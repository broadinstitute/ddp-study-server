package org.broadinstitute.dsm;

import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDataDto;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class ParticipantTest {

    List<ParticipantDataDto> participantDatas;
    List<ParticipantDataDto> newParticipantDatas;

    @Before
    public void initializeParticipantDatas() {
        participantDatas = List.of(
                new ParticipantDataDto.Builder()
                        .withDdpParticipantId("testId")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"DATSTAT_ALTPID\":\"testId\", \"COLLABORATOR_PARTICIPANT_ID\":\"id1\", \"DATSTAT_ALTEMAIL\":\"email\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build(),
                new ParticipantDataDto.Builder()
                        .withDdpParticipantId("testId2")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"COLLABORATOR_PARTICIPANT_ID\":\"id2\", \"DATSTAT_ALTEMAIL\":\"email\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build(),
                new ParticipantDataDto.Builder()
                        .withDdpParticipantId("testId3")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"COLLABORATOR_PARTICIPANT_ID\":\"id3\", \"DATSTAT_ALTEMAIL\":\"email1\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build()
        );

        newParticipantDatas = List.of(
                new ParticipantDataDto.Builder()
                        .withDdpParticipantId("testId")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"IS_APPLICANT\":\"true\", \"COLLABORATOR_PARTICIPANT_ID\":\"id1\", \"DATSTAT_ALTEMAIL\":\"email\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build(),
                new ParticipantDataDto.Builder()
                        .withDdpParticipantId("testId2")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"COLLABORATOR_PARTICIPANT_ID\":\"id2\", \"DATSTAT_ALTEMAIL\":\"email\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build(),
                new ParticipantDataDto.Builder()
                        .withDdpParticipantId("testId3")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"COLLABORATOR_PARTICIPANT_ID\":\"id3\", \"DATSTAT_ALTEMAIL\":\"email1\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build()
        );
    }

    @Test
    public void checkApplicant() {
        String collaboratorParticipantId1 = "id1";

        Assert.assertTrue(ParticipantUtil.matchesApplicantEmail(collaboratorParticipantId1, participantDatas));
    }

    @Test
    public void checkMemberWithSameEmail() {
        String collaboratorParticipantId2 = "id2";

        Assert.assertTrue(ParticipantUtil.matchesApplicantEmail(collaboratorParticipantId2, participantDatas));
    }

    @Test
    public void checkMemberWithDifferentEmail() {
        String collaboratorParticipantId3 = "id3";

        Assert.assertFalse(ParticipantUtil.matchesApplicantEmail(collaboratorParticipantId3, participantDatas));
    }

    @Test
    public void checkNewApplicant() {
        String collaboratorParticipantId1 = "id1";

        Assert.assertTrue(ParticipantUtil.matchesApplicantEmail(collaboratorParticipantId1, newParticipantDatas));
    }

    @Test
    public void checkNewMemberWithSameEmail() {
        String collaboratorParticipantId2 = "id2";

        Assert.assertTrue(ParticipantUtil.matchesApplicantEmail(collaboratorParticipantId2, newParticipantDatas));
    }

    @Test
    public void checkNewMemberWithDifferentEmail() {
        String collaboratorParticipantId3 = "id3";

        Assert.assertFalse(ParticipantUtil.matchesApplicantEmail(collaboratorParticipantId3, newParticipantDatas));
    }

}
