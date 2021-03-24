package org.broadinstitute.dsm.model.participant.data;


import java.util.Optional;
import java.util.OptionalInt;

import lombok.Setter;

@Setter
public class AddFamilyMemberPayload {

    private String participantGuid;
    private String realm;
    private FamilyMemberDetails data;
    private Integer userId;
    private Boolean copyProbandInfo;
    private int probandDataId;

    public AddFamilyMemberPayload(String participantGuid, String realm, FamilyMemberDetails data, Integer userId,
                                  Boolean copyProbandInfo, int probandDataId) {
        this.participantGuid = participantGuid;
        this.realm = realm;
        this.data = data;
        this.userId = userId;
        this.copyProbandInfo = copyProbandInfo;
        this.probandDataId = probandDataId;
    }

    public Optional<String> getParticipantGuid() {
        return Optional.ofNullable(participantGuid);
    }

    public Optional<String> getRealm() {
        return Optional.ofNullable(realm);
    }

    public Optional<FamilyMemberDetails> getData() {
        return Optional.ofNullable(data);
    }

    public Optional<Integer> getUserId() {
        return Optional.ofNullable(userId);
    }

    public Optional<Boolean> getCopyProbandInfo() { return Optional.ofNullable(copyProbandInfo); }

    public OptionalInt getProbandDataId() { return OptionalInt.of(probandDataId); }
}
