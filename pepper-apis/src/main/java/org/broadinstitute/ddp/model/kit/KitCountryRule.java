package org.broadinstitute.ddp.model.kit;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dao.JdbiCountry;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.jdbi.v3.core.Handle;

public class KitCountryRule extends KitRule<String> {

    private final String validCountry;

    public KitCountryRule(String validCountry) {
        super(KitRuleType.COUNTRY);
        this.validCountry = validCountry;
    }

    @Override
    public boolean validate(Handle handle, String input) {
        JdbiMailAddress jdbiMailAddress = handle.attach(JdbiMailAddress.class);
        JdbiCountry jdbiCountry = handle.attach(JdbiCountry.class);

        Optional<MailAddress> optionalMailAddress = jdbiMailAddress.findDefaultAddressForParticipant(input);
        if (!optionalMailAddress.isPresent()) {
            return false;
        } else {
            MailAddress mailAddress = optionalMailAddress.get();
            String country = mailAddress.getCountry();
            if (country != null) {
                String countryName = jdbiCountry.getCountryNameByCode(country.toLowerCase());
                if (StringUtils.isNotBlank(countryName)) {
                    return (countryName.equals(validCountry));
                }
            }
            return false;
        }
    }

}
