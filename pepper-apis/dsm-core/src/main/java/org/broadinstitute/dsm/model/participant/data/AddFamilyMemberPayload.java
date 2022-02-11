package org.broadinstitute.dsm.model.participant.data;


import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.google.gson.Gson;
import lombok.Setter;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.bookmark.Bookmark;

@Setter
public class AddFamilyMemberPayload {

    private String participantId;
    private String realm;
    private FamilyMemberDetails data;
    private Integer userId;
    private Boolean copyProbandInfo;
    private int probandDataId;

    private AddFamilyMemberPayload(Builder builder) {
        this.participantId = builder.participantId;
        this.realm = builder.realm;
        this.data = builder.data;
        this.userId = builder.userId;
        this.copyProbandInfo = builder.copyProbandInfo;
        this.probandDataId = builder.probandDataId;
    }
    
    public static class Builder {
        private String participantId;
        private String realm;
        private FamilyMemberDetails data;
        private Integer userId;
        private Boolean copyProbandInfo;
        private int probandDataId;
        
        public Builder(String participantId, String realm) {
            this.participantId = participantId;
            this.realm = realm;
        }
        
        public Builder withData(FamilyMemberDetails data) {
            this.data = data;
            return this;
        }
        
        public Builder withUserId(Integer userId) {
            this.userId = userId;
            return this;
        }

        public Builder withCopyProbandInfo(Boolean copyProbandInfo) {
            this.copyProbandInfo = copyProbandInfo;
            return this;
        }

        public Builder withProbandDataId(int probandDataId) {
            this.probandDataId = probandDataId;
            return this;
        }

        public AddFamilyMemberPayload build() {
            return new AddFamilyMemberPayload(this);
        }
    }

    public Optional<String> getParticipantId() {
        return Optional.ofNullable(participantId);
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

    public String generateCollaboratorParticipantId() {
        if (Objects.isNull(this.data)) throw new NullPointerException("field data[FamilyMemberDetails] is null");
        DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
        String collaboratorIdPrefix = ddpInstanceDao.getCollaboratorIdPrefixByStudyGuid(this.realm).orElseThrow();
        return collaboratorIdPrefix +
                "_" +
                getOrGenerateFamilyId() +
                "_" +
                this.data.getSubjectId();
    }

    public long getFamilyId(List<ParticipantData> participantData) throws NoSuchFieldException {
        String familyId = null;
        for (ParticipantData pDataDto: Objects.requireNonNull(participantData)) {
            Map<String, String> pDataMap = new Gson().fromJson(pDataDto.getData().orElse(""), Map.class);
            familyId = pDataMap.get(FamilyMemberConstants.FAMILY_ID);
            if (org.apache.commons.lang3.StringUtils.isNumeric(familyId)) break;
        }
        if (Objects.isNull(familyId)) throw new NoSuchFieldException("could not find family id");
        return Long.parseLong(familyId);
    }

    public long getOrGenerateFamilyId() {
        org.broadinstitute.dsm.model.participant.data.ParticipantData participantData = new org.broadinstitute.dsm.model.participant.data.ParticipantData();
        List<ParticipantData> participantDataByParticipantId = participantData.getParticipantDataByParticipantId(this.participantId);
        long familyId;
        try {
            familyId = getFamilyId(participantDataByParticipantId);
        } catch (NoSuchFieldException e) {
            familyId = new Bookmark().getBookmarkFamilyIdAndUpdate(this.realm);
        }
        return familyId;
    }

}
