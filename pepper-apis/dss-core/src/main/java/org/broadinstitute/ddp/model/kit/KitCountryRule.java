package org.broadinstitute.ddp.model.kit;

import java.util.Optional;

import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.jdbi.v3.core.Handle;

public class KitCountryRule extends KitRule<String> {

    private final String validCountryCode;

    public KitCountryRule(long id, String validCountryCode) {
        super(id, KitRuleType.COUNTRY);
        this.validCountryCode = validCountryCode;
    }

    @Override
    public boolean validate(Handle handle, String input) {
        JdbiMailAddress jdbiMailAddress = handle.attach(JdbiMailAddress.class);

        Optional<MailAddress> optionalMailAddress = jdbiMailAddress.findDefaultAddressForParticipant(input);
        if (!optionalMailAddress.isPresent()) {
            return false;
        } else {
            MailAddress mailAddress = optionalMailAddress.get();
            String country = mailAddress.getCountry();
            if (country != null) {
                return validCountryCode.equals(country.toLowerCase());
            }
            return false;
        }
    }

}
