package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.OLCService.DEFAULT_OLC_PRECISION;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.AddressVerificationException;
import org.broadinstitute.ddp.client.EasyPostClient;
import org.broadinstitute.ddp.client.EasyPostVerifyError;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiCountryAddressInfo;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.model.address.AddressWarning;
import org.broadinstitute.ddp.model.address.CountryAddressInfo;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.kit.KitRule;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.model.kit.KitZipCodeRule;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;

/**
 * Main entry-point for back-end operations related to mailing address.
 */
@Slf4j
public class AddressService {
    private final EasyPostClient easyPost;
    private final OLCService olcService;

    /**
     * This constructor can be used when needs to create the service instance for address reading
     * (for example {@link #findAddressByGuid(Handle, String)}
     */
    public AddressService() {
        this((String) null, (String) null);
    }

    public AddressService(String easyPostApiKey, String geocodingKey) {
        this(easyPostApiKey, new OLCService(geocodingKey));
    }

    public AddressService(String easyPostApiKey, OLCService olcService) {
        this(new EasyPostClient(easyPostApiKey), olcService);
    }

    public AddressService(EasyPostClient easyPostClient, OLCService olcService) {
        this.easyPost = easyPostClient;
        this.olcService = olcService;
    }

    /**
     * Inserts new address. Sets the validation status checking against EasyPost before saving.
     *
     * @param handle            the database handle
     * @param addressFromClient the address to save
     * @param participantGuid   the user guid for the participant
     * @param operatorGuid      the user guid for the operator
     * @return the saved address with new guid and validation flag assigned
     */
    public MailAddress addAddress(Handle handle, MailAddress addressFromClient, String participantGuid, String operatorGuid) {
        addressFromClient.setPlusCode(olcService.calculateFullPlusCode(addressFromClient));
        DsmAddressValidationStatus validationStatus = calculateAddressValidationStatus(addressFromClient);
        addressFromClient.setValidationStatus(validationStatus);
        JdbiMailAddress dao = buildAddressDao(handle);
        dao.insertAddress(addressFromClient, participantGuid, operatorGuid);
        setAddressIsDefaultFlag(addressFromClient, dao);
        return addressFromClient;
    }

    /**
     * Insert a copy of already existing address (which already was successfully added to the database and
     * having plus code and validation status calculated.
     *
     * @param handle          the database handle
     * @param copiedAddress   the address to save
     * @param participantGuid the user guid for the participant
     * @param operatorGuid    the user guid for the operator
     * @return the saved address with new guid
     */
    public MailAddress addExistingAddress(Handle handle, MailAddress copiedAddress, String participantGuid, String operatorGuid) {
        JdbiMailAddress dao = buildAddressDao(handle);
        dao.insertAddress(copiedAddress, participantGuid, operatorGuid);
        setAddressIsDefaultFlag(copiedAddress, dao);
        return copiedAddress;
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
     * @param handle          the database handle
     * @param addressGuid     address guid
     * @param address         the address with the new properties to be updated
     * @param participantGuid the  participant user guid
     * @param operatorGuid    the operator user guid
     * @return true if the address was found and updated. False otherwise
     */
    public boolean updateAddress(Handle handle, String addressGuid, MailAddress address, String participantGuid, String operatorGuid) {
        JdbiMailAddress dao = buildAddressDao(handle);
        setAddressIsDefaultFlag(address, dao);

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
     * Delete the address with specified guid
     *
     * @param addressGuid the address guid
     * @return true if found and deleted, false otherwise
     */
    public boolean deleteAddress(Handle handle, String addressGuid) {
        return handle.attach(JdbiMailAddress.class).deleteAddressByGuid(addressGuid);
    }

    /**
     * Return the default address for the the participant guid
     *
     * @param participantGuid the participant {@code User} guid
     * @return an {@code Optional} which might or might not be empty depending on existence of a default address.
     */
    public Optional<MailAddress> findDefaultAddressForParticipant(Handle handle, String participantGuid, OLCPrecision precision) {
        return buildAddressDao(handle)
                .findDefaultAddressForParticipant(participantGuid)
                .map(addr -> {
                    addr.setPlusCode(OLCService.convertPlusCodeToPrecision(addr.getPlusCode(), precision));
                    return addr;
                });
    }

    public Optional<MailAddress> findDefaultAddressForParticipant(Handle handle, String participantGuid) {
        return findDefaultAddressForParticipant(handle, participantGuid, DEFAULT_OLC_PRECISION);
    }

    public Optional<MailAddress> findDefaultAddressForParticipantWithRetries(Handle handle, String participantGuid, int retries) {
        do {
            Optional<MailAddress> optionalAddress = findDefaultAddressForParticipant(handle, participantGuid);
            if (optionalAddress.isPresent()) {
                return optionalAddress;
            } else {
                try {
                    log.warn("Could not find default address for participant with guid {}. Waiting to retry", participantGuid);
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    log.error("Sleep while waiting to retry findDefaultAddressForParticipant was interrupted", e);
                }
            }
        } while (retries-- > 0);
        return Optional.empty();
    }

    /**
     * Return the corresponding address
     *
     * @param guid the address guid
     * @return an {@code Optional} for the address. Will be empty if not found
     */
    public Optional<MailAddress> findAddressByGuid(Handle handle, String guid, OLCPrecision precision) {
        return buildAddressDao(handle)
                .findAddressByGuid(guid)
                .map(addr -> {
                    addr.setPlusCode(OLCService.convertPlusCodeToPrecision(addr.getPlusCode(), precision));
                    return addr;
                });
    }

    public Optional<MailAddress> findAddressByGuid(Handle handle, String guid) {
        return findAddressByGuid(handle, guid, DEFAULT_OLC_PRECISION);
    }

    /**
     * Return all the addresses associated with the participant in order of creation time
     *
     * @param participantGuid the user guid for the participant
     * @return the list of addresses sorted by creation time
     */
    public List<MailAddress> findAllAddressesForParticipant(Handle handle, String participantGuid, OLCPrecision precision) {
        List<MailAddress> addresses = buildAddressDao(handle).findAllAddressesForParticipant(participantGuid);
        for (MailAddress address : addresses) {
            address.setPlusCode(OLCService.convertPlusCodeToPrecision(address.getPlusCode(), precision));
        }
        return addresses;
    }

    public List<MailAddress> findAllAddressesForParticipant(Handle handle, String participantGuid) {
        return findAllAddressesForParticipant(handle, participantGuid, DEFAULT_OLC_PRECISION);
    }

    /**
     * Do second-order validation of MailAddress object (stuff that cannot be checked by simple validation annotations.
     *
     * @param address address to validate
     * @return the list of errors found. Empty if none found.
     */
    public List<JsonValidationError> validateAddress(Handle handle, MailAddress address) {
        List<JsonValidationError> errors = new ArrayList<>();
        JdbiCountryAddressInfo countryInfoDao = buildCountryInfoDao(handle);
        Optional<CountryAddressInfo> countryInfo = countryInfoDao.getCountryAddressInfo(address.getCountry());
        if (countryInfo.isEmpty()) {
            errors.add(new JsonValidationError(Collections.singletonList("country"),
                    "Country code: " + address.getCountry() + " is not recognized", address.getCountry()));
        } else {
            if (countryInfo.get().getSubnationDisivisionByCode(address.getState()).isEmpty()) {
                errors.add(new JsonValidationError(Collections.singletonList("state"),
                        "State code: " + address.getState() + " could not be found", address.getState()));
            }
        }
        return errors;
    }

    /**
     * Verify provided address using EasyPost service. Will find problems in given address and, if possible, return a
     * suggested address.
     *
     * @param address the address
     * @return an address with suggested changes
     * @throws AddressVerificationException if EasyPost is unable to verify the address. the contained {@code
     *                                      EasyPostVerifyError} could include more specific error information,
     *                                      including field specific errors.
     */
    public MailAddress verifyAddress(MailAddress address) throws AddressVerificationException {
        var result = easyPost.createAndVerify(address);
        result.runIfThrown(e -> {
            log.warn("Failed to verify address with EasyPost, returning generic error", e);
            throw new AddressVerificationException(buildGenericVerificationError());
        });
        if (result.getStatusCode() == 200) {
            MailAddress verified = EasyPostClient.convertToMailAddress(result.getBody());
            verified.setPlusCode(olcService.calculateFullPlusCode(verified));
            return verified;
        } else {
            throw new AddressVerificationException(result.getError());
        }
    }

    /**
     * Check the given address against study configuration for any potential warnings.
     *
     * @param handle   the database handle
     * @param studyId  the study id
     * @param address  the address
     * @param langCode the language code
     * @return list of warnings, or empty
     */
    public List<AddressWarning> checkStudyAddress(Handle handle, long studyId, String langCode, MailAddress address) {
        List<List<KitRule>> kitZipCodeRules = handle.attach(KitConfigurationDao.class)
                .findStudyKitConfigurations(studyId)
                .stream()
                .map(kit -> kit.getRules().stream()
                        .filter(rule -> rule.getType() == KitRuleType.ZIP_CODE)
                        .collect(Collectors.toList()))
                .filter(rules -> !rules.isEmpty())
                .collect(Collectors.toList());

        List<AddressWarning> warns = new ArrayList<>();
        if (!kitZipCodeRules.isEmpty()) {
            log.info("Checking address zip code against kit configurations for study with id {}", studyId);
            String zipCode = StringUtils.defaultString(address.getZip(), "");
            for (var rules : kitZipCodeRules) {
                boolean matched = rules.stream().anyMatch(rule -> rule.validate(handle, zipCode));
                if (!matched) {
                    log.warn("Address zip code does not match, studyId={} zipCode={}", studyId, zipCode);
                    String msg = AddressWarning.Warn.ZIP_UNSUPPORTED.getMessage();
                    Long warningTmplId = rules.stream()
                            .map(rule -> ((KitZipCodeRule) rule).getWarningMessageTemplateId())
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
                    if (warningTmplId != null) {
                        // Query using the current time to get the latest template.
                        msg = handle.attach(TemplateDao.class)
                                .loadTemplateByIdAndTimestamp(warningTmplId, Instant.now().toEpochMilli())
                                .render(langCode);
                    }
                    warns.add(new AddressWarning(AddressWarning.Warn.ZIP_UNSUPPORTED.getCode(), msg));
                    break;
                }
            }
        }

        return warns;
    }

    /**
     * Snapshot mail address.
     *
     * <b>Algorithm:</b>
     * <ul>
     *     <li>find default address;</li>
     *     <li>create a copy of the default address (but setting it as non-default);</li>
     *     <li>save address.guid to activity_instance_substitution with key ADDRESS_GUID.</li>
     * </ul>
     *
     * @param instanceId ID of an activity instance in which substitutions to save addressGuid
     * @return new address object which is created
     */
    public MailAddress snapshotAddress(Handle handle, String participantGuid, String operatorGuid, long instanceId) {
        // find default address
        Optional<MailAddress> defaultAddress = findDefaultAddressForParticipantWithRetries(handle, participantGuid, 5);
        if (defaultAddress.isPresent()) {
            // create a copy of the default address (but setting it as non-default)
            MailAddress mailAddress = addExistingAddress(
                    handle, new MailAddress(defaultAddress.get(), false), participantGuid, operatorGuid);
            // save address.guid to activity_instance_substitution with key ADDRESS_GUID
            handle.attach(ActivityInstanceDao.class).saveSubstitutions(
                    instanceId, Map.of(I18nTemplateConstants.Snapshot.ADDRESS_GUID, mailAddress.getGuid()));
            return mailAddress;
        }
        return null;
    }

    private EasyPostVerifyError buildGenericVerificationError() {
        return new EasyPostVerifyError("E.ADDRESS.NOT_FOUND", "Unable to verify address.");
    }

    private JdbiMailAddress buildAddressDao(Handle handle) {
        return handle.attach(JdbiMailAddress.class);
    }

    private JdbiCountryAddressInfo buildCountryInfoDao(Handle handle) {
        return handle.attach(JdbiCountryAddressInfo.class);
    }
}
