package org.broadinstitute.ddp.db.dto.pdf;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class MailingAddressTemplateDto {

    private long pdfBaseTemplateId;
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

    @JdbiConstructor
    public MailingAddressTemplateDto(@ColumnName("template_id") long pdfBaseTemplateId,
                                     @ColumnName("first_name_placeholder") String firstNamePlaceholder,
                                     @ColumnName("last_name_placeholder") String lastNamePlaceholder,
                                     @ColumnName("proxy_last_name_placeholder") String proxyLastNamePlaceholder,
                                     @ColumnName("proxy_first_name_placeholder") String proxyFirstNamePlaceholder,
                                     @ColumnName("street_placeholder") String streetPlaceholder,
                                     @ColumnName("city_placeholder") String cityPlaceholder,
                                     @ColumnName("state_placeholder") String statePlaceholder,
                                     @ColumnName("zip_placeholder") String zipPlaceholder,
                                     @ColumnName("country_placeholder") String countryPlaceholder,
                                     @ColumnName("phone_placeholder") String phonePlaceholder) {
        this.pdfBaseTemplateId = pdfBaseTemplateId;
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
}
