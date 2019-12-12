package org.broadinstitute.ddp.json.medicalprovider;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.db.dto.MedicalProviderDto;

public class GetMedicalProviderResponse {

    @SerializedName("medicalProviderGuid")
    private String medicalProviderGuid;
    @SerializedName("physicianName")
    private String physicianName;
    @SerializedName("institutionName")
    private String institutionName;
    @SerializedName("city")
    private String city;
    @SerializedName("state")
    private String state;

    public GetMedicalProviderResponse(MedicalProviderDto dto) {
        this.medicalProviderGuid = dto.getUserMedicalProviderGuid();
        this.physicianName = dto.getPhysicianName();
        this.institutionName = dto.getInstitutionName();
        this.city = dto.getCity();
        this.state = dto.getState();
    }

    public String getMedicalProviderGuid() {
        return medicalProviderGuid;
    }

    public String getPhysicianName() {
        return physicianName;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

}
