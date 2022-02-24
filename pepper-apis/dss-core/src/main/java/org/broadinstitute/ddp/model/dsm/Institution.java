package org.broadinstitute.ddp.model.dsm;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

public class Institution {

    @SerializedName("institution")
    private String institutionName;

    @SerializedName("physician")
    private String physicianName;

    @SerializedName("streetAddress")
    private String streetAddress;

    @SerializedName("state")
    private String state;

    @SerializedName("city")
    private String city;

    @SerializedName("id")
    private String institutionId;

    @SerializedName("type")
    private InstitutionType institutionType;

    public Institution(MedicalProviderDto dto) {
        this.institutionName = dto.getInstitutionName();
        this.physicianName = dto.getPhysicianName();
        this.state = dto.getState();
        this.city = dto.getCity();
        this.institutionType = dto.getInstitutionType();
        if (StringUtils.isEmpty(dto.getLegacyGuid())) {
            this.institutionId = dto.getUserMedicalProviderGuid();
        } else {
            this.institutionId = dto.getLegacyGuid();
        }
    }

    public Institution(String institutionName,
                       String physicianName,
                       String streetAddress,
                       String state,
                       String city,
                       String institutionId,
                       InstitutionType institutionType) {
        this.institutionName = institutionName;
        this.physicianName = physicianName;
        this.streetAddress = streetAddress;
        this.state = state;
        this.city = city;
        this.institutionId = institutionId;
        this.institutionType = institutionType;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getPhysicianName() {
        return physicianName;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public String getState() {
        return state;
    }

    public String getCity() {
        return city;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }
}
