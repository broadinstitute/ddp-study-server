package org.broadinstitute.ddp.model.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityInstanceCreationEventActionTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testRequiresParentActivityTrigger() {
        TransactionWrapper.useTxn(handle -> {
            var parentAct = FormActivityDef
                    .generalFormBuilder("ACT_PARENT_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "parent"))
                    .build();
            var nestedAct = FormActivityDef
                    .generalFormBuilder("ACT_NESTED_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "child"))
                    .setParentActivityCode(parentAct.getActivityCode())
                    .build();
            handle.attach(ActivityDao.class).insertActivity(parentAct, List.of(nestedAct),
                    RevisionMetadata.now(testData.getUserId(), "test activity"));

            var signal = new EventSignal(
                    testData.getUserId(), testData.getUserId(), testData.getUserGuid(), testData.getUserGuid(),
                    testData.getStudyId(), testData.getStudyGuid(), EventTriggerType.DSM_NOTIFICATION);
            var action = new ActivityInstanceCreationEventAction(null, nestedAct.getActivityId(), false, null, null);

            try {
                action.doActionSynchronously(handle, signal);
                fail("Expected exception not thrown");
            } catch (Exception e) {
                assertTrue(e instanceof DDPException);
                assertTrue(e.getMessage().contains("ACTIVITY_STATUS"));
            }

            handle.rollback();
        });
    }

    @Test
    public void testCreateNestActivityInstance() {
        TransactionWrapper.useTxn(handle -> {
            var parentAct = FormActivityDef
                    .generalFormBuilder("ACT_PARENT_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "parent"))
                    .build();
            var nestedAct = FormActivityDef
                    .generalFormBuilder("ACT_NESTED_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "child"))
                    .setParentActivityCode(parentAct.getActivityCode())
                    .build();
            handle.attach(ActivityDao.class).insertActivity(parentAct, List.of(nestedAct),
                    RevisionMetadata.now(testData.getUserId(), "test activity"));
            long parentInstanceId = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(parentAct.getActivityId(), testData.getUserGuid()).getId();

            var signal = new ActivityInstanceStatusChangeSignal(
                    testData.getUserId(), testData.getUserId(), testData.getUserGuid(), testData.getUserGuid(),
                    parentInstanceId, parentAct.getActivityId(), testData.getStudyId(), testData.getStudyGuid(),
                    InstanceStatusType.CREATED);
            var action = new ActivityInstanceCreationEventAction(null, nestedAct.getActivityId(), false, null, null);
            action.doActionSynchronously(handle, signal);

            List<ActivityInstanceDto> actualInstances = handle.attach(JdbiActivityInstance.class)
                    .findAllByUserGuidAndActivityCode(testData.getUserGuid(), nestedAct.getActivityCode(), testData.getStudyId());
            assertEquals(1, actualInstances.size());
            assertEquals(nestedAct.getActivityId(), (Long) actualInstances.get(0).getActivityId());
            assertEquals((Long) parentInstanceId, actualInstances.get(0).getParentInstanceId());

            handle.rollback();
        });
    }

    @Test
    public void testCreateActivityInstanceWithNestedActivities() {
        TransactionWrapper.useTxn(handle -> {
            var act1 = FormActivityDef
                    .generalFormBuilder("ACT1_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "1"))
                    .build();
            var act2 = FormActivityDef
                    .generalFormBuilder("ACT2_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "2"))
                    .build();
            var act2Nested = FormActivityDef
                    .generalFormBuilder("ACT2_NESTED" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "nested"))
                    .setParentActivityCode(act2.getActivityCode())
                    .setCreateOnParentCreation(true)
                    .build();
            var activityDao = handle.attach(ActivityDao.class);
            activityDao.insertActivity(act1, RevisionMetadata.now(testData.getUserId(), "test"));
            activityDao.insertActivity(act2, List.of(act2Nested), RevisionMetadata.now(testData.getUserId(), "test"));
            long instanceId1 = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(act1.getActivityId(), testData.getUserGuid()).getId();

            var signal = new ActivityInstanceStatusChangeSignal(
                    testData.getUserId(), testData.getUserId(), testData.getUserGuid(), testData.getUserGuid(),
                    instanceId1, act1.getActivityId(), testData.getStudyId(), testData.getStudyGuid(), InstanceStatusType.CREATED);
            var action = new ActivityInstanceCreationEventAction(null, act2.getActivityId(), false, null, null);
            action.doActionSynchronously(handle, signal);

            List<ActivityInstanceDto> actualInstances = handle.attach(JdbiActivityInstance.class)
                    .findAllByUserGuidAndActivityCode(testData.getUserGuid(), act2.getActivityCode(), testData.getStudyId());
            assertEquals(1, actualInstances.size());
            ActivityInstanceDto instance2 = actualInstances.get(0);
            assertEquals(act2.getActivityId(), (Long) instance2.getActivityId());
            assertNull("top-level should not have a parent", instance2.getParentInstanceId());

            actualInstances = handle.attach(JdbiActivityInstance.class)
                    .findAllByUserGuidAndActivityCode(testData.getUserGuid(), act2Nested.getActivityCode(), testData.getStudyId());
            assertEquals(1, actualInstances.size());
            ActivityInstanceDto nestedInstance = actualInstances.get(0);
            assertEquals(act2Nested.getActivityId(), (Long) nestedInstance.getActivityId());
            assertEquals((Long) instance2.getId(), nestedInstance.getParentInstanceId());

            handle.rollback();
        });
    }
}
