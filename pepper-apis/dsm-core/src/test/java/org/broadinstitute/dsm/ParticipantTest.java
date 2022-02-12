package org.broadinstitute.dsm;

import java.util.List;

import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ParticipantTest {

    List<ParticipantData> participantDatas;
    List<ParticipantData> newParticipantDatas;

    @Before
    public void initializeParticipantDatas() {
        participantDatas = List.of(
                new ParticipantData.Builder()
                        .withDdpParticipantId("testId")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData(
                                "{\"DATSTAT_ALTPID\":\"testId\", \"COLLABORATOR_PARTICIPANT_ID\":\"id1\", \"DATSTAT_ALTEMAIL\":\"email\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build(),
                new ParticipantData.Builder()
                        .withDdpParticipantId("testId2")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"COLLABORATOR_PARTICIPANT_ID\":\"id2\", \"DATSTAT_ALTEMAIL\":\"email\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build(),
                new ParticipantData.Builder()
                        .withDdpParticipantId("testId3")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"COLLABORATOR_PARTICIPANT_ID\":\"id3\", \"DATSTAT_ALTEMAIL\":\"email1\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build()
        );

        newParticipantDatas = List.of(
                new ParticipantData.Builder()
                        .withDdpParticipantId("testId")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"IS_APPLICANT\":\"true\", \"COLLABORATOR_PARTICIPANT_ID\":\"id1\", \"DATSTAT_ALTEMAIL\":\"email\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build(),
                new ParticipantData.Builder()
                        .withDdpParticipantId("testId2")
                        .withDdpInstanceId(19)
                        .withFieldTypeId("testFieldType")
                        .withData("{\"COLLABORATOR_PARTICIPANT_ID\":\"id2\", \"DATSTAT_ALTEMAIL\":\"email\"}")
                        .withLastChanged(0)
                        .withChangedBy(null)
                        .build(),
                new ParticipantData.Builder()
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
