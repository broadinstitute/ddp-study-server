package org.broadinstitute.ddp.db.dao;

import java.util.concurrent.TimeUnit;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

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
                    Assert.assertFalse(ActivityInstanceUtil.isReadonly(handle, instanceDto.getGuid()));
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
}
