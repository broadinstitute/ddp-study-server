package org.broadinstitute.ddp.customexport.db.dao;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CustomExportDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static FormActivityDef activityDef;
    private static ActivityInstanceDto instanceDto;

    @Before
    public void setup() {
        TransactionWrapper.useTxn(
                handle -> {
                    testData = TestDataSetupUtil.generateBasicUserTestData(handle);
                    activityDef = TestDataSetupUtil.generateTestFormActivityForUser(
                            handle, testData.getUserGuid(), testData.getStudyGuid()
                    );
                    instanceDto = TestDataSetupUtil.generateTestFormActivityInstanceForUser(
                            handle, activityDef.getActivityId(), testData.getUserGuid()
                    );
                    TestDataSetupUtil.setUserEnrollmentStatus(handle, testData, EnrollmentStatusType.REGISTERED);
                }
        );
    }

    @Test
    public void testFindCustomUserIdsToExport() {
        TransactionWrapper.useTxn(
                handle -> {
                    CustomExportDao dao = handle.attach(CustomExportDao.class);
                    Set<Long> ids = dao.findCustomUserIdsToExport(
                            testData.getStudyId(), "COMPLETE",
                            Instant.now().toEpochMilli(), instanceDto.getActivityCode(), 50, 0);

                    // Make sure we don't export incomplete surveys
                    Assert.assertNotNull(ids);
                    Assert.assertEquals(0, ids.size());

                    // Make sure we don't export already exported surveys
                    long time = Instant.now().toEpochMilli();
                    handle.attach(ActivityInstanceStatusDao.class).insertStatus(instanceDto.getId(), InstanceStatusType.COMPLETE, time,
                            testData.getTestingUser().getUserGuid());
                    ids = dao.findCustomUserIdsToExport(testData.getStudyId(), "COMPLETE", time,
                            instanceDto.getActivityCode(), 50, 0);

                    Assert.assertNotNull(ids);
                    Assert.assertEquals(0, ids.size());

                    //Make sure we export recently completed surveys
                    ids = dao.findCustomUserIdsToExport(testData.getStudyId(), "COMPLETE", 0,
                            instanceDto.getActivityCode(), 50, 0);

                    Assert.assertNotNull(ids);
                    Assert.assertEquals(1, ids.size());
                    handle.rollback();
                }
        );
    }


    @Test
    public void testNeedCustomExport() {
        TransactionWrapper.useTxn(
                handle -> {
                    CustomExportDao dao = handle.attach(CustomExportDao.class);
                    boolean needExport = dao.needCustomExport(testData.getStudyId(), "COMPLETE",
                            Instant.now().toEpochMilli(), instanceDto.getActivityCode());
                    Assert.assertFalse(needExport);
                    handle.rollback();
                }
        );
    }

    @Test
    public void testGetCustomLastCompletionDate() {
        TransactionWrapper.useTxn(
                handle -> {
                    CustomExportDao dao = handle.attach(CustomExportDao.class);
                    Optional<Long> date = dao.getLastCustomCompletionDate(
                            testData.getStudyId(), "COMPLETE", instanceDto.getActivityCode());
                    Assert.assertTrue(date.isEmpty());

                    long time = Instant.now().toEpochMilli();
                    handle.attach(ActivityInstanceStatusDao.class).insertStatus(instanceDto.getId(), InstanceStatusType.COMPLETE, time,
                            testData.getTestingUser().getUserGuid());
                    date = dao.getLastCustomCompletionDate(testData.getStudyId(), "COMPLETE", instanceDto.getActivityCode());
                    Assert.assertTrue(date.isPresent());
                    long realDate = date.get();
                    Assert.assertEquals(time, realDate);

                    handle.rollback();
                }
        );
    }
}
