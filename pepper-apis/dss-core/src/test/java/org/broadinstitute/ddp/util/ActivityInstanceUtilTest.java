package org.broadinstitute.ddp.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityInstanceUtilTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static ActivityInstanceDto instanceDto;
    private static FormActivityDef activityDef;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(
                handle -> {
                    testData = TestDataSetupUtil.generateBasicUserTestData(handle);
                    activityDef = TestDataSetupUtil.generateTestFormActivityForUser(
                            handle, testData.getUserGuid(), testData.getStudyGuid()
                    );
                    instanceDto = TestDataSetupUtil.generateTestFormActivityInstanceForUser(
                            handle, activityDef.getActivityId(), testData.getUserGuid()
                    );
                }
        );
    }

    @Test
    public void test_whenIsReadonlyIsNotNull_thenItTakesPrecedenceOverCalculatedValue() throws InterruptedException {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiActivity.class).updateEditTimeoutSecByCode(
                            1L, activityDef.getActivityCode(), testData.getStudyId()
                    );
                    handle.attach(JdbiActivityInstance.class).updateIsReadonlyByGuid(
                            false, instanceDto.getGuid()
                    );
                    TimeUnit.SECONDS.sleep(1L);

                    // ActivityInstanceUtil.isReadonly() evaluated to true but "isReadonly" flag overrides it
                    assertFalse(ActivityInstanceUtil.isReadonly(handle, instanceDto.getGuid()));
                    handle.rollback();
                }
        );
    }

    @Test
    public void test_whenIsReadonlyIsNull_thenItCalculatedValueTakesPrecedenceOverIt() throws InterruptedException {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiActivity.class).updateEditTimeoutSecByCode(
                            1L, activityDef.getActivityCode(), testData.getStudyId()
                    );
                    Boolean isReadonly = null;
                    handle.attach(JdbiActivityInstance.class).updateIsReadonlyByGuid(
                            isReadonly, instanceDto.getGuid()
                    );
                    TimeUnit.SECONDS.sleep(1L);

                    // ActivityInstanceUtil.isReadonly() evaluates to true because "isReadonly" flag is not taken into account
                    Assert.assertTrue(ActivityInstanceUtil.isReadonly(handle, instanceDto.getGuid()));
                    handle.rollback();
                }
        );
    }

    @Test
    public void testComputeCanDelete() {
        boolean actual = ActivityInstanceUtil.computeCanDelete(false, true, true);
        assertFalse("should not allow delete since canDeleteInstances is false", actual);

        actual = ActivityInstanceUtil.computeCanDelete(true, null, false);
        assertTrue("not first instance so can delete", actual);

        actual = ActivityInstanceUtil.computeCanDelete(true, null, true);
        assertTrue("is first instance but since canDeleteFirstInstance is not set it should default to allow", actual);

        actual = ActivityInstanceUtil.computeCanDelete(true, false, true);
        assertFalse("is first instance and canDeleteFirstInstance is false", actual);
    }
}
