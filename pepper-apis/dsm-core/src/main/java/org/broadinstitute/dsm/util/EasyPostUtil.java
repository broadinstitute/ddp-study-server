package org.broadinstitute.dsm.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.easypost.EasyPost;
import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.easypost.model.CustomsInfo;
import com.easypost.model.CustomsItem;
import com.easypost.model.Parcel;
import com.easypost.model.Rate;
import com.easypost.model.Shipment;
import com.easypost.model.ShipmentMessage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.exception.CarrierRejectionException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.RateNotAvailableException;
import org.broadinstitute.dsm.model.EasypostLabelRate;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.lddp.util.DeliveryAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EasyPostUtil {

    private static final Logger logger = LoggerFactory.getLogger(EasyPostUtil.class);
    public final String printCustom1Key = "print_custom_1";
    //easypost fields
    private final String toAddressKey = "to_address";
    private final String fromAddressKey = "from_address";
    private final String parcelKey = "parcel";
    private final String optionsKey = "options";
    private final String labelFormatKey = "label_format";
    private final String labelFormat = "PNG";
    private final String labelSizeKey = "label_size";
    private final String labelSize = "4x6";
    private final String customsInfoKey = "customs_info";

    private final String name = "name";
    private final String street1 = "street1";
    private final String street2 = "street2";
    private final String city = "city";
    private final String state = "state";
    private final String zip = "zip";
    private final String country = "country";
    private final String phone = "phone";
    private final String residential = "residential";
    private final String company = "company";

    private final String weight = "weight";
    private final String height = "height";
    private final String width = "width";
    private final String length = "length";

    private final String description = "description";
    private final String value = "value";
    private final String quantity = "quantity";
    private final String originCountry = "origin_country";
    private final String hsTariffNumber = "hs_tariff_number";
    private final String customsCertify = "customs_certify";
    private final String customsSigner = "customs_signer";
    private final String contentsType = "contents_type";
    private final String contentsExplanation = "contents_explanation";
    private final String eelPfc = "eel_pfc";
    private final String nonDeliveryOption = "non_delivery_option";
    private final String restrictionType = "restriction_type";
    private final String restrictionComments = "restriction_comments";
    private final String customsItems = "customs_items";

    public EasyPostUtil(@NonNull String instanceName) {
        String apiKey = DSMServer.getDDPEasypostApiKey(instanceName);
        if (StringUtils.isNotBlank(apiKey)) {
            EasyPost.apiKey = apiKey;
            logger.info("Setup of EasyPost api key");
        } else {
            throw new RuntimeException("No EasyPost api key was found");
        }
    }

    public EasyPostUtil(String instanceName, String apiKey) {
        if (StringUtils.isNotBlank(apiKey)) {
            EasyPost.apiKey = apiKey;
            logger.info("Setup of EasyPost api key");
        } else {
            throw new RuntimeException("No EasyPost api key was found");
        }
    }

    public static EasypostLabelRate getExpressRate(@NonNull String shipmentId, @NonNull String apiKey,
                                                   @NonNull String carrier, @NonNull String service) throws EasyPostException {
        Shipment shipment = Shipment.retrieve(shipmentId, apiKey);
        String express = null;
        String normal = null;
        for (Rate availableRate : shipment.getRates()) {
            if ("FedEx".equals(availableRate.getCarrier()) && "FIRST_OVERNIGHT".equals(availableRate.getService())) {
                express = String.valueOf(availableRate.getRate());
                logger.debug(
                        "Express rate is available. Carrier: " + availableRate.getCarrier() + ", service: " + availableRate.getService()
                                + " " + availableRate.getRate());
            }
            if (carrier.equals(availableRate.getCarrier()) && service.equals(availableRate.getService())) {
                normal = String.valueOf(availableRate.getRate());
                logger.debug("Normal label was Carrier: " + availableRate.getCarrier() + ", service: " + availableRate.getService()
                        + " " + availableRate.getRate());
            }
        }
        return new EasypostLabelRate(express, normal);
    }

    public Shipment buyShipment(@NonNull String carrier, String carrierId, String service, @NonNull Address toAddress,
                                @NonNull Address fromAddress,
                                @NonNull Parcel parcel, String billingReference, CustomsInfo customsInfo) throws EasyPostException {
        if (StringUtils.isEmpty(carrier)) {
            logger.error("Carrier and service needs to be set");
            return null;
        }

        try {
            //options
            Map<String, Object> optionsMap = new HashMap<>();
            optionsMap.put(labelFormatKey, labelFormat);
            optionsMap.put(labelSizeKey, labelSize);

            if (StringUtils.isNotBlank(billingReference)) {
                optionsMap.put(printCustom1Key, billingReference);
            }

            // create shipment
            Map<String, Object> shipmentMap = new HashMap<>();
            shipmentMap.put(toAddressKey, toAddress);
            shipmentMap.put(fromAddressKey, fromAddress);
            shipmentMap.put(parcelKey, parcel);
            shipmentMap.put(optionsKey, optionsMap);

            if (StringUtils.isNotBlank(carrierId)) { //special carrier account
                List<String> carriers = new ArrayList<>();
                carriers.add(carrierId);
                shipmentMap.put("carrier_accounts", carriers);
            }

            if (customsInfo != null) {
                shipmentMap.put(customsInfoKey, customsInfo);
            }

            Shipment shipment = Shipment.create(shipmentMap);
            List<ShipmentMessage> messages = shipment.getMessages();
            if (messages != null) {
                for (ShipmentMessage message : messages) {
                    if (carrier.equals(message.getCarrier())) {
                        throw new CarrierRejectionException(message.getMessage());
                    }
                }
            }

            Rate rate = null;
            for (Rate availableRate : shipment.getRates()) {
                if (StringUtils.isBlank(service) && carrier.equals(availableRate.getCarrier())) {
                    if (rate == null) {
                        rate = availableRate;
                    }
                    if (availableRate.getRate() < rate.getRate()) {
                        rate = availableRate;
                    }
                } else if (carrier.equals(availableRate.getCarrier()) && service.equals(availableRate.getService())) {
                    rate = availableRate;
                    logger.debug("Requested rate is available. Carrier: " + availableRate.getCarrier() + ", service: "
                            + availableRate.getService() + " " + availableRate.getRate());
                }
            }
            if (rate != null) {
                logger.info("Going to buy shipment with rate " + rate.getService());
                shipment.buy(rate);
                return shipment;
            } else {
                throw new RateNotAvailableException(carrier + " " + service + " not available");
            }
        } catch (EasyPostException e) {
            logger.warn(e.getMessage());
            throw new RuntimeException("Error buying easypost shipment", e);
        }
    }

    public Map<String, Object> createAddressMap(DDPParticipant ddpParticipant, @NonNull String phone)
            throws EasyPostException {
        if (StringUtils.isEmpty(this.phone)) {
            throw new RuntimeException("Contact phone number is needed");
        }

        String mailToName = ddpParticipant.getMailToName();
        if (StringUtils.isBlank(mailToName)) {
            String firstName = ddpParticipant.getFirstName();
            String lastName = ddpParticipant.getLastName();
            mailToName = firstName + " " + lastName;
        }

        Map<String, Object> toAddressMap = new HashMap<>();
        toAddressMap.put(this.name, mailToName);
        toAddressMap.put(this.street1, ddpParticipant.getStreet1());
        toAddressMap.put(this.street2, ddpParticipant.getStreet2());
        toAddressMap.put(this.city, ddpParticipant.getCity());
        toAddressMap.put(this.state, ddpParticipant.getState());
        toAddressMap.put(this.zip, ddpParticipant.getPostalCode());
        toAddressMap.put(this.country, ddpParticipant.getCountry());
        toAddressMap.put(this.phone, phone); //Needed for FedEx!
        toAddressMap.put(this.residential, true);
        return toAddressMap;
    }

    public Address createAddress(DDPParticipant ddpParticipant, @NonNull String phone, @NonNull String company)
            throws EasyPostException {
        if (StringUtils.isEmpty(this.phone)) {
            throw new RuntimeException("Contact phone number is needed");
        }
        Map<String, Object> toAddressMap = createAddressMap(ddpParticipant, phone);
        toAddressMap.put(this.company, company); // Care Of field goes here
        return Address.create(toAddressMap);
    }

    public Address createAddress(DDPParticipant ddpParticipant, @NonNull String phone)
            throws EasyPostException {
        if (StringUtils.isEmpty(this.phone)) {
            throw new RuntimeException("Contact phone number is needed");
        }
        Map<String, Object> toAddressMap = createAddressMap(ddpParticipant, phone);
        return Address.create(toAddressMap);
    }

    public Address createAddressWithoutValidation(@NonNull String name, @NonNull String street1, @NonNull String street2,
                                                  @NonNull String city, @NonNull String zip, @NonNull String state,
                                                  @NonNull String country, @NonNull String phone) throws EasyPostException {
        Map<String, Object> fromAddressMap = new HashMap<>();
        fromAddressMap.put(this.name, name);
        fromAddressMap.put(this.street1, street1);
        fromAddressMap.put(this.street2, street2);
        fromAddressMap.put(this.city, city);
        fromAddressMap.put(this.state, state);
        fromAddressMap.put(this.zip, zip);
        fromAddressMap.put(this.country, country);
        fromAddressMap.put(this.phone, phone);
        return Address.create(fromAddressMap);
    }

    //inches and oz
    public Parcel createParcel(@NonNull String weight, @NonNull String height, @NonNull String width,
                               @NonNull String length) throws EasyPostException {
        Map<String, Object> parcelMap = new HashMap<>();
        parcelMap.put(this.weight, weight);
        parcelMap.put(this.height, height);
        parcelMap.put(this.width, width);
        parcelMap.put(this.length, length);

        return Parcel.create(parcelMap);
    }

    public CustomsInfo createCustomsInfo(@NonNull String customsJson) throws EasyPostException {
        Map<String, Object> customsItemMap = new HashMap<>();
        customsItemMap.put(this.description, getStringFromJson(customsJson, this.description));
        customsItemMap.put(this.quantity, getStringFromJson(customsJson, this.quantity));
        customsItemMap.put(this.value, getStringFromJson(customsJson, this.value));
        customsItemMap.put(this.weight, getStringFromJson(customsJson, this.weight));
        customsItemMap.put(this.originCountry, getStringFromJson(customsJson, this.originCountry));
        customsItemMap.put(this.hsTariffNumber, getStringFromJson(customsJson, this.hsTariffNumber));
        CustomsItem customsItem = CustomsItem.create(customsItemMap);

        List<CustomsItem> customsItemsList = new ArrayList<>();
        customsItemsList.add(customsItem);

        Map<String, Object> customsInfoMap = new HashMap<>();
        customsInfoMap.put(this.customsCertify, getStringFromJson(customsJson, this.customsCertify));
        customsInfoMap.put(this.customsSigner, getStringFromJson(customsJson, this.customsSigner));
        customsInfoMap.put(this.contentsType, getStringFromJson(customsJson, this.contentsType));
        customsInfoMap.put(this.contentsExplanation, getStringFromJson(customsJson, this.contentsExplanation));
        customsInfoMap.put(this.eelPfc, getStringFromJson(customsJson, this.eelPfc));
        customsInfoMap.put(this.nonDeliveryOption, getStringFromJson(customsJson, this.nonDeliveryOption));
        customsInfoMap.put(this.restrictionType, getStringFromJson(customsJson, this.restrictionType));
        customsInfoMap.put(this.restrictionComments, getStringFromJson(customsJson, this.restrictionComments));
        customsInfoMap.put(this.customsItems, customsItemsList);

        return CustomsInfo.create(customsInfoMap);
    }

    private String getStringFromJson(@NonNull String json, @NonNull String value) {
        return ((JsonObject) (new JsonParser().parse(json))).get(value).getAsString();
    }

    public Address getAddress(String addressId) throws EasyPostException {
        return Address.retrieve(addressId);
    }

    public Shipment getShipment(String shipmentId) throws EasyPostException {
        return Shipment.retrieve(shipmentId);
    }

    /**
     * getEasyPostAddressId tries creating an address in EasyPost. If it is successful,
     * it returns the easyPost address id that is generated, if not throws an exception
     * An address is valid only if participant has shortId, first - and lastName, for Juniper shortId is the juniperParticipantId
     *
     * @param juniperKitRequest the JuniperKitRequest with address to check
     * @param phone the phone number from ddp kit request settings
     * @param deliveryAddress an instance of DeliverAddress created from the address passed from Juniper's request
     * @return String the easypost address id for the specific address
     */

    public String getEasyPostAddressId(@NonNull JuniperKitRequest juniperKitRequest, String phone, DeliveryAddress deliveryAddress) {
        if (StringUtils.isBlank(juniperKitRequest.getLastName())) {
            throw new DSMBadRequestException("KitRequest did not have a last name ");
        }
        if (juniperKitRequest.isSkipAddressValidation()) {
            //if no validation is needed, we just need to create the Address instance in easypost and get its id back
            try {
                Address address = createAddressWithoutValidation(name, juniperKitRequest.getStreet1(), juniperKitRequest.getStreet2(),
                        juniperKitRequest.getCity(),
                        juniperKitRequest.getPostalCode(), juniperKitRequest.getState(), juniperKitRequest.getCountry(), phone);
                return address.getId();
            } catch (EasyPostException e) {
                // log the reason for address creation failure and return false. The method will then return the error code
                throw new DsmInternalError("Easypost couldn't create an address for " + juniperKitRequest.getShortId(), e);
            }
        }
        //call easypost apis to make sure the address is valid
        deliveryAddress.validate();
        if (deliveryAddress.isValid()) {
            //store the address back
            return deliveryAddress.getId();
        }
        throw new DSMBadRequestException(String.format("Address is not valid %s", juniperKitRequest.getJuniperKitId()));
    }
}
