package org.broadinstitute.ddp.json.medicalprovider;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.db.dto.MedicalProviderDto;

public class PostMedicalProviderResponsePayload {

    @SerializedName("medicalProviderGuid")
    private String medicalProviderGuid;

    public PostMedicalProviderResponsePayload(MedicalProviderDto dto) {
        this.medicalProviderGuid = dto.getUserMedicalProviderGuid();
    }

    public String getMedicalProviderGuid() {
        return medicalProviderGuid;
    }

}
