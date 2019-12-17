package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDaoFactory;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiProfileTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testGetUserProfileByUserGuid_nullableFieldsAreNull() {
        TransactionWrapper.useTxn(handle -> {
            UserDto user = UserDaoFactory.createFromSqlConfig(sqlConfig).createTemporaryUser(handle, testData.getAuth0ClientId());

            UserProfileDto profile = new UserProfileDto(user.getUserId(), "first", "last", null,
                    LocalDate.of(1987, 3, 14), null, null, null);
            assertEquals(1, handle.attach(JdbiProfile.class).insert(profile));

            UserProfileDto actual = handle.attach(JdbiProfile.class).getUserProfileByUserGuid(user.getUserGuid());
            assertNotNull(actual);
            assertEquals(user.getUserId(), actual.getUserId());
            assertEquals("first", actual.getFirstName());
            assertEquals("last", actual.getLastName());
            assertNull(actual.getSex());
            assertEquals(LocalDate.of(1987, 3, 14), actual.getBirthDate());
            assertNull(actual.getPreferredLanguageId());
            assertNull(actual.getPreferredLanguageCode());
            assertNull(actual.getDoNotContact());

            handle.rollback();
        });
    }
}
