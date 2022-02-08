package org.broadinstitute.ddp.db.dto.pdf;

import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PhysicianInstitutionTemplateDto {

    private long pdfBaseTemplateId;
    private String physicianNamePlaceholder;
    private String institutionNamePlaceholder;
    private String cityPlaceholder;
    private String statePlaceholder;
    private String streetPlaceholder;
    private String zipPlaceholder;
    private String phonePlaceholder;
    private InstitutionType institutionType;

    @JdbiConstructor
    public PhysicianInstitutionTemplateDto(@ColumnName("template_id") long pdfBaseTemplateId,
                                           @ColumnName("physician_name_placeholder") String physicianNamePlaceholder,
                                           @ColumnName("institution_name_placeholder") String institutionNamePlaceholder,
                                           @ColumnName("city_placeholder") String cityPlaceholder,
                                           @ColumnName("state_placeholder") String statePlaceholder,
                                           @ColumnName("street_placeholder") String streetPlaceholder,
                                           @ColumnName("zip_placeholder") String zipPlaceholder,
                                           @ColumnName("phone_placeholder") String phonePlaceholder,
                                           @ColumnName("institution_type") InstitutionType institutionType) {
        this.pdfBaseTemplateId = pdfBaseTemplateId;
        this.physicianNamePlaceholder = physicianNamePlaceholder;
        this.institutionNamePlaceholder = institutionNamePlaceholder;
        this.cityPlaceholder = cityPlaceholder;
        this.statePlaceholder = statePlaceholder;
        this.streetPlaceholder = streetPlaceholder;
        this.zipPlaceholder = zipPlaceholder;
        this.phonePlaceholder = phonePlaceholder;
        this.institutionType = institutionType;
    }

    public String getPhysicianNamePlaceholder() {
        return physicianNamePlaceholder;
    }

    public String getInstitutionNamePlaceholder() {
        return institutionNamePlaceholder;
    }

    public String getCityPlaceholder() {
        return cityPlaceholder;
    }

    public String getStatePlaceholder() {
        return statePlaceholder;
    }

    public String getStreetPlaceholder() {
        return streetPlaceholder;
    }

    public String getZipPlaceholder() {
        return zipPlaceholder;
    }

    public String getPhonePlaceholder() {
        return phonePlaceholder;
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }
}
