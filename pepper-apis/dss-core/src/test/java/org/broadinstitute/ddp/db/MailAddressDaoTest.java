package org.broadinstitute.ddp.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.MailAddressWithStrictValidationRules;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class MailAddressDaoTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String TEST_USER_GUID;
    private List<Long> idsToDelete = new ArrayList<>();

    @BeforeClass
    public static void setupTestUser() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            TEST_USER_GUID = testData.getTestingUser().getUserGuid();
        });
    }

    @After
    public void breakDown() {
        for (Long id : idsToDelete) {
            TransactionWrapper.withTxn(handle -> {
                JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
                if (id != null) {
                    return dao.deleteAddress(id);
                } else {
                    return false;
                }
            });
        }
        return;
    }

    @Test
    public void testAddressValidation() {
        MailAddress addressToTest = buildTestWithStrictValidationAddress();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<MailAddress>> constraintViolations = validator.validate(addressToTest);
        assertEquals(0, constraintViolations.size());
        addressToTest.setName(null);
        constraintViolations = validator.validate(addressToTest);
        assertEquals(1, constraintViolations.size());
        addressToTest.setStreet2(StringUtils.leftPad("Hello", 101, '!'));
        constraintViolations = validator.validate(addressToTest);
        assertEquals(2, constraintViolations.size());
    }

    @Test
    public void testInsertAddress() {
        MailAddress addressToBeSaved = buildTestAddress();
        MailAddress savedAddress = insertTestAddress(addressToBeSaved, TEST_USER_GUID, TEST_USER_GUID);
        assertNotNull(savedAddress);
        assertNotNull(savedAddress.getGuid());
        assertNotNull(savedAddress.getId());
        Optional<MailAddress> retrievedTestAddressOptional = TransactionWrapper.withTxn(handle -> {
            JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
            return dao.findAddressByGuid(savedAddress.getGuid());
        });
        assertTrue(retrievedTestAddressOptional.isPresent());
        MailAddress retrievedAddress = retrievedTestAddressOptional.get();
        checkFieldsEqual(savedAddress, retrievedAddress);

    }

    @Test
    public void testInsertBlankAddress() {
        MailAddress blankAddress = new MailAddress();
        MailAddress savedBlankAddress = insertTestAddress(blankAddress, TEST_USER_GUID, TEST_USER_GUID);
        assertNotNull(savedBlankAddress.getGuid());

        Optional<MailAddress> retrievedTestAddressOptional = TransactionWrapper.withTxn(handle -> {
            JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
            return dao.findAddressByGuid(savedBlankAddress.getGuid());
        });
        assertTrue(retrievedTestAddressOptional.isPresent());
        MailAddress retrievedAddress = retrievedTestAddressOptional.get();
        checkFieldsEqual(savedBlankAddress, retrievedAddress);
    }

    @Test
    public void testGetAllAddresses() {
        MailAddress addressToBeSaved1 = buildTestAddress();
        String name1 = UUID.randomUUID().toString();
        addressToBeSaved1.setName(name1);
        MailAddress savedAddress1 = insertTestAddress(addressToBeSaved1, TEST_USER_GUID, TEST_USER_GUID);

        List<MailAddress> allAddressesAfter1Insertion = TransactionWrapper.withTxn(handle -> {
            JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
            return dao.findAllAddressesForParticipant(TEST_USER_GUID);
        });
        assertTrue(allAddressesAfter1Insertion.size() >= 1);
        assertTrue(allAddressesAfter1Insertion.stream().anyMatch(address -> address.getName().equals(name1)));

        MailAddress addressToBeSaved2 = buildTestAddress();
        String name2 = UUID.randomUUID().toString();
        addressToBeSaved2.setName(name2);
        addressToBeSaved2.setDescription("Some other one");
        MailAddress savedAddress2 = insertTestAddress(addressToBeSaved2, TEST_USER_GUID, TEST_USER_GUID);
        List<MailAddress> allAddressesAfter2Insertions = TransactionWrapper.withTxn(handle -> {
            JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
            return dao.findAllAddressesForParticipant(TEST_USER_GUID);
        });
        assertTrue(allAddressesAfter2Insertions.size() >= 2);
        assertTrue(allAddressesAfter2Insertions.stream().anyMatch(address -> address.getName().equals(name1)));
        assertTrue(allAddressesAfter2Insertions.stream().anyMatch(address -> address.getName().equals(name2)));
        checkFieldsEqual(savedAddress2, allAddressesAfter2Insertions.stream()
                .filter(add -> add.getName().equals(name2)).findFirst().get());


    }

    protected void checkFieldsEqual(MailAddress original, MailAddress retrievedAddress) {
        assertEquals(original.getId(), retrievedAddress.getId());
        assertEquals(original.getGuid(), retrievedAddress.getGuid());
        assertEquals(original.getState(), retrievedAddress.getState());
        assertEquals(original.getCountry(), retrievedAddress.getCountry());
        assertEquals(original.getState(), retrievedAddress.getState());
        assertEquals(original.getCity(), retrievedAddress.getCity());
        assertEquals(original.getZip(), retrievedAddress.getZip());
        assertEquals(original.getStreet1(), retrievedAddress.getStreet1());
        assertEquals(original.getStreet2(), retrievedAddress.getStreet2());
        assertEquals(original.getPhone(), retrievedAddress.getPhone());
        assertEquals(original.getName(), retrievedAddress.getName());
        assertEquals(original.getDescription(), retrievedAddress.getDescription());
        assertEquals(original.getValidationStatus(), retrievedAddress.getValidationStatus());
    }


    @Test
    public void testUpdateAllFields() {
        MailAddress originalAddress = buildTestAddress();
        MailAddress savedAddress = insertTestAddress(originalAddress, TEST_USER_GUID, TEST_USER_GUID);
        MailAddress updatedAddress = new MailAddress("New Name", "New Street 1", "New Street 2",
                "Big City", "QC", "CA", "H23 ZYZ", "999-111-2222", "87JC9W76+5G",
                "some description", DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS, true);
        int rowsUpdated = updateAddress(originalAddress.getGuid(), updatedAddress, TEST_USER_GUID, TEST_USER_GUID);
        assertEquals(1, rowsUpdated);
        Optional<MailAddress> retrievedUpdatedAddress = TransactionWrapper.withTxn(handle -> {
            JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
            return dao.findAddressByGuid(savedAddress.getGuid());
        });
        assertTrue(retrievedUpdatedAddress.isPresent());
        //just so that comparison is valid...
        updatedAddress.setId(savedAddress.getId());
        updatedAddress.setGuid(savedAddress.getGuid());
        checkFieldsEqual(updatedAddress, retrievedUpdatedAddress.get());
    }

    @Test
    public void testSetAddressAsDefault() {
        TransactionWrapper.withTxn(handle -> {
            JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
            MailAddress originalAddress = buildTestAddress();
            MailAddress originalSavedAddress = insertTestAddress(originalAddress, TEST_USER_GUID,
                    TEST_USER_GUID, handle);
            Optional<MailAddress> retrievedSavedAddress = dao.findAddressByGuid(originalSavedAddress.getGuid());
            assertTrue(retrievedSavedAddress.isPresent());
            assertFalse(retrievedSavedAddress.get().isDefault());

            dao.setDefaultAddressForParticipant(originalSavedAddress.getGuid());
            Optional<MailAddress> addressAfterSetToDefault = dao.findAddressByGuid(originalSavedAddress.getGuid());

            assertTrue(addressAfterSetToDefault.isPresent());
            assertTrue(addressAfterSetToDefault.get().isDefault());

            MailAddress newAddress = buildTestAddress();
            newAddress.setDescription("Another one");
            newAddress.setName(UUID.randomUUID().toString());
            MailAddress newSavedAddress = insertTestAddress(newAddress, TEST_USER_GUID, TEST_USER_GUID, handle);
            Optional<MailAddress> retrievedNewAddress = dao.findAddressByGuid(newSavedAddress.getGuid());
            assertTrue(retrievedNewAddress.isPresent());
            assertFalse(retrievedNewAddress.get().isDefault());

            dao.setDefaultAddressForParticipant(newSavedAddress.getGuid());
            Optional<MailAddress> newAddressAferSettingItToDefault = dao.findAddressByGuid(newSavedAddress.getGuid());
            assertTrue(newAddressAferSettingItToDefault.isPresent());
            assertTrue(newAddressAferSettingItToDefault.get().isDefault());

            //Check that the original default is no logner
            retrievedSavedAddress = dao.findAddressByGuid(originalSavedAddress.getGuid());
            assertTrue(retrievedSavedAddress.isPresent());
            assertFalse(retrievedSavedAddress.get().isDefault());

            //and finally, unset the default
            dao.unsetDefaultAddressForParticipant(newSavedAddress.getGuid());
            Optional<MailAddress> retrievedUnsetAddress = dao.findAddressByGuid(newSavedAddress.getGuid());
            assertFalse(retrievedUnsetAddress.get().isDefault());

            //     dao.setAddressAsDefault(TEST_USER_GUID, savedAddress.getGuid());
            return null;
        });

    }

    private MailAddress buildTestAddress() {
        String name = "Potato Head";
        String street1 = "300 Spud Lane";
        String street2 = "Apartment # 6";
        String city = "Boise";
        String state = "ID";
        String country = "US";
        String zip = "99666";
        String phone = "617-867-5309";
        String plusCode = "87JC9W76+5G";
        String description = "The description";

        return new MailAddress(name, street1, street2, city, state, country, zip, phone, plusCode, description,
                DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS, false);
    }

    private MailAddress buildTestWithStrictValidationAddress() {
        String name = "Potato Head";
        String street1 = "300 Spud Lane";
        String street2 = "Apartment # 6";
        String city = "Boise";
        String state = "ID";
        String country = "US";
        String zip = "99666";
        String phone = "617-867-5309";
        String plusCode = "87JC9W76+5G";
        String description = "The description";

        return new MailAddressWithStrictValidationRules(name, street1, street2, city, state, country, zip, phone, description,
                DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS, plusCode, false);
    }

    private MailAddress insertTestAddress(MailAddress testAddress, String participantGuid, String creatorGuid) {
        return TransactionWrapper.withTxn(handle -> insertTestAddress(testAddress, participantGuid, creatorGuid, handle));
    }

    private MailAddress insertTestAddress(MailAddress testAddress, String participantGuid,
                                          String creatorGuid, Handle handle) {
        JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
        MailAddress savedAddress = dao.insertAddress(testAddress, participantGuid, creatorGuid);
        idsToDelete.add(savedAddress.getId());
        return savedAddress;
    }

    private int updateAddress(String guid, MailAddress addressWithUpdatedFields, String participantGuid,
                              String creatorGuid) {
        return TransactionWrapper.withTxn(handle -> {
            JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
            return dao.updateAddress(guid, addressWithUpdatedFields, participantGuid, creatorGuid);

        });

    }

    private Set<ConstraintViolation<MailAddress>> validateAddress(MailAddress address) {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        return validator.validate(address);
    }
}
