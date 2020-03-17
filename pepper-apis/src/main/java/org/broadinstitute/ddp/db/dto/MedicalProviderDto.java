package org.broadinstitute.ddp.db.dto;

import org.apache.commons.lang3.StringUtils;

import org.broadinstitute.ddp.json.medicalprovider.PostPatchMedicalProviderRequestPayload;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class MedicalProviderDto {

    private Long userMedicalProviderId;
    private String userMedicalProviderGuid;
    private long userId;
    private long umbrellaStudyId;
    private InstitutionType institutionType;
    private String institutionName;
    private String physicianName;
    private String city;
    private String state;
    private String postalCode;
    private String phone;
    private String legacyGuid;
    private String street;

    @JdbiConstructor
    public MedicalProviderDto(
            Long userMedicalProviderId,
            String userMedicalProviderGuid,
            Long userId,
            Long umbrellaStudyId,
            @ColumnName("institution_type_code") InstitutionType institutionType,
            String institutionName,
            String physicianName,
            String city,
            String state,
            String postalCode,
            String phone,
            String legacyGuid,
            String street
    ) {
        this.userMedicalProviderId = userMedicalProviderId;
        this.userMedicalProviderGuid = userMedicalProviderGuid;
        this.userId = userId;
        this.umbrellaStudyId = umbrellaStudyId;
        this.institutionType = institutionType;
        this.institutionName = institutionName;
        this.physicianName = physicianName;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.phone = phone;
        this.legacyGuid = legacyGuid;
        this.street = street;
    }

    private MedicalProviderDto(
            String userMedicalProviderGuid,
            long userId,
            long umbrellaStudyId,
            InstitutionType institutionType,
            PostPatchMedicalProviderRequestPayload requestPayload
    ) {
        this.userMedicalProviderGuid = userMedicalProviderGuid;
        this.userId = userId;
        this.umbrellaStudyId = umbrellaStudyId;
        this.institutionType = institutionType;
        this.institutionName = requestPayload.getInstitutionName();
        this.physicianName = requestPayload.getPhysicianName();
        this.city = requestPayload.getCity();
        this.state = requestPayload.getState();
    }

    public static MedicalProviderDto createUnspecifiedMedicalProvider(
            String medicalProviderGuid,
            long userId,
            long umbrellaStudyId,
            InstitutionType institutionTypeCode,
            PostPatchMedicalProviderRequestPayload requestPayload
    ) {
        return new MedicalProviderDto(medicalProviderGuid, userId, umbrellaStudyId, institutionTypeCode, requestPayload);
    }

    public boolean isBlank() {
        return StringUtils.isBlank(physicianName)
                && StringUtils.isBlank(institutionName)
                && StringUtils.isBlank(city)
                && StringUtils.isBlank(state);
    }

    public long getUserMedicalProviderId() {
        return userMedicalProviderId;
    }

    public String getUserMedicalProviderGuid() {
        return userMedicalProviderGuid;
    }

    public long getUserId() {
        return userId;
    }

    public long getUmbrellaStudyId() {
        return umbrellaStudyId;
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getPhysicianName() {
        return physicianName;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getPhone() {
        return phone;
    }

    public String getLegacyGuid() {
        return legacyGuid;
    }

    public String getStreet() {
        return street;
    }
}
