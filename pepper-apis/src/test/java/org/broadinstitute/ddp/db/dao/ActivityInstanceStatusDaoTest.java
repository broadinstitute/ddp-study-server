package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityInstanceStatusDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testInsertStatus_createsNewStatusWhenNoneExist() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceStatusDao dao = handle.attach(ActivityInstanceStatusDao.class);

            FormActivityDef form = setupDummyActivity(handle, testData.getStudyGuid(), testData.getUserId());

            ActivityInstanceDto instanceDto = manuallyInsertInstance(handle, form.getActivityId(), testData.getUserId());
            assertFalse(dao.getCurrentStatus(instanceDto.getId()).isPresent());
            assertEquals(0, dao.getAllStatuses(instanceDto.getId()).size());

            long statusId = dao.insertStatus(instanceDto.getId(), InstanceStatusType.CREATED,
                    Instant.now().toEpochMilli(), testData.getUserGuid()).getId();

            Optional<ActivityInstanceStatusDto> current = dao.getCurrentStatus(instanceDto.getId());
            assertTrue(current.isPresent());
            assertEquals(statusId, current.get().getId());
            assertEquals(1, dao.getAllStatuses(instanceDto.getId()).size());
            assertEquals(InstanceStatusType.CREATED, current.get().getType());

            handle.rollback();
        });
    }

    @Test
    public void testInsertStatus_reuseExistingStatusWhenRedundant() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceStatusDao dao = handle.attach(ActivityInstanceStatusDao.class);

            FormActivityDef form = setupDummyActivity(handle, testData.getStudyGuid(), testData.getUserId());

            ActivityInstanceDto instanceDto = manuallyInsertInstance(handle, form.getActivityId(), testData.getUserId());
            long nowMillis = Instant.now().toEpochMilli();
            long statusId = dao.insertStatus(instanceDto.getId(), InstanceStatusType.CREATED,
                    nowMillis, testData.getUserGuid()).getId();

            long newMillis = nowMillis + 2000L;
            long newStatusId = dao.insertStatus(instanceDto.getId(), InstanceStatusType.CREATED,
                    newMillis, testData.getUserGuid()).getId();
            assertEquals(statusId, newStatusId);
            assertEquals(1, dao.getAllStatuses(instanceDto.getId()).size());

            testGetLatestStatus(dao, instanceDto.getGuid(), newStatusId, InstanceStatusType.CREATED);

            Optional<ActivityInstanceStatusDto> current = dao.getCurrentStatus(instanceDto.getId());

            assertTrue(current.isPresent());
            assertEquals(statusId, current.get().getId());

            handle.rollback();
        });
    }

    @Test
    public void testInsertStatus_createsNewWhenStatusesDiffer() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceStatusDao dao = handle.attach(ActivityInstanceStatusDao.class);

            FormActivityDef form = setupDummyActivity(handle, testData.getStudyGuid(), testData.getUserId());

            ActivityInstanceDto instanceDto = manuallyInsertInstance(handle, form.getActivityId(), testData.getUserId());
            long nowMillis = Instant.now().toEpochMilli();
            long statusId = dao.insertStatus(instanceDto.getId(), InstanceStatusType.CREATED,
                    nowMillis, testData.getUserGuid()).getId();

            testGetLatestStatus(dao, instanceDto.getGuid(), statusId, InstanceStatusType.CREATED);

            long newMillis = nowMillis + 2000L;
            long newStatusId = dao.insertStatus(instanceDto.getId(), InstanceStatusType.IN_PROGRESS,
                    newMillis, testData.getUserGuid()).getId();
            assertNotEquals(statusId, newStatusId);
            assertEquals(2, dao.getAllStatuses(instanceDto.getId()).size());

            testGetLatestStatus(dao, instanceDto.getGuid(), newStatusId, InstanceStatusType.IN_PROGRESS);

            Optional<ActivityInstanceStatusDto> current = dao.getCurrentStatus(instanceDto.getId());
            assertTrue(current.isPresent());
            assertEquals(newStatusId, current.get().getId());
            assertEquals(newMillis, current.get().getUpdatedAt());
            assertEquals(getStatusTypeId(handle, InstanceStatusType.IN_PROGRESS), current.get().getTypeId());

            handle.rollback();
        });
    }

    @Test
    public void testInsertStatus_transitioningFromCompleteNotAllowed() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceStatusDao dao = handle.attach(ActivityInstanceStatusDao.class);
            FormActivityDef form = setupDummyActivity(handle, testData.getStudyGuid(), testData.getUserId());
            ActivityInstanceDto instanceDto = manuallyInsertInstance(handle, form.getActivityId(), testData.getUserId());
            long oldStatusTypeId = dao.insertStatus(instanceDto.getId(), InstanceStatusType.COMPLETE,
                    Instant.now().toEpochMilli(), testData.getUserGuid()).getTypeId();
            Arrays.asList(InstanceStatusType.CREATED, InstanceStatusType.IN_PROGRESS).forEach(newStatus -> {
                long newStatusTypeId = dao.insertStatus(
                        instanceDto.getId(),
                        newStatus,
                        Instant.now().toEpochMilli() + 2000L,
                        testData.getUserGuid()
                ).getTypeId();
                Optional<ActivityInstanceStatusDto> current = dao.getCurrentStatus(instanceDto.getId());
                assertEquals("Transitioning from COMPLETE to " + newStatus + " not allowed", oldStatusTypeId, current.get().getTypeId());
            });
            handle.rollback();
        });
    }

    @Test
    public void testInsertStatus_fillsInFirstCompletedAt_onlyOnFirstTransitionToComplete() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceStatusDao dao = handle.attach(ActivityInstanceStatusDao.class);

            FormActivityDef form = setupDummyActivity(handle, testData.getStudyGuid(), testData.getUserId());

            ActivityInstanceDto instanceDto = manuallyInsertInstance(handle, form.getActivityId(), testData.getUserId());
            dao.insertStatus(instanceDto.getId(), InstanceStatusType.CREATED, Instant.now().toEpochMilli(), testData.getUserGuid());

            instanceDto = handle.attach(JdbiActivityInstance.class).getByActivityInstanceId(instanceDto.getId()).get();
            assertNull(instanceDto.getFirstCompletedAt());

            // First time around firstCompletedAt should be set
            long timestamp = Instant.now().toEpochMilli();
            dao.insertStatus(instanceDto.getId(), InstanceStatusType.COMPLETE, timestamp, testData.getUserGuid());
            instanceDto = handle.attach(JdbiActivityInstance.class).getByActivityInstanceId(instanceDto.getId()).get();
            assertEquals((Long) timestamp, instanceDto.getFirstCompletedAt());

            // Second time around firstCompletedAt should not change
            dao.insertStatus(instanceDto.getId(), InstanceStatusType.COMPLETE, timestamp + 1000, testData.getUserGuid());
            instanceDto = handle.attach(JdbiActivityInstance.class).getByActivityInstanceId(instanceDto.getId()).get();
            assertEquals((Long) timestamp, instanceDto.getFirstCompletedAt());

            handle.rollback();
        });
    }

    @Test
    public void testInsertStatus_updatesTimestampWhenSameStatus() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceStatusDao dao = handle.attach(ActivityInstanceStatusDao.class);
            JdbiActivityInstanceStatus jdbiStatus = handle.attach(JdbiActivityInstanceStatus.class);

            FormActivityDef form = setupDummyActivity(handle, testData.getStudyGuid(), testData.getUserId());

            ActivityInstanceDto instanceDto = manuallyInsertInstance(handle, form.getActivityId(), testData.getUserId());
            long timestamp = Instant.now().toEpochMilli();

            long id1 = dao.insertStatus(instanceDto.getId(), InstanceStatusType.CREATED, timestamp, testData.getUserGuid()).getId();
            long id2 = dao.insertStatus(instanceDto.getId(), InstanceStatusType.CREATED, timestamp + 10, testData.getUserGuid()).getId();
            assertEquals(id1, id2);
            assertEquals(timestamp + 10, jdbiStatus.findByStatusId(id1).get().getUpdatedAt());

            id1 = dao.insertStatus(instanceDto.getId(), InstanceStatusType.IN_PROGRESS, timestamp + 20, testData.getUserGuid()).getId();
            id2 = dao.insertStatus(instanceDto.getId(), InstanceStatusType.IN_PROGRESS, timestamp + 30, testData.getUserGuid()).getId();
            assertEquals(id1, id2);
            assertEquals(timestamp + 30, jdbiStatus.findByStatusId(id1).get().getUpdatedAt());

            id1 = dao.insertStatus(instanceDto.getId(), InstanceStatusType.COMPLETE, timestamp + 40, testData.getUserGuid()).getId();
            id2 = dao.insertStatus(instanceDto.getId(), InstanceStatusType.COMPLETE, timestamp + 50, testData.getUserGuid()).getId();
            assertEquals(id1, id2);
            assertEquals(timestamp + 50, jdbiStatus.findByStatusId(id1).get().getUpdatedAt());

            handle.rollback();
        });
    }

    private long getStatusTypeId(Handle handle, InstanceStatusType statusType) {
        return handle.attach(JdbiActivityInstanceStatusType.class).getStatusTypeId(statusType);
    }

    private FormActivityDef setupDummyActivity(Handle handle, String studyGuid, long userId) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT", "v1", studyGuid)
                .addName(new Translation("en", "dummy activity"))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "test"));
        return form;
    }

    private ActivityInstanceDto manuallyInsertInstance(Handle handle, long activityId, long participantId) {
        JdbiActivityInstance jdbiInstance = handle.attach(JdbiActivityInstance.class);
        String instanceGuid = jdbiInstance.generateUniqueGuid();
        long millis = Instant.now().toEpochMilli();
        long instanceId = jdbiInstance.insert(activityId, participantId, instanceGuid, false, millis, null);
        Optional<ActivityInstanceDto> dto = jdbiInstance.getByActivityInstanceId(instanceId);

        assertTrue(dto.isPresent());
        assertNull("manually created instance should not have status", dto.get().getStatusType());

        return dto.get();
    }

    private void testGetLatestStatus(ActivityInstanceStatusDao dao, String instanceGuid, long newStatusId,
                                     InstanceStatusType statusType) {
        Optional<ActivityInstanceStatusDto> optionalCurrentStatus = dao.getLatestStatus(instanceGuid, statusType);
        if (optionalCurrentStatus.isPresent()) {
            assertEquals(newStatusId, optionalCurrentStatus.get().getId());
        } else {
            fail("Could not retrieve latest status");
        }
    }


}
