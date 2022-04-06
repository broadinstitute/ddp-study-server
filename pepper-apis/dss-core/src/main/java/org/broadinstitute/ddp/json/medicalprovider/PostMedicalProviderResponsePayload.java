package org.broadinstitute.ddp.json.medicalprovider;

import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;

@Value
@AllArgsConstructor
public class PostMedicalProviderResponsePayload {
    @SerializedName("medicalProviderGuid")
    String medicalProviderGuid;

    public PostMedicalProviderResponsePayload(final MedicalProviderDto dto) {
        this(dto.getUserMedicalProviderGuid());
    }
}
