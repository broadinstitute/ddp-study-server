package org.broadinstitute.ddp.model.address;

import javax.validation.constraints.NotEmpty;

import org.broadinstitute.ddp.service.DsmAddressValidationStatus;

/**
 * Subclass of mail address that includes additional validation rules, including enforcing
 * required values and minimum length of fields.
 * Note that the validation rules are added to whatever rules already exist in overridden field
 * or method.
 */
public class MailAddressWithStrictValidationRules extends MailAddress {
    @NotEmpty
    public String getName() {
        return super.getName();
    }

    @NotEmpty
    public String getStreet1() {
        return super.getStreet1();
    }

    @Override
    @NotEmpty
    public String getCity() {
        return super.getCity();
    }

    @Override
    public String getState() {
        return super.getState();
    }

    @Override
    @NotEmpty
    public String getCountry() {
        return super.getCountry();
    }

    @Override
    public String getPhone() {
        return super.getPhone();
    }

    @Override
    @NotEmpty
    public String getZip() {
        return super.getZip();
    }

    public MailAddressWithStrictValidationRules(String name, String street1, String street2, String city,
                                                String state, String country, String zip, String phone, String description,
                                                DsmAddressValidationStatus validationStatus, String plusCode, boolean isDefault) {
        super(name, street1, street2, city,
                state, country, zip, phone, plusCode, description,
                validationStatus, isDefault);
    }
}
