package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class UserProfileDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testGetUserProfileByUserGuid_nullableFieldsAreNull() {
        TransactionWrapper.useTxn(handle -> {
            User user = handle.attach(UserDao.class).createTempUser(testData.getClientId());

            UserProfile profile = new UserProfile.Builder(user.getId())
                    .setFirstName("first")
                    .setLastName("last")
                    .setBirthDate(LocalDate.of(1987, 3, 14))
                    .build();
            handle.attach(UserProfileDao.class).createProfile(profile);

            UserProfile actual = handle.attach(UserProfileDao.class).findProfileByUserGuid(user.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(user.getId(), actual.getUserId());
            assertEquals("first", actual.getFirstName());
            assertEquals("last", actual.getLastName());
            assertNull(actual.getSexType());
            assertEquals(LocalDate.of(1987, 3, 14), actual.getBirthDate());
            assertNull(actual.getPreferredLangId());
            assertNull(actual.getPreferredLangCode());
            assertNull(actual.getDoNotContact());
            assertNull(actual.getIsDeceased());

            handle.rollback();
        });
    }
}
