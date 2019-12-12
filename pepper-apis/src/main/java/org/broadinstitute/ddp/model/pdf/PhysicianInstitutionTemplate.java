package org.broadinstitute.ddp.model.pdf;

import static org.broadinstitute.ddp.model.pdf.PdfTemplateType.PHYSICIAN_INSTITUTION;

import org.broadinstitute.ddp.db.dto.pdf.PdfTemplateDto;
import org.broadinstitute.ddp.db.dto.pdf.PhysicianInstitutionTemplateDto;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

public final class PhysicianInstitutionTemplate extends PdfTemplate {

    private String physicianNamePlaceholder;
    private String institutionNamePlaceholder;
    private String cityPlaceholder;
    private String statePlaceholder;
    private String streetPlaceholder;
    private String zipPlaceholder;
    private String phonePlaceholder;
    private InstitutionType institutionType;

    public PhysicianInstitutionTemplate(PdfTemplateDto dto, PhysicianInstitutionTemplateDto templateDto) {
        super(dto.getId(), PHYSICIAN_INSTITUTION, dto.getBlob());
        if (dto.getType() != PHYSICIAN_INSTITUTION) {
            throw new IllegalArgumentException("mismatched pdf template type " + dto.getType());
        }
        this.physicianNamePlaceholder = templateDto.getPhysicianNamePlaceholder();
        this.institutionNamePlaceholder = templateDto.getInstitutionNamePlaceholder();
        this.cityPlaceholder = templateDto.getCityPlaceholder();
        this.statePlaceholder = templateDto.getStatePlaceholder();
        this.streetPlaceholder = templateDto.getStreetPlaceholder();
        this.zipPlaceholder = templateDto.getZipPlaceholder();
        this.phonePlaceholder = templateDto.getPhonePlaceholder();
        this.institutionType = templateDto.getInstitutionType();
    }

    public PhysicianInstitutionTemplate(byte[] rawBytes, InstitutionType institutionType,
                                        String physicianNamePlaceholder, String institutionNamePlaceholder,
                                        String cityPlaceholder, String statePlaceholder,
                                        String streetPlaceholder, String zipPlaceholder, String phonePlaceholder) {
        super(PHYSICIAN_INSTITUTION, rawBytes);
        this.physicianNamePlaceholder = physicianNamePlaceholder;
        this.institutionNamePlaceholder = institutionNamePlaceholder;
        this.cityPlaceholder = cityPlaceholder;
        this.statePlaceholder = statePlaceholder;
        this.streetPlaceholder = streetPlaceholder;
        this.zipPlaceholder = zipPlaceholder;
        this.phonePlaceholder = phonePlaceholder;
        this.institutionType = institutionType;
    }

    public PhysicianInstitutionTemplate(byte[] rawBytes, InstitutionType institutionType,
                                        String physicianNamePlaceholder, String institutionNamePlaceholder,
                                        String cityPlaceholder, String statePlaceholder) {
        this(rawBytes, institutionType, physicianNamePlaceholder,
                institutionNamePlaceholder, cityPlaceholder, statePlaceholder,
                null, null, null);
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
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
}
