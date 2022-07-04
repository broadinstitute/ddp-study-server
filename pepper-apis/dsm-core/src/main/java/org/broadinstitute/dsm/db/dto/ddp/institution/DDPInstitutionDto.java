package org.broadinstitute.dsm.db.dto.ddp.institution;

import lombok.Data;

@Data
public class DDPInstitutionDto implements Cloneable {

    private Integer institutionId;
    private String ddpInstitutionId;
    private String type;
    private Integer participantId;
    private Long lastChanged;

    private DDPInstitutionDto(Builder builder) {
        this.institutionId = builder.institutionId;
        this.ddpInstitutionId = builder.ddpInstitutionId;
        this.type = builder.type;
        this.participantId = builder.participantId;
        this.lastChanged = builder.lastChanged;
    }

    @Override
    public DDPInstitutionDto clone() {
        try {
            return (DDPInstitutionDto) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public static class Builder {

        Integer institutionId;
        String ddpInstitutionId;
        String type;
        Integer participantId;
        Long lastChanged;

        public Builder withInstitutionId(int institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withDdpInstitutionId(String ddpInstitutionId) {
            this.ddpInstitutionId = ddpInstitutionId;
            return this;
        }

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Builder withParticipantId(Integer participantId) {
            this.participantId = participantId;
            return this;
        }

        public Builder withLastChanged(long lastChanged) {
            this.lastChanged = lastChanged;
            return this;
        }

        public DDPInstitutionDto build() {
            return new DDPInstitutionDto(this);
        }
    }

}
