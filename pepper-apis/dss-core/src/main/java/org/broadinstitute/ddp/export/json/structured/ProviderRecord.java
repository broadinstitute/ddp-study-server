package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

public class ProviderRecord {

    @SerializedName("guid")
    private String guid;
    @SerializedName("legacyGuid")
    private String legacyGuid;
    @SerializedName("type")
    private InstitutionType type;
    @SerializedName("physicianName")
    private String physicianName;
    @SerializedName("institutionName")
    private String institutionName;
    @SerializedName("city")
    private String city;
    @SerializedName("state")
    private String state;
    @SerializedName("postalCode")
    private String postalCode;
    @SerializedName("phone")
    private String phone;

    public ProviderRecord(MedicalProviderDto providerDto) {
        this(providerDto.getUserMedicalProviderGuid(),
                providerDto.getLegacyGuid(),
                providerDto.getInstitutionType(),
                providerDto.getPhysicianName(),
                providerDto.getInstitutionName(),
                providerDto.getCity(),
                providerDto.getState(),
                providerDto.getPostalCode(),
                providerDto.getPhone());
    }

    public ProviderRecord(String guid, String legacyGuid, InstitutionType type, String physicianName,
                          String institutionName, String city, String state, String postalCode, String phone) {
        this.guid = guid;
        this.legacyGuid = legacyGuid;
        this.type = type;
        this.physicianName = physicianName;
        this.institutionName = institutionName;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.phone = phone;
    }
}
