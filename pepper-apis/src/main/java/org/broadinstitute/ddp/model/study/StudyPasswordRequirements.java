package org.broadinstitute.ddp.model.study;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.db.dto.StudyPasswordRequirementsDto;

public class StudyPasswordRequirements {
    @SerializedName("auth0TenantId")
    private transient long auth0TenantId;
    @SerializedName("minLength")
    private int minLength;
    @SerializedName("isUppercaseLetterRequired")
    private boolean isUppercaseLetterRequired;
    @SerializedName("isLowercaseLetterRequired")
    private boolean isLowercaseLetterRequired;
    @SerializedName("isSpecialCharacterRequired")
    private boolean isSpecialCharacterRequired;
    @SerializedName("isNumberRequired")
    private boolean isNumberRequired;
    @SerializedName("maxIdenticalConsecutiveCharacters")
    private Integer maxIdenticalConsecutiveCharacters;

    public StudyPasswordRequirements(StudyPasswordRequirementsDto dto) {
        this.auth0TenantId = dto.getAuth0TenantId();
        this.minLength = dto.getMinLength();
        this.isUppercaseLetterRequired = dto.isUppercaseLetterRequired();
        this.isLowercaseLetterRequired = dto.isLowercaseLetterRequired();
        this.isSpecialCharacterRequired = dto.isSpecialCharacterRequired();
        this.isNumberRequired = dto.isNumberRequired();
        this.maxIdenticalConsecutiveCharacters = dto.getMaxIdenticalConsecutiveCharacters();
    }
}


