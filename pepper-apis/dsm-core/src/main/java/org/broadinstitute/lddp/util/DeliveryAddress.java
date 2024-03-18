package org.broadinstitute.lddp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.easypost.model.Address;
import com.easypost.model.AddressVerification;
import com.easypost.model.Error;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class DeliveryAddress {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryAddress.class);

    private static final String LOG_PREFIX = "ADDRESS - ";

    private String id = "";
    private String name = "";
    private String street1 = "";
    private String street2 = "";
    private String city = "";
    private String state = "";
    private String zip = "";
    private String country = "";
    private String phone = "";
    private boolean valid = false;

    public DeliveryAddress(String street1, String street2, String city, String state, String zip, String country) {
        this.street1 = street1;
        this.street2 = street2;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.country = country;
    }

    public boolean isEmpty() {
        return (StringUtils.isBlank(this.street1))
                && (StringUtils.isBlank(this.street2))
                && (StringUtils.isBlank(this.city))
                && (StringUtils.isBlank(this.state))
                && (StringUtils.isBlank(this.zip))
                && (StringUtils.isBlank(this.country));
    }

    public DeliveryAddress(String street1, String street2, String city, String state, String zip, String country,
                           @NonNull String name, @NonNull String phone) {
        this(street1, street2, city, state, zip, country);
        this.name = name;
        this.phone = phone;
    }

    /** Uses easyPost services to determine whether an address
     * appears to be a legitimate address that can be shipped to.
     * Returns true if the address is valid for delivery, and false otherwise.
     */
    public boolean validate() {
        valid = false;

        //don't bother to call EasyPost if we just have an empty address...
        if (!isEmpty()) {
            Map<String, Object> addressFields = new HashMap<>();
            addressFields.put("name", name);
            addressFields.put("street1", street1);
            addressFields.put("street2", street2);
            addressFields.put("city", city);
            addressFields.put("state", state);
            addressFields.put("zip", zip);
            addressFields.put("country", country);
            addressFields.put("phone", phone);

            List<String> verifications = new ArrayList<>();
            verifications.add("delivery");
            addressFields.put("verify", verifications);

            Address address = null;

            try {
                address = Address.create(addressFields);
                AddressVerification deliveryVerification = address.getVerifications().get("delivery");
                if (!deliveryVerification.getSuccess()) {
                    List<Error> errors = deliveryVerification.getErrors();
                    StringBuilder errorMessage = new StringBuilder();
                    if (errors != null && !errors.isEmpty()) {
                        for (Error error : errors) {
                            errorMessage.append("validation error: " + error.getMessage() + "\n");
                        }
                    }
                    logger.info(LOG_PREFIX + "Address verification failed for "
                            + address.prettyPrint() + errorMessage);
                } else { //since address is ok update values
                    valid = true;
                    id = address.getId(); //only including id if valid
                    name = address.getName();
                    street1 = address.getStreet1();
                    street2 = address.getStreet2();
                    city = address.getCity();
                    state = address.getState();
                    zip = address.getZip();
                    country = address.getCountry();
                    phone = address.getPhone();
                }
            } catch (Exception ex) {
                // todo arz figure out proper error handling
                valid = false;
                logger.error(LOG_PREFIX + "An error occurred during address verification of " + address.prettyPrint(), ex);
            }
        } else {
            logger.info(LOG_PREFIX + "Address is empty.");
        }
        return valid;
    }
}
