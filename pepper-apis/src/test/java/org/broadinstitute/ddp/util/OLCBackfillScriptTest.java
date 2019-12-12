package org.broadinstitute.ddp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.auth0.exception.Auth0Exception;
import com.typesafe.config.Config;

import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.AddressService;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class OLCBackfillScriptTest {
    private static String geocodingKey;
    private static TestDataSetupUtil.GeneratedTestData data;
    private Set<String> mailAddressesToDelete = new HashSet<>();

    @BeforeClass
    public static void beforeClass() {
        Config cfg = ConfigManager.getInstance().getConfig();
        geocodingKey = cfg.getString(ConfigFile.GEOCODING_API_KEY);

        OLCBackfillScript.initDbConnection();

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> data = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @After
    public void breakdown() {
        TransactionWrapper.useTxn(handle -> mailAddressesToDelete.forEach(guid -> assertTrue(AddressService.deleteAddress(handle, guid))));
    }

    @Test
    public void testHappyPath() throws Auth0Exception {
        Writer writer = new StringWriter();

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            mailAddressesToDelete.add(TestDataSetupUtil.createTestingMailAddress(handle, data).getGuid());
            data.getMailAddress().setPlusCode("aSuperGreatPlusCodeWow");
            handle.attach(JdbiMailAddress.class).updateAddress(data.getMailAddress().getGuid(),
                    data.getMailAddress(),
                    data.getUserGuid(),
                    data.getUserGuid());
        });

        String secondUserGuid = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle ->
                TestDataSetupUtil.generateTestUser(handle, data.getStudyGuid()).getUserGuid());
        MailAddress secondTestAddress = new MailAddress();
        secondTestAddress.setName("Abraham Lincoln");
        secondTestAddress.setStreet1("86 Brattle Street");
        secondTestAddress.setCity("Cambridge");
        secondTestAddress.setState("MA");
        secondTestAddress.setZip("02138");
        secondTestAddress.setCountry("US");
        secondTestAddress.setDefault(true);
        String secondAddressGuid = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> {
            JdbiMailAddress jdbiMailAddress = handle.attach(JdbiMailAddress.class);
            String guid = jdbiMailAddress.insertAddress(secondTestAddress,
                    secondUserGuid,
                    secondUserGuid).getGuid();
            Optional<MailAddress> optionalSavedAddress = jdbiMailAddress.findAddressByGuid(guid);
            assertTrue(optionalSavedAddress.isPresent());
            assertNull(optionalSavedAddress.get().getPlusCode());

            mailAddressesToDelete.add(optionalSavedAddress.get().getGuid());

            return guid;
        });


        OLCBackfillScript.processRecords(writer, geocodingKey);

        String idsOfAlteredAddressesString = writer.toString();
        String[] idStrings = idsOfAlteredAddressesString.split(",");

        assertEquals(1, idStrings.length);

        assertEquals(secondAddressGuid, idStrings[0]);

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            assertNotNull(handle.attach(JdbiMailAddress.class).findAddressByGuid(idStrings[0]).get().getPlusCode());
        });
    }
}
