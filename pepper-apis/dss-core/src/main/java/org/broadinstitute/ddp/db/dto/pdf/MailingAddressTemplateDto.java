package org.broadinstitute.ddp.db.dto.pdf;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class MailingAddressTemplateDto {
    @ColumnName("template_id")
    long pdfBaseTemplateId;
    
    @ColumnName("first_name_placeholder")
    String firstNamePlaceholder;
    
    @ColumnName("last_name_placeholder")
    String lastNamePlaceholder;

    @ColumnName("proxy_last_name_placeholder")
    String proxyLastNamePlaceholder;
    
    @ColumnName("proxy_first_name_placeholder")
    String proxyFirstNamePlaceholder;

    @ColumnName("street_placeholder")
    String streetPlaceholder;

    @ColumnName("city_placeholder")
    String cityPlaceholder;

    @ColumnName("state_placeholder")
    String statePlaceholder;
    
    @ColumnName("zip_placeholder")
    String zipPlaceholder;

    @ColumnName("country_placeholder")
    String countryPlaceholder;

    @ColumnName("phone_placeholder")
    String phonePlaceholder;
}
