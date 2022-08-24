package org.broadinstitute.ddp.json.medicalprovider;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;

@Value
@AllArgsConstructor
public class GetMedicalProviderResponse {
    @SerializedName("medicalProviderGuid")
    String medicalProviderGuid;
    
    @SerializedName("physicianName")
    String physicianName;
    
    @SerializedName("institutionName")
    String institutionName;
    
    @SerializedName("city")
    String city;
    
    @SerializedName("state")
    String state;

    @SerializedName("country")
    String country;

    public GetMedicalProviderResponse(final MedicalProviderDto dto) {
        this(dto.getUserMedicalProviderGuid(), dto.getPhysicianName(), dto.getInstitutionName(), dto.getCity(),
                dto.getState(), dto.getCountry());
    }
}
