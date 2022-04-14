package org.broadinstitute.lddp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.easypost.model.Address;
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
    private boolean empty = true;
    private boolean valid = false;

    public DeliveryAddress(String street1, String street2, String city, String state, String zip, String country) {
        this.street1 = street1;
        this.street2 = street2;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.country = country;

        empty = (StringUtils.isBlank(street1)) && (StringUtils.isBlank(street2)) && (StringUtils.isBlank(city))
                && (StringUtils.isBlank(state)) && (StringUtils.isBlank(zip)) && (StringUtils.isBlank(country));
    }

    public DeliveryAddress(String street1, String street2, String city, String state, String zip, String country,
                           @NonNull String name, @NonNull String phone) {
        this(street1, street2, city, state, zip, country);
        this.name = name;
        this.phone = phone;
    }

    private DeliveryAddress(@NonNull Address address) {
        this.id = address.getId();
        this.name = address.getName();
        this.street1 = address.getStreet1();
        this.street2 = address.getStreet2();
        this.city = address.getCity();
        this.state = address.getState();
        this.zip = address.getZip();
        this.country = address.getCountry();
        this.phone = address.getPhone();
    }

    public static DeliveryAddress populateFromEasyPost(@NonNull String id) {
        DeliveryAddress deliveryAddress = null;
        try {
            Address address = Address.retrieve(id);
            deliveryAddress = new DeliveryAddress(address);
        } catch (Exception ex) {
            logger.error(LOG_PREFIX + "An error occurred during address retrieval.", ex);
        }
        return deliveryAddress;
    }

    public void validate() {
        valid = false;

        //don't bother to call EasyPost if we just have an empty address...
        if (!empty) {
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
                if (!address.getVerifications().get("delivery").getSuccess()) {
                    logger.info(LOG_PREFIX + "Address verification failed.");
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
                logger.error(LOG_PREFIX + "An error occurred during address verification.", ex);
            }
        } else {
            logger.info(LOG_PREFIX + "Address is empty.");
        }
    }
}
