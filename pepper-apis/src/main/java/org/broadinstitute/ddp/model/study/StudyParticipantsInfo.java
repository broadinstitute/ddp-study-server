package org.broadinstitute.ddp.model.study;

import java.util.List;

public class StudyParticipantsInfo {
    private List<ParticipantInfo> participantInfoList;

    public StudyParticipantsInfo(List<ParticipantInfo> participantInfoList) {
        this.participantInfoList = participantInfoList;
    }

    public List<ParticipantInfo> getParticipantInfoList() {
        return participantInfoList;
    }
}
