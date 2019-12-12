package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.OLCService.DEFAULT_OLC_PRECISION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nonnull;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiCountryAddressInfo;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.exception.AddressVerificationException;
import org.broadinstitute.ddp.model.address.CountryAddressInfo;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Main entry-point for back-end operations related to mailing address.
 */
public class AddressService {
    private static final Logger LOG = LoggerFactory.getLogger(AddressService.class);

    private final String easyPostApiKey;
    private final String geocodingKey;

    public AddressService(@Nonnull String easyPostApiKey, @Nonnull String geocodingKey) {
        this.easyPostApiKey = easyPostApiKey;
        this.geocodingKey = geocodingKey;
    }

    /**
     * Inserts new addresses. Sets the validation status checking against EasyPost before saving
     *
     * @param addressFromClient the address to save
     * @param participantGuid   the user guid for the participant
     * @param operatorGuid      the user guid for the operator
     * @return the saved Address with new GUID and validation flag assigned.
     */
    public MailAddress addAddress(MailAddress addressFromClient, String participantGuid, String operatorGuid) {
        return TransactionWrapper.withTxn(handle -> addAddress(handle, addressFromClient, participantGuid, operatorGuid));
    }

    public MailAddress addAddress(Handle handle, MailAddress addressFromClient, String participantGuid, String operatorGuid) {
        OLCService olcService = new OLCService(geocodingKey);
        addressFromClient.setPlusCode(olcService.calculateFullPlusCode(addressFromClient));
        DsmAddressValidationStatus validationStatus = calculateAddressValidationStatus(addressFromClient);
        addressFromClient.setValidationStatus(validationStatus);
        JdbiMailAddress dao = buildAddressDao(handle);
        dao.insertAddress(addressFromClient, participantGuid, operatorGuid);
        setAddressIsDefaultFlag(addressFromClient, dao);
        return addressFromClient;
    }

    /**
     * Calculate the validation status of the address to support DSM integration
     *
     * @param address the address
     * @return enum {@code DsmAddressValidationStatus} corresponding to the EasyPost check
     */
    private DsmAddressValidationStatus calculateAddressValidationStatus(MailAddress address) {
        DsmAddressValidationStatus validationStatus;
        try {
            MailAddress suggestedAddressFromEasyPost = verifyAddress(address);
            //We are going to convert all the field values with empty strings to nulls and then compare all the string
            // fields.
            //if they are all the same, then we are going to say that user had accepted the EasyPost suggested address
            if (areAllStringsEqual(normalizeMailAddress(address), normalizeMailAddress(suggestedAddressFromEasyPost))) {
                validationStatus = DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS;
            } else {
                //One or more fields were different, but EasyPost did not say it was invalid. We are calling it valid
                validationStatus = DSM_VALID_ADDRESS_STATUS;
            }
        } catch (AddressVerificationException e) {
            //EasyPost did not like this address. Invalid!!
            validationStatus = DSM_INVALID_ADDRESS_STATUS;
        }
        return validationStatus;
    }

    /**
     * Compare the strings of the two mail addresses
     *
     * @param address1 first address
     * @param address2 second address
     * @return true if all strings are equal, false otherwise
     */
    private boolean areAllStringsEqual(MailAddress address1, MailAddress address2) {
        return getMailAddressStringAccessors().stream()
                .allMatch(accessor -> mailStringValuesAreEqual(address1, address2, accessor));
    }

    /**
     * All the string accessors method references in Mail Address
     *
     * @return accessors in a list
     */
    private List<Function<MailAddress, String>> getMailAddressStringAccessors() {
        return Arrays.asList(MailAddress::getName,
                MailAddress::getStreet1, MailAddress::getStreet2, MailAddress::getCity, MailAddress::getState,
                MailAddress::getCountry, MailAddress::getZip, MailAddress::getPhone, MailAddress::getPlusCode);
    }

    /**
     * Given the {@code MailAddress} accessor and the two addresses, check whether corresponding values are equal or not
     *
     * @param address1       address1
     * @param address2       address2
     * @param stringAccessor the MailAddress method reference that will be applied to both objects.
     * @return true or false if not equal
     */
    private boolean mailStringValuesAreEqual(MailAddress address1, MailAddress address2,
                                             Function<MailAddress, String> stringAccessor) {
        BiFunction<String, String, Boolean> eq = (str1, str2) -> (str1 == null && str2 == null)
                || str1 != null && str1.equals(str2);
        return eq.apply(stringAccessor.apply(address1), stringAccessor.apply(address2));
    }

    /**
     * Create a "normalized" mail address for comparison purposes. For our comparison, a null and an empty string are
     * equivalent, so fields with the {code String} type will be converted to one or the other. Then the mail
     * address comparison can proceed as an apples-to-apples comparison
     *
     * @param original the original address
     * @return the normalized {@code MailAddress}
     */
    private MailAddress normalizeMailAddress(MailAddress original) {
        return new MailAddress(normalizeStringField(original.getName()), normalizeStringField(original.getStreet1()),
                normalizeStringField(original.getStreet2()), normalizeStringField(original.getCity()),
                normalizeStringField(original.getState()), normalizeStringField(original.getCountry()),
                normalizeStringField(original.getZip()), normalizeStringField(original.getPhone()),
                normalizeStringField(original.getPlusCode()),
                normalizeStringField(original.getDescription()), null,
                original.isDefault());
    }

    private String normalizeStringField(String fieldValue) {
        if (fieldValue == null) {
            return null;
        }
        String trimmedValue = fieldValue.trim();
        if (trimmedValue.length() == 0) {
            return null;
        } else {
            return trimmedValue;
        }

    }

    /**
     * Only one address can be the default one for a participant. Given a mail address, we check the
     * {@code MailAddress.isDefault()} flag and set the address to be the default or we uncheck it.
     *
     * @param address the mail address
     * @param dao     the dao
     */
    private void setAddressIsDefaultFlag(MailAddress address, JdbiMailAddress dao) {
        if (address.isDefault()) {
            dao.setDefaultAddressForParticipant(address.getGuid());
        } else {
            dao.unsetDefaultAddressForParticipant(address.getGuid());
        }
    }

    /**
     * Update the address identified by given guid with properties specified in address object.
     *
     * @param addressGuid     address guid
     * @param address         the address with the new properties to be updated
     * @param participantGuid the  participant user guid
     * @param operatorGuid    the operator user guid
     * @return true if the address was found and updated. False otherwise
     */
    public boolean updateAddress(String addressGuid, MailAddress address, String participantGuid, String operatorGuid) {
        return TransactionWrapper.withTxn(handle -> updateAddress(handle, addressGuid, address, participantGuid, operatorGuid));
    }

    public boolean updateAddress(Handle handle, String addressGuid, MailAddress address, String participantGuid, String operatorGuid) {
        JdbiMailAddress dao = buildAddressDao(handle);
        setAddressIsDefaultFlag(address, dao);

        OLCService olcService = new OLCService(geocodingKey);
        address.setPlusCode(olcService.calculateFullPlusCode(address));
        DsmAddressValidationStatus validationStatus = calculateAddressValidationStatus(address);
        address.setValidationStatus(validationStatus);

        int rowsChanged = dao.updateAddress(addressGuid, address, participantGuid, operatorGuid);
        return rowsChanged == 1;
    }

    /**
     * Given an address guid, we will set this address as the default for the participant associated with it.
     * There is only one default address per participant, so this call will replace whatever other default address
     * has been selected.
     *
     * @param addressGuid the address guid
     * @return true if the address with specified guid was found and set. false if not
     */
    public boolean setAddressAsDefault(Handle handle, String addressGuid) {
        JdbiMailAddress dao = buildAddressDao(handle);
        dao.setDefaultAddressForParticipant(addressGuid);
        Optional<MailAddress> address = dao.findAddressByGuid(addressGuid);
        return address.isPresent() && address.get().isDefault();
    }

    /**
     * Return the default address for the the participant guid
     *
     * @param participantGuid the participant {@code User} guid
     * @return an {@code Optional} which might or might not be empty depending on existence of a default address.
     */
    public Optional<MailAddress> findDefaultAddressForParticipant(String participantGuid, OLCPrecision precision) {
        return TransactionWrapper.withTxn(handle ->
                buildAddressDao(handle)
                        .findDefaultAddressForParticipant(participantGuid)
                        .map(addr -> {
                            addr.setPlusCode(OLCService.convertPlusCodeToPrecision(addr.getPlusCode(), precision));
                            return addr;
                        })
        );
    }

    public Optional<MailAddress> findDefaultAddressForParticipant(String participantGuid) {
        return findDefaultAddressForParticipant(participantGuid, DEFAULT_OLC_PRECISION);
    }

    /**
     * Return the corresponding address
     *
     * @param guid the address guid
     * @return an {@code Optional} for the address. Will be empty if not found
     */
    public Optional<MailAddress> findAddressByGuid(String guid, OLCPrecision precision) {
        return TransactionWrapper.withTxn(handle -> buildAddressDao(handle)
                .findAddressByGuid(guid)
                .map(addr -> {
                    addr.setPlusCode(OLCService.convertPlusCodeToPrecision(addr.getPlusCode(), precision));
                    return addr;
                }));
    }

    public Optional<MailAddress> findAddressByGuid(String guid) {
        return findAddressByGuid(guid, DEFAULT_OLC_PRECISION);
    }

    /**
     * Return all the addresses associated with the participant in order of creation time
     *
     * @param participantGuid the user guid for the participant
     * @return the list of addresses sorted by creation time
     */
    public List<MailAddress> findAllAddressesForParticipant(String participantGuid, OLCPrecision precision) {
        return TransactionWrapper.withTxn(handle -> {
            List<MailAddress> addresses = buildAddressDao(handle).findAllAddressesForParticipant(participantGuid);

            for (MailAddress address : addresses) {
                address.setPlusCode(OLCService.convertPlusCodeToPrecision(address.getPlusCode(), precision));
            }
            return addresses;
        });
    }

    public List<MailAddress> findAllAddressesForParticipant(String participantGuid) {
        return findAllAddressesForParticipant(participantGuid, DEFAULT_OLC_PRECISION);
    }

    /**
     * Delete the address with specified guid
     *
     * @param addressGuid the address guid
     * @return true if found and deleted, false otherwise
     */
    public static boolean deleteAddress(Handle handle, String addressGuid) {
        return handle.attach(JdbiMailAddress.class).deleteAddressByGuid(addressGuid);
    }

    /**
     * Do second-order validation of MailAddress object (stuff that cannot be checked by simple validation annotations.
     *
     * @param address address to validate
     * @return the list of errors found. Empty if none found.
     */
    public List<JsonValidationError> validateAddress(MailAddress address) {
        return TransactionWrapper.withTxn(handle -> {
            List<JsonValidationError> errors = new ArrayList<>();
            JdbiCountryAddressInfo countryInfoDao = buildCountryInfoDao(handle);
            Optional<CountryAddressInfo> countryInfo = countryInfoDao.getCountryAddressInfo(address.getCountry());
            if (!countryInfo.isPresent()) {
                errors.add(new JsonValidationError(Collections.singletonList("country"),
                        "Country code: " + address.getCountry() + " is not recognized", address.getCountry()));
            } else {
                if (!countryInfo.get().getSubnationDisivisionByCode(address.getState()).isPresent()) {
                    errors.add(new JsonValidationError(Collections.singletonList("state"),
                            "State code: " + address.getState() + " could not be found", address.getState()));
                }
            }
            return errors;

        });
    }


    /**
     * Verify provided address using EasyPost service. Will find problems in given address and, if possible, return a
     * suggested address.
     *
     * @param address the address
     * @return an address with suggested changes
     * @throws AddressVerificationException if EasyPost is unable to verify the address. the contained
     *                                      {@code AddressVerificationError} could include more specific error
     *                                      information, including field specific errors.
     */
    public MailAddress verifyAddress(MailAddress address) throws AddressVerificationException {
        try {
            OLCService olcService = new OLCService(geocodingKey);

            Address easyPostVerifiedAddress = Address.create(convertToEasyPostAddressMap(address), getEasyPostApiKey());
            MailAddress verifiedAddress = new MailAddress();
            verifiedAddress.setName(easyPostVerifiedAddress.getName());
            verifiedAddress.setStreet1(easyPostVerifiedAddress.getStreet1());
            verifiedAddress.setStreet2(easyPostVerifiedAddress.getStreet2());
            verifiedAddress.setCity(easyPostVerifiedAddress.getCity());
            verifiedAddress.setState(easyPostVerifiedAddress.getState());
            verifiedAddress.setCountry(easyPostVerifiedAddress.getCountry());
            verifiedAddress.setZip(easyPostVerifiedAddress.getZip());
            verifiedAddress.setPhone(easyPostVerifiedAddress.getPhone());
            verifiedAddress.setPlusCode(olcService.calculateFullPlusCode(verifiedAddress));

            return verifiedAddress;
        } catch (EasyPostException e) {
            AddressVerificationError error;
            //we need to parse the JSON out of the error message! Sad but true:
            if (e.getMessage() != null) {
                error = buildVerificationErrorFromExceptionMessage(e.getMessage());
            } else {
                error = buildGenericVerificationError();
            }
            throw new AddressVerificationException(error);
        }
    }


    /**
     * Shame on EasyPost to only provide the error JSON as an embedded string in the error message.
     * We will try to parse it ourselves paranoid style. Who knows what will happen when library is updated?
     *
     * @param message the error message from the EasyPost exception
     * @return the parsed AddressVerificationError or null if we fail to extract it
     */
    private AddressVerificationError buildVerificationErrorFromExceptionMessage(String message) {
        int jsonStart = message.indexOf("{");
        int jsonEnd = message.lastIndexOf("}");
        if (jsonStart >= 0 && jsonEnd > 0) {
            String jsonString = message.substring(jsonStart, jsonEnd + 1);
            JsonElement json;
            try {
                json = new JsonParser().parse(jsonString);
            } catch (JsonSyntaxException e) {
                //Could not parse the message. Oh well!
                LOG.warn(String.format("Could not parse into JSON error message that came from an exception: %s",
                        message), e);
                return null;
            }
            if (json.isJsonObject()) {
                JsonObject jsonTopLevelErrorObject = json.getAsJsonObject();

                //A weird thing in the error JSON from EasyPost: there it is
                // an object with only one property named error
                //We only care about the contents of that property!
                JsonObject jsonErrorObject = jsonTopLevelErrorObject.getAsJsonObject("error");
                if (jsonErrorObject != null) {
                    try {
                        return new Gson().fromJson(jsonErrorObject, AddressVerificationError.class);
                    } catch (JsonSyntaxException e) {
                        LOG.warn(String.format("Could not convert the JSON to a AddressVerificationError: %s",
                                jsonErrorObject.toString()), e);
                        return null;
                    }

                } else {
                    LOG.warn("JSON string from exception did not have a property named error: {}", message);
                    return null;
                }
            } else {
                LOG.warn("There was JSON in the message, but it was not a JSON object: {}", message);
                return null;
            }
        } else {
            LOG.warn("Could not find a start an end JSON object in Exception message: {}", message);
            return null;
        }

    }

    private AddressVerificationError buildGenericVerificationError() {
        return new AddressVerificationError("Unable to verify address.", "E.ADDRESS.NOT_FOUND");
    }

    private Map<String, Object> convertToEasyPostAddressMap(MailAddress address) {
        Map<String, Object> addressMap = new HashMap<>();
        // we are not company in validation
        // including phone because EasyPost suggests a reformat
        // not including plusCode in easypost address since separate concept
        addressMap.put("phone", address.getPhone());
        addressMap.put("name", address.getName());
        addressMap.put("street1", address.getStreet1());
        addressMap.put("street2", address.getStreet2());
        addressMap.put("city", address.getCity());
        addressMap.put("state", address.getState());
        addressMap.put("zip", address.getZip());
        addressMap.put("country", address.getCountry());
        addressMap.put("verify_strict", Collections.singletonList("delivery"));
        return addressMap;

    }

    private JdbiMailAddress buildAddressDao(Handle handle) {
        return handle.attach(JdbiMailAddress.class);
    }

    private JdbiCountryAddressInfo buildCountryInfoDao(Handle handle) {
        return handle.attach(JdbiCountryAddressInfo.class);
    }


    private String getEasyPostApiKey() {
        return this.easyPostApiKey;
    }


}
