package org.broadinstitute.ddp.model.pdf;

import static org.broadinstitute.ddp.model.pdf.PdfTemplateType.MAILING_ADDRESS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.ddp.db.dto.pdf.MailingAddressTemplateDto;
import org.broadinstitute.ddp.db.dto.pdf.PdfTemplateDto;

public final class MailingAddressTemplate extends PdfTemplate {

    private String firstNamePlaceholder;
    private String lastNamePlaceholder;
    private String proxyFirstNamePlaceholder;
    private String proxyLastNamePlaceholder;
    private String streetPlaceholder;
    private String cityPlaceholder;
    private String statePlaceholder;
    private String zipPlaceholder;
    private String countryPlaceholder;
    private String phonePlaceholder;

    public MailingAddressTemplate(PdfTemplateDto dto, MailingAddressTemplateDto templateDto) {
        super(dto.getId(), MAILING_ADDRESS, dto.getBlob());
        if (dto.getType() != MAILING_ADDRESS) {
            throw new IllegalArgumentException("mismatched pdf template type " + dto.getType());
        }
        this.firstNamePlaceholder = templateDto.getFirstNamePlaceholder();
        this.lastNamePlaceholder = templateDto.getLastNamePlaceholder();
        this.proxyFirstNamePlaceholder = templateDto.getProxyFirstNamePlaceholder();
        this.proxyLastNamePlaceholder = templateDto.getProxyLastNamePlaceholder();
        this.streetPlaceholder = templateDto.getStreetPlaceholder();
        this.cityPlaceholder = templateDto.getCityPlaceholder();
        this.statePlaceholder = templateDto.getStatePlaceholder();
        this.zipPlaceholder = templateDto.getZipPlaceholder();
        this.countryPlaceholder = templateDto.getCountryPlaceholder();
        this.phonePlaceholder = templateDto.getPhonePlaceholder();
    }

    public MailingAddressTemplate(byte[] rawBytes, String firstNamePlaceholder, String lastNamePlaceholder,
                                  String proxyFirstNamePlaceholder, String proxyLastNamePlaceholder,
                                  String streetPlaceholder, String cityPlaceholder, String statePlaceholder,
                                  String zipPlaceholder, String countryPlaceholder, String phonePlaceholder) {
        super(MAILING_ADDRESS, rawBytes);
        this.firstNamePlaceholder = firstNamePlaceholder;
        this.lastNamePlaceholder = lastNamePlaceholder;
        this.proxyFirstNamePlaceholder = proxyFirstNamePlaceholder;
        this.proxyLastNamePlaceholder = proxyLastNamePlaceholder;
        this.streetPlaceholder = streetPlaceholder;
        this.cityPlaceholder = cityPlaceholder;
        this.statePlaceholder = statePlaceholder;
        this.zipPlaceholder = zipPlaceholder;
        this.countryPlaceholder = countryPlaceholder;
        this.phonePlaceholder = phonePlaceholder;
    }

    public String getFirstNamePlaceholder() {
        return firstNamePlaceholder;
    }

    public String getLastNamePlaceholder() {
        return lastNamePlaceholder;
    }

    public void setLastNamePlaceholder(String lastNamePlaceholder) {
        this.lastNamePlaceholder = lastNamePlaceholder;
    }

    public String getStreetPlaceholder() {
        return streetPlaceholder;
    }

    public String getCityPlaceholder() {
        return cityPlaceholder;
    }

    public String getStatePlaceholder() {
        return statePlaceholder;
    }

    public String getZipPlaceholder() {
        return zipPlaceholder;
    }

    public String getCountryPlaceholder() {
        return countryPlaceholder;
    }

    public String getPhonePlaceholder() {
        return phonePlaceholder;
    }

    public String getProxyFirstNamePlaceholder() {
        return proxyFirstNamePlaceholder;
    }

    public String getProxyLastNamePlaceholder() {
        return proxyLastNamePlaceholder;
    }

    public void setProxyLastNamePlaceholder(String proxyLastNamePlaceholder) {
        this.proxyLastNamePlaceholder = proxyLastNamePlaceholder;
    }

    public List<String> getRequiredPlaceholders() {
        return new ArrayList<>(Arrays.asList(
                streetPlaceholder, cityPlaceholder, statePlaceholder,
                phonePlaceholder, zipPlaceholder));
    }
}
