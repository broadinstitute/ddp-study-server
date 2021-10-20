package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.elasticsearch.client.RestHighLevelClient;
import org.jdbi.v3.core.Handle;

public class DeleteUserRouteTestAbstract extends IntegrationTestSuite.TestCase {

    protected static TestDataSetupUtil.GeneratedTestData testData;

    protected static User userNonGoverned;
    protected static User userMultiGoverned;
    protected static User userWithAccount;
    protected static User userEnrolled;
    protected static User userWithKit;
    protected static User userNormal;

    protected static final List<Long> addressesToDelete = new ArrayList<>();
    protected static final List<Long> kitsToDelete = new ArrayList<>();
    protected static final List<User> usersToDelete = new ArrayList<>();
    protected static final Map<User, List<Long>> governancesToDelete = new HashMap<>();

    protected static RestHighLevelClient esClientMock = mock(RestHighLevelClient.class);

    protected static void setup() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);

            userNonGoverned = createUser(handle, testData.getTestingStudy(), null, false,
                    false);
            userMultiGoverned = createUser(handle, testData.getTestingStudy(), null, false,
                    false, testData.getUserId(), userNonGoverned.getId());
            userWithAccount = createUser(handle, testData.getTestingStudy(), "some_account", false,
                    false, testData.getUserId());
            userEnrolled = createUser(handle, testData.getTestingStudy(), null, true, false,
                    testData.getUserId());
            userWithKit = createUser(handle, testData.getTestingStudy(), null, false, true,
                    testData.getUserId());
            userNormal = createUser(handle, testData.getTestingStudy(), null, false,
                    false, testData.getUserId());
        });
    }

    protected static void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            var jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            var profileDao = handle.attach(UserProfileDao.class);
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            DsmKitRequestDao kitDao = handle.attach(DsmKitRequestDao.class);
            JdbiMailAddress addressDao = handle.attach(JdbiMailAddress.class);
            kitsToDelete.forEach(kitDao::deleteKitRequest);
            addressesToDelete.forEach(addressDao::deleteAddress);
            governancesToDelete.values().stream().flatMap(Collection::stream).forEach(userGovernanceDao::unassignProxy);
            userGovernanceDao.deleteAllGovernancesForProxy(testData.getUserId());
            for (var user : usersToDelete) {
                jdbiEnrollment.deleteByUserGuidStudyGuid(user.getGuid(), testData.getStudyGuid());
                profileDao.getUserProfileSql().deleteByUserGuid(user.getGuid());
                handle.attach(DataExportDao.class).deleteDataSyncRequestsForUser(user.getId());
            }
            handle.attach(JdbiUser.class).deleteAllByGuids(usersToDelete.stream().map(User::getGuid).collect(Collectors.toSet()));
            usersToDelete.clear();
        });
    }

    protected static User createUser(Handle handle, StudyDto study, String auth0Account, boolean enrolled,
                                   boolean kitRequested, Long... proxyUserIds) throws Exception {
        UserDao userDao = handle.attach(UserDao.class);
        JdbiUser jdbiUser = handle.attach(JdbiUser.class);
        String userGuid = DBUtils.uniqueUserGuid(handle);
        String userHruid = DBUtils.uniqueUserHruid(handle);
        long userId = jdbiUser.insert(auth0Account, userGuid, testData.getClientId(), userHruid);
        User user = userDao.findUserById(userId).orElseThrow(() -> new Exception("Could not find user: " + userId));
        Arrays.stream(proxyUserIds).forEach(proxyUserId -> {
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            long governanceId = userGovernanceDao.assignProxy(userGuid, proxyUserId, userId);
            governancesToDelete.computeIfAbsent(user, id -> new ArrayList<>()).add(governanceId);
            userGovernanceDao.grantGovernedStudy(governanceId, study.getId());
        });
        if (enrolled) {
            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(user.getGuid(), study.getGuid(), EnrollmentStatusType.ENROLLED);
        }
        if (kitRequested) {
            JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
            MailAddress address = dao.insertAddress(buildTestAddress(), userGuid, userGuid);
            JdbiMailAddress jdbiMailAddress = handle.attach(JdbiMailAddress.class);
            jdbiMailAddress.setDefaultAddressForParticipant(address.getGuid());
            addressesToDelete.add(address.getId());
            KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);
            KitType salivaKitType = kitTypeDao.getSalivaKitType();
            assertNotNull(salivaKitType);
            kitsToDelete.add(handle.attach(DsmKitRequestDao.class).createKitRequest(study.getGuid(), userGuid, salivaKitType));
        }
        usersToDelete.add(user);
        return user;
    }

    protected static MailAddress buildTestAddress() {
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
}
