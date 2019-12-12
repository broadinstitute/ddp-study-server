package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiTempMailAddressTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData data;
    private static String userGuid;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = data.getUserGuid();
        });
    }

    @Test
    public void testSaveTempAddress() {
        TransactionWrapper.useTxn(handle -> {
            JdbiTempMailAddress tempAddressDao = handle.attach(JdbiTempMailAddress.class);
            String testActivityInstanceGuid = createTestActivityInstance(handle);
            MailAddress blankTempAddress = new MailAddress();
            //save a blank one
            Long id = tempAddressDao.saveTempAddress(blankTempAddress, userGuid, userGuid,
                    testActivityInstanceGuid);

            Optional<MailAddress> blankTempAddressFromDbOptional = tempAddressDao
                    .findTempAddressByActvityInstanceGuid(testActivityInstanceGuid);
            assertTrue(blankTempAddressFromDbOptional.isPresent());
            checkFieldsEqual(blankTempAddress, blankTempAddressFromDbOptional.get());

            MailAddress addressWithSomeData = blankTempAddress;
            addressWithSomeData.setCountry("CA");
            saveRetrieveAndCheckTempAddress(addressWithSomeData, userGuid, userGuid,
                    testActivityInstanceGuid, tempAddressDao);

            addressWithSomeData.setZip("96666");
            saveRetrieveAndCheckTempAddress(addressWithSomeData, userGuid, userGuid,
                    testActivityInstanceGuid, tempAddressDao);

            addressWithSomeData.setState("QC");
            saveRetrieveAndCheckTempAddress(addressWithSomeData, userGuid, userGuid,
                    testActivityInstanceGuid, tempAddressDao);

            addressWithSomeData.setCity("Montreal");
            saveRetrieveAndCheckTempAddress(addressWithSomeData, userGuid, userGuid,
                    testActivityInstanceGuid, tempAddressDao);

            addressWithSomeData.setStreet2("Near Olimpique Stadium");
            saveRetrieveAndCheckTempAddress(addressWithSomeData, userGuid, userGuid,
                    testActivityInstanceGuid, tempAddressDao);

            addressWithSomeData.setStreet2("222 Cartier Place");
            saveRetrieveAndCheckTempAddress(addressWithSomeData, userGuid, userGuid,
                    testActivityInstanceGuid, tempAddressDao);
            addressWithSomeData.setDefault(true);
            saveRetrieveAndCheckTempAddress(addressWithSomeData, userGuid, userGuid,
                    testActivityInstanceGuid, tempAddressDao);

            addressWithSomeData.setDefault(false);
            saveRetrieveAndCheckTempAddress(addressWithSomeData, userGuid, userGuid,
                    testActivityInstanceGuid, tempAddressDao);
            handle.rollback();
        });
    }

    @Test
    public void testDeleteTempAddress() {
        TransactionWrapper.useTxn(handle -> {
            JdbiTempMailAddress tempAddressDao = handle.attach(JdbiTempMailAddress.class);
            String testActivityInstanceGuid = createTestActivityInstance(handle);
            MailAddress blankTempAddress = new MailAddress();
            //save a blank one
            tempAddressDao.saveTempAddress(blankTempAddress, userGuid, userGuid, testActivityInstanceGuid);
            Optional<MailAddress> blankTempAddressFromDbOptional = tempAddressDao
                    .findTempAddressByActvityInstanceGuid(testActivityInstanceGuid);
            assertTrue(blankTempAddressFromDbOptional.isPresent());

            tempAddressDao.deleteTempAddressByActivityInstanceGuid(testActivityInstanceGuid);

            Optional<MailAddress> tempAddressAfterDeletionOptional = tempAddressDao
                    .findTempAddressByActvityInstanceGuid(testActivityInstanceGuid);
            assertFalse(tempAddressAfterDeletionOptional.isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testTempInternationalAddress() {
        TransactionWrapper.useTxn(handle -> {
            JdbiTempMailAddress jdbiTempAddr = handle.attach(JdbiTempMailAddress.class);
            String instanceGuid = createTestActivityInstance(handle);
            MailAddress blank = new MailAddress();

            Long id = jdbiTempAddr.saveTempAddress(blank, userGuid, userGuid, instanceGuid);
            assertNotNull("blank address should be saved", id);

            Optional<MailAddress> res = jdbiTempAddr.findTempAddressByActvityInstanceGuid(instanceGuid);
            assertTrue(res.isPresent());
            checkFieldsEqual(blank, res.get());

            MailAddress partial = new MailAddress();
            partial.setCountry("FR");
            saveRetrieveAndCheckTempAddress(partial, userGuid, userGuid, instanceGuid, jdbiTempAddr);

            partial.setZip("14390");
            saveRetrieveAndCheckTempAddress(partial, userGuid, userGuid, instanceGuid, jdbiTempAddr);

            partial.setState("Normany");
            saveRetrieveAndCheckTempAddress(partial, userGuid, userGuid, instanceGuid, jdbiTempAddr);

            partial.setCity("Cabourg");
            saveRetrieveAndCheckTempAddress(partial, userGuid, userGuid, instanceGuid, jdbiTempAddr);

            partial.setStreet2("2nd Floor");
            saveRetrieveAndCheckTempAddress(partial, userGuid, userGuid, instanceGuid, jdbiTempAddr);

            partial.setStreet1("27 Avenue Pasteur");
            saveRetrieveAndCheckTempAddress(partial, userGuid, userGuid, instanceGuid, jdbiTempAddr);

            partial.setDefault(true);
            saveRetrieveAndCheckTempAddress(partial, userGuid, userGuid, instanceGuid, jdbiTempAddr);

            partial.setDefault(false);
            saveRetrieveAndCheckTempAddress(partial, userGuid, userGuid, instanceGuid, jdbiTempAddr);

            handle.rollback();
        });
    }

    private void saveRetrieveAndCheckTempAddress(MailAddress emptyAddress, String participantGuid,
                                                 String operatorGuid, String activityInstanceGuid,
                                                 JdbiTempMailAddress tempAddressDao) {
        tempAddressDao.saveTempAddress(emptyAddress, participantGuid, operatorGuid, activityInstanceGuid);
        Optional<MailAddress> tempAddressFromDb = tempAddressDao.findTempAddressByActvityInstanceGuid(
                activityInstanceGuid);
        assertTrue(tempAddressFromDb.isPresent());
        checkFieldsEqual(emptyAddress, tempAddressFromDb.get());

    }

    private void checkFieldsEqual(MailAddress original, MailAddress retrievedAddress) {
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
        assertEquals(original.isDefault(), retrievedAddress.isDefault());
    }

    private String createTestActivityInstance(Handle handle) {
        String code = "TEMP_ADDR_ACT_" + Instant.now().toEpochMilli();
        FormActivityDef activity = FormActivityDef.generalFormBuilder(code, "v1", data.getStudyGuid())
                .addName(new Translation("en", "activity " + code))
                .build();
        handle.attach(ActivityDao.class).insertActivity(activity, RevisionMetadata.now(data.getTestingUser().getUserId(), "add " + code));
        assertNotNull(activity.getActivityId());

        String instanceGuid = handle.attach(ActivityInstanceDao.class)
                .insertInstance(activity.getActivityId(), userGuid, userGuid, InstanceStatusType.CREATED, false)
                .getGuid();
        assertNotNull(instanceGuid);
        return instanceGuid;

    }
}
