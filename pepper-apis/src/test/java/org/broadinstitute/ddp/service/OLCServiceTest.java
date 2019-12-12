package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.auth0.exception.Auth0Exception;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.study.ParticipantInfo;
import org.broadinstitute.ddp.model.study.StudyParticipantsInfo;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class OLCServiceTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData data;

    private static AddressService addressService;

    private Set<String> mailAddressesToDelete = new HashSet<>();

    @BeforeClass
    public static void setupTestData() {
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
        });
        addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY), cfg.getString(ConfigFile.GEOCODING_API_KEY));
    }

    @After
    public void breakdown() {
        TransactionWrapper.useTxn(handle -> mailAddressesToDelete.forEach(guid -> assertTrue(addressService.deleteAddress(handle, guid))));
    }

    @Test
    public void testHappyPath() throws Auth0Exception {
        MailAddress address;

        OLCPrecision studyPrecision = data.getTestingStudy().getOlcPrecision();
        TransactionWrapper.useTxn(handle -> mailAddressesToDelete.add(TestDataSetupUtil.createTestingMailAddress(handle, data).getGuid()));
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

        address = addressService.addAddress(secondTestAddress, secondUserGuid, secondUserGuid);
        mailAddressesToDelete.add(address.getGuid());
        String firstOLC = address.getPlusCode();
        firstOLC = OLCService.convertPlusCodeToPrecision(firstOLC, studyPrecision);

        String thirdUserGuid = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle ->
                TestDataSetupUtil.generateTestUser(handle, data.getStudyGuid()).getUserGuid());
        MailAddress thirdTestAddress = new MailAddress();
        thirdTestAddress.setName("Bob Lincoln");
        thirdTestAddress.setStreet1("1600 Amphitheatre Parkway");
        thirdTestAddress.setCity("Mountain View");
        thirdTestAddress.setState("CA");
        thirdTestAddress.setZip("94043");
        thirdTestAddress.setCountry("US");
        thirdTestAddress.setDefault(true);

        address = addressService.addAddress(thirdTestAddress, thirdUserGuid, thirdUserGuid);
        mailAddressesToDelete.add(address.getGuid());
        String secondOLC = address.getPlusCode();
        secondOLC = OLCService.convertPlusCodeToPrecision(secondOLC, studyPrecision);


        StudyParticipantsInfo participants = TransactionWrapper.withTxn(handle -> {
            JdbiUserStudyEnrollment jdbiUserStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            jdbiUserStudyEnrollment
                    .changeUserStudyEnrollmentStatus(data.getUserGuid(), data.getStudyGuid(), EnrollmentStatusType.ENROLLED);
            jdbiUserStudyEnrollment
                    .changeUserStudyEnrollmentStatus(secondUserGuid, data.getStudyGuid(), EnrollmentStatusType.ENROLLED);
            jdbiUserStudyEnrollment
                    .changeUserStudyEnrollmentStatus(thirdUserGuid, data.getStudyGuid(), EnrollmentStatusType.ENROLLED);

            return OLCService.getAllOLCsForEnrolledParticipantsInStudy(handle, data.getStudyGuid());
        });

        assertEquals(3, participants.getParticipantInfoList().size());

        List<ParticipantInfo> enrolledParticipantInfoList = participants.getParticipantInfoList();
        assertEquals(firstOLC, enrolledParticipantInfoList.get(0).getLocation());
        assertEquals(firstOLC, enrolledParticipantInfoList.get(1).getLocation());
        assertEquals(secondOLC, enrolledParticipantInfoList.get(2).getLocation());
    }

    @Test
    public void testReturnsNullWhenNoLocationSharingAllowed() {
        try {
            TransactionWrapper.useTxn(handle -> {
                int rowsUpdated = handle.attach(JdbiUmbrellaStudy.class).updateShareLocationForStudy(false, data.getStudyGuid());
                assertEquals(1, rowsUpdated);

                StudyParticipantsInfo participants =
                        OLCService.getAllOLCsForEnrolledParticipantsInStudy(handle, data.getStudyGuid());
                assertNull(participants);
            });
        } finally {
            int rowsUpdated = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUmbrellaStudy.class)
                    .updateShareLocationForStudy(true, data.getStudyGuid()));
            assertEquals(1, rowsUpdated);
        }
    }

    @Test
    public void testReturnsEmptyListWhenNoParticipantsHaveDefaultAddresses() {
        TransactionWrapper.useTxn(handle -> {
            mailAddressesToDelete.add(TestDataSetupUtil.createTestingMailAddress(handle, data).getGuid());
            data.getMailAddress().setDefault(false);
        });
        addressService.updateAddress(data.getMailAddress().getGuid(), data.getMailAddress(), data.getUserGuid(), data.getUserGuid());

        StudyParticipantsInfo participants =
                TransactionWrapper.withTxn(handle -> OLCService.getAllOLCsForEnrolledParticipantsInStudy(handle, data.getStudyGuid()));

        assertTrue(participants.getParticipantInfoList().isEmpty());
    }

    @Test
    public void testReturnsNullWhenNotOLCPrecisionSetForStudy() {
        try {
            TransactionWrapper.useTxn(handle -> {
                int rowsUpdated = handle.attach(JdbiUmbrellaStudy.class).updateOlcPrecisionForStudy(null, data.getStudyGuid());
                assertEquals(1, rowsUpdated);

                StudyParticipantsInfo participants =
                        OLCService.getAllOLCsForEnrolledParticipantsInStudy(handle, data.getStudyGuid());
                assertNull(participants);
            });
        } finally {
            int rowsUpdated = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUmbrellaStudy.class)
                    .updateOlcPrecisionForStudy(OLCPrecision.MEDIUM, data.getStudyGuid()));
            assertEquals(1, rowsUpdated);
        }

    }
}
