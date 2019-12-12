package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.AddressVerificationException;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class AddressServiceTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData data;
    private static String userGuid;

    private static AddressService service;
    private Set<String> mailAddressesToDelete = new HashSet<>();

    @BeforeClass
    public static void setupTestData() {
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = data.getUserGuid();
        });
        service = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY), cfg.getString(ConfigFile.GEOCODING_API_KEY));
    }

    @After
    public void breakdown() {
        TransactionWrapper.useTxn(handle -> mailAddressesToDelete.forEach(guid -> assertTrue(service.deleteAddress(handle, guid))));
    }

    @Test
    public void testVerifyGoodAddress() {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("Abraham Lincoln");
        testAddress.setStreet1("415 Main Street");
        testAddress.setCity("Cambridge");
        testAddress.setState("MA");
        testAddress.setZip("02142");
        testAddress.setCountry("US");
        testAddress.setPlusCode("87JC9W76+5G");
        MailAddress verifiedAddress = service.verifyAddress(testAddress);
        assertEquals(testAddress.getName().toUpperCase(), verifiedAddress.getName());
        assertEquals("415 MAIN ST", verifiedAddress.getStreet1());
        assertTrue(StringUtils.isEmpty(verifiedAddress.getStreet2()));
        assertEquals(testAddress.getCity().toUpperCase(), verifiedAddress.getCity().toUpperCase());
        assertEquals(testAddress.getState().toUpperCase(), verifiedAddress.getState().toUpperCase());
        assertEquals(testAddress.getCountry().toUpperCase(), verifiedAddress.getCountry().toUpperCase());
        assertEquals(testAddress.getPlusCode(), verifiedAddress.getPlusCode());
        assertTrue(verifiedAddress.getZip().startsWith(testAddress.getZip()));
        System.out.println(new Gson().toJson(verifiedAddress));
        assertNotNull(verifiedAddress);
    }

    @Test
    public void testVerifyBadAddress() {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("Abraham Lincoln");
        testAddress.setStreet1("666 Nowhere Street");
        testAddress.setCity("Cambridge");
        testAddress.setState("MA");
        testAddress.setZip("02142");
        testAddress.setCountry("US");
        testAddress.setPlusCode("87JC9W76+5G");
        boolean exceptionOccurred = false;
        try {
            MailAddress verifiedAddress = service.verifyAddress(testAddress);
            assertNotNull(verifiedAddress);
        } catch (AddressVerificationException e) {
            exceptionOccurred = true;
            assertNotNull(e.getError());
            assertTrue(e.getError() instanceof AddressVerificationError);
            assertNotNull(e.getError().getMessage());
            assertNotNull(e.getError().getCode());
            assertNotNull(e.getError().getErrors());
            assertFalse(e.getError().getErrors().isEmpty());
        }
        assertTrue(exceptionOccurred);

    }

    @Test
    public void testValidateGoodAddress() {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("Abraham Lincoln");
        testAddress.setStreet1("666 Nowhere Street");
        testAddress.setCity("Cambridge");
        testAddress.setState("MA");
        testAddress.setZip("02142");
        testAddress.setCountry("US");
        testAddress.setPlusCode("87JC9W76+5G");
        List<JsonValidationError> validationErrors = service.validateAddress(testAddress);
        assertEquals(0, validationErrors.size());
    }

    @Test
    public void testValidateAddressWithBadCountry() {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("Abraham Lincoln");
        testAddress.setStreet1("666 Nowhere Street");
        testAddress.setCity("Cambridge");
        testAddress.setState("MA");
        testAddress.setZip("02142");
        testAddress.setCountry("BAD");
        testAddress.setPlusCode("87JC9W76+5G");
        List<JsonValidationError> validationErrors = service.validateAddress(testAddress);
        assertEquals(1, validationErrors.size());
        assertTrue(validationErrors.get(0).getPropertyPath().contains("country"));
    }

    @Test
    public void testValidateAddressWithBadState() {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("Abraham Lincoln");
        testAddress.setStreet1("666 Nowhere Street");
        testAddress.setCity("Cambridge");
        testAddress.setState("ZZ");
        testAddress.setZip("02142");
        testAddress.setCountry("US");
        testAddress.setPlusCode("87JC9W76+5G");
        List<JsonValidationError> validationErrors = service.validateAddress(testAddress);
        assertEquals(1, validationErrors.size());
        assertTrue(validationErrors.get(0).getPropertyPath().contains("state"));
    }

    @Test
    public void testAddEasyPostSuggestedAddress() {
        addEasyPostSuggestedAddress();
    }


    private MailAddress addEasyPostSuggestedAddress() {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("RHONDA ROUSEY");
        testAddress.setStreet1("75 AMES ST");
        testAddress.setCity("CAMBRIDGE");
        testAddress.setState("MA");
        testAddress.setZip("02142-1403");
        testAddress.setCountry("US");
        MailAddress savedMailAddress = service.addAddress(testAddress, userGuid, userGuid);
        mailAddressesToDelete.add(savedMailAddress.getGuid());
        Optional<MailAddress> addressFromServer
                = service.findAddressByGuid(savedMailAddress.getGuid());
        assertTrue(addressFromServer.isPresent());
        assertNotNull(addressFromServer.get().getGuid());
        assertEquals(DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS.getCode(), addressFromServer.get().getValidationStatus());
        return addressFromServer.get();
    }

    @Test
    public void testAddEasyPostValidAddress() {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("RHONDA ROUSEY");
        testAddress.setStreet1("75 Ames Street");
        testAddress.setCity("Cambridge");
        testAddress.setState("MA");
        testAddress.setZip("02142");
        testAddress.setCountry("US");
        testAddress.setPlusCode("87JC9W76+5G");
        MailAddress savedMailAddress = service.addAddress(testAddress, userGuid, userGuid);
        mailAddressesToDelete.add(savedMailAddress.getGuid());
        Optional<MailAddress> addressFromServer
                = service.findAddressByGuid(savedMailAddress.getGuid());
        assertTrue(addressFromServer.isPresent());
        assertNotNull(addressFromServer.get().getGuid());
        assertEquals(DSM_VALID_ADDRESS_STATUS.getCode(), addressFromServer.get().getValidationStatus());
    }

    @Test
    public void testAddEasyPostInvalidAddress() {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("RHONDA ROUSEY");
        testAddress.setStreet1("75 Nowhere Street");
        testAddress.setCity("Cambridge");
        testAddress.setState("MA");
        testAddress.setZip("02142");
        testAddress.setCountry("US");
        testAddress.setPlusCode("87JC9W76+5G");
        MailAddress savedMailAddress = service.addAddress(testAddress, userGuid, userGuid);
        mailAddressesToDelete.add(savedMailAddress.getGuid());
        Optional<MailAddress> addressFromServer
                = service.findAddressByGuid(savedMailAddress.getGuid());
        assertTrue(addressFromServer.isPresent());
        assertNotNull(addressFromServer.get().getGuid());
        assertEquals(DSM_INVALID_ADDRESS_STATUS.getCode(), addressFromServer.get().getValidationStatus());
    }

    @Test
    public void testUpdateWithValidationStatusCheck() {
        MailAddress suggestedAddressFromServer = addEasyPostSuggestedAddress();
        //save the good values
        String originalCity = suggestedAddressFromServer.getCity();
        String originalZip = suggestedAddressFromServer.getZip();
        //mess up the address a bit
        suggestedAddressFromServer.setCity("NOHWERETOWN");
        suggestedAddressFromServer.setZip("99999");
        boolean updateSucceeded = service.updateAddress(suggestedAddressFromServer.getGuid(),
                suggestedAddressFromServer, userGuid, userGuid);
        assertTrue(updateSucceeded);
        Optional<MailAddress> addressAfterUpdate
                = service.findAddressByGuid(suggestedAddressFromServer.getGuid());
        assertTrue(addressAfterUpdate.isPresent());
        assertEquals(DSM_INVALID_ADDRESS_STATUS.getCode(), addressAfterUpdate.get().getValidationStatus());

        addressAfterUpdate.get().setCity(originalCity);
        addressAfterUpdate.get().setZip(originalZip);
        service.updateAddress(addressAfterUpdate.get().getGuid(),
                addressAfterUpdate.get(), userGuid, userGuid);
        Optional<MailAddress> addressAfterSecondUpdate
                = service.findAddressByGuid(addressAfterUpdate.get().getGuid(), OLCService.DEFAULT_OLC_PRECISION);
        assertTrue(addressAfterSecondUpdate.isPresent());
        assertEquals(DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS.getCode(),
                addressAfterSecondUpdate.get().getValidationStatus());
    }

    @Test
    public void testInternationalAddress_saved() {
        MailAddress addr = new MailAddress();
        addr.setName("Foo Bar");
        addr.setStreet1("27 Avenue Pasteur");
        addr.setCity("Cabourg");
        addr.setState("Normandy");
        addr.setZip("14390");
        addr.setCountry("FR");
        MailAddress saved = service.addAddress(addr, userGuid, userGuid);
        assertNotNull(saved.getGuid());
        mailAddressesToDelete.add(saved.getGuid());

        MailAddress actual = service.findAddressByGuid(saved.getGuid(), OLCService.DEFAULT_OLC_PRECISION).get();
        assertEquals("Foo Bar", actual.getName());
        assertEquals("27 Avenue Pasteur", actual.getStreet1());
        assertEquals("Cabourg", actual.getCity());
        assertEquals("Normandy", actual.getState());
        assertEquals("14390", actual.getZip());
        assertEquals("FR", actual.getCountry());
        assertEquals(saved.getPlusCode(), actual.getPlusCode());
    }

    @Test
    public void testInternationalAddress_updated() {
        MailAddress addr = new MailAddress();
        addr.setName("Foo Bar");
        addr.setStreet1("27 Avenue Pasteur");
        addr.setCity("Cabourg");
        addr.setState("Normandy");
        addr.setZip("14390");
        addr.setCountry("FR");
        MailAddress saved = service.addAddress(addr, userGuid, userGuid);
        assertNotNull(saved.getGuid());
        mailAddressesToDelete.add(saved.getGuid());

        addr.setState("NOR");
        service.updateAddress(saved.getGuid(), addr, userGuid, userGuid);

        MailAddress actual = service.findAddressByGuid(saved.getGuid(), OLCService.DEFAULT_OLC_PRECISION).get();
        assertEquals("Foo Bar", actual.getName());
        assertEquals("27 Avenue Pasteur", actual.getStreet1());
        assertEquals("Cabourg", actual.getCity());
        assertEquals("NOR", actual.getState());
        assertEquals("14390", actual.getZip());
        assertEquals("FR", actual.getCountry());
        assertEquals(saved.getPlusCode(), actual.getPlusCode());
    }
}
