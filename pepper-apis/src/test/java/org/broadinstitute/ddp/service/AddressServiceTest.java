package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS;
import static org.broadinstitute.ddp.service.DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.exception.AddressVerificationException;
import org.broadinstitute.ddp.model.address.AddressWarning;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.kit.KitZipCodeRule;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AddressServiceTest extends TxnAwareBaseTest {

    private static final String TEST_PLUSCODE = "87JC9W76+CF";

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;

    // The logic around EasyPost's address verification is a little tricky, so we're only mocking
    // the OLCService here and letting the EasyPostClient pass through to the real service.
    private OLCService mockOLC;
    private AddressService service;

    @BeforeClass
    public static void setupTestData() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = testData.getUserGuid();
        });
    }

    @Before
    public void init() {
        mockOLC = mock(OLCService.class);
        service = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY), mockOLC);
        when(mockOLC.calculateFullPlusCode(any())).thenReturn(TEST_PLUSCODE);
    }

    @Test
    public void testAddAddress_suggested() {
        MailAddress addr = buildTestAddress();
        addr.setStreet1("75 AMES ST");
        addr.setCity("CAMBRIDGE");
        addr.setZip("02142-1403");
        TransactionWrapper.useTxn(handle -> {
            MailAddress saved = service.addAddress(handle, addr, userGuid, userGuid);
            MailAddress actual = service.findAddressByGuid(handle, saved.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(addr.getGuid(), actual.getGuid());
            assertEquals(DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS.getCode(), actual.getValidationStatus());
            handle.rollback();
        });
    }

    @Test
    public void testAddAddress_valid() {
        MailAddress addr = buildTestAddress();
        TransactionWrapper.useTxn(handle -> {
            MailAddress saved = service.addAddress(handle, addr, userGuid, userGuid);
            MailAddress actual = service.findAddressByGuid(handle, saved.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(addr.getGuid(), actual.getGuid());
            assertEquals(DSM_VALID_ADDRESS_STATUS.getCode(), actual.getValidationStatus());
            handle.rollback();
        });
    }

    @Test
    public void testAddAddress_invalid() {
        MailAddress addr = buildTestAddress();
        addr.setStreet1("75 Nowhere Street");
        TransactionWrapper.useTxn(handle -> {
            MailAddress saved = service.addAddress(handle, addr, userGuid, userGuid);
            MailAddress actual = service.findAddressByGuid(handle, saved.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(addr.getGuid(), actual.getGuid());
            assertEquals(DSM_INVALID_ADDRESS_STATUS.getCode(), actual.getValidationStatus());
            handle.rollback();
        });
    }

    @Test
    public void testAddAddress_international() {
        MailAddress addr = buildInternationalAddress();
        TransactionWrapper.useTxn(handle -> {
            MailAddress saved = service.addAddress(handle, addr, userGuid, userGuid);
            MailAddress actual = service.findAddressByGuid(handle, saved.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(addr.getGuid(), actual.getGuid());
            handle.rollback();
        });
    }

    @Test
    public void testUpdateAddress_validationStatus() {
        MailAddress addr = buildTestAddress();
        addr.setStreet1("75 AMES ST");
        addr.setCity("CAMBRIDGE");
        addr.setZip("02142-1403");
        TransactionWrapper.useTxn(handle -> {
            MailAddress original = service.addAddress(handle, addr, userGuid, userGuid);
            String originalCity = original.getCity();
            String originalZip = original.getZip();

            // Update to invalid
            original.setCity("NOHWERETOWN");
            original.setZip("99999");
            assertTrue(service.updateAddress(handle, original.getGuid(), original, userGuid, userGuid));

            MailAddress updated = service.findAddressByGuid(handle, original.getGuid()).orElse(null);
            assertNotNull(updated);
            assertEquals(DSM_INVALID_ADDRESS_STATUS.getCode(), updated.getValidationStatus());

            // Restore to original
            updated.setCity(originalCity);
            updated.setZip(originalZip);
            assertTrue(service.updateAddress(handle, updated.getGuid(), updated, userGuid, userGuid));
            MailAddress actual = service.findAddressByGuid(handle, updated.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS.getCode(), actual.getValidationStatus());

            handle.rollback();
        });
    }

    @Test
    public void testUpdateAddress_international() {
        MailAddress addr = buildInternationalAddress();
        TransactionWrapper.useTxn(handle -> {
            MailAddress saved = service.addAddress(handle, addr, userGuid, userGuid);

            addr.setState("NOR");
            assertTrue(service.updateAddress(handle, saved.getGuid(), addr, userGuid, userGuid));

            MailAddress actual = service.findAddressByGuid(handle, saved.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals("NOR", actual.getState());

            handle.rollback();
        });
    }

    @Test
    public void testValidateAddress_good() {
        MailAddress addr = buildTestAddress();
        List<JsonValidationError> validationErrors = TransactionWrapper
                .withTxn(handle -> service.validateAddress(handle, addr));
        assertEquals(0, validationErrors.size());
    }

    @Test
    public void testValidateAddress_withBadCountry() {
        MailAddress addr = buildTestAddress();
        addr.setCountry("BAD");
        List<JsonValidationError> validationErrors = TransactionWrapper
                .withTxn(handle -> service.validateAddress(handle, addr));
        assertEquals(1, validationErrors.size());
        assertTrue(validationErrors.get(0).getPropertyPath().contains("country"));
    }

    @Test
    public void testValidateAddress_withBadState() {
        MailAddress addr = buildTestAddress();
        addr.setState("ZZ");
        List<JsonValidationError> validationErrors = TransactionWrapper
                .withTxn(handle -> service.validateAddress(handle, addr));
        assertEquals(1, validationErrors.size());
        assertTrue(validationErrors.get(0).getPropertyPath().contains("state"));
    }

    @Test
    public void testVerifyAddress_good() {
        MailAddress addr = buildTestAddress();
        addr.setStreet1("75 Ames St");

        MailAddress verified = service.verifyAddress(addr);

        assertNotNull(verified);
        assertEquals(addr.getName().toUpperCase(), verified.getName());
        assertEquals(addr.getStreet1().toUpperCase(), verified.getStreet1());
        assertTrue(StringUtils.isEmpty(verified.getStreet2()));
        assertEquals(addr.getCity().toUpperCase(), verified.getCity().toUpperCase());
        assertEquals(addr.getState().toUpperCase(), verified.getState().toUpperCase());
        assertEquals(addr.getCountry().toUpperCase(), verified.getCountry().toUpperCase());
        assertEquals(addr.getPlusCode(), verified.getPlusCode());
        assertTrue(verified.getZip().startsWith(addr.getZip()));
    }

    @Test
    public void testVerifyAddress_bad() {
        MailAddress addr = new MailAddress();
        addr.setStreet1("666 Nowhere Street");
        try {
            service.verifyAddress(addr);
            fail("expected exception not thrown");
        } catch (AddressVerificationException e) {
            assertNotNull(e.getError());
            assertNotNull(e.getError().getMessage());
            assertNotNull(e.getError().getCode());
            assertNotNull(e.getError().getErrors());
            assertFalse(e.getError().getErrors().isEmpty());
        }
    }

    @Test
    public void testCheckStudyAddress() {
        MailAddress addr = buildTestAddress();
        var kitConfig = new KitConfiguration(1L, 1, new KitType(1L, "SALIVA"), testData.getStudyGuid(), List.of(
                new KitZipCodeRule(1L, Set.of("12345", "02115"))));

        var mockHandle = mock(Handle.class);
        var mockKitDao = mock(KitConfigurationDao.class);
        doReturn(mockKitDao).when(mockHandle).attach(KitConfigurationDao.class);
        doReturn(List.of(kitConfig)).when(mockKitDao).findStudyKitConfigurations(anyLong());

        addr.setZip("02115");
        List<AddressWarning> actual = service.checkStudyAddress(mockHandle, testData.getStudyId(), addr);
        assertNotNull(actual);
        assertTrue(actual.isEmpty());

        addr.setZip("02161");
        actual = service.checkStudyAddress(mockHandle, testData.getStudyId(), addr);
        assertEquals(1, actual.size());
        assertEquals(AddressWarning.Warn.ZIP_UNSUPPORTED.getCode(), actual.get(0).getCode());
    }

    private MailAddress buildTestAddress() {
        MailAddress addr = new MailAddress();
        addr.setName("RHONDA ROUSEY");
        addr.setStreet1("75 Ames Street");
        addr.setCity("Cambridge");
        addr.setState("MA");
        addr.setZip("02142");
        addr.setCountry("US");
        addr.setPlusCode(TEST_PLUSCODE);
        return addr;
    }

    private MailAddress buildInternationalAddress() {
        MailAddress addr = new MailAddress();
        addr.setName("Foo Bar");
        addr.setStreet1("27 Avenue Pasteur");
        addr.setCity("Cabourg");
        addr.setState("Normandy");
        addr.setZip("14390");
        addr.setCountry("FR");
        return addr;
    }
}
