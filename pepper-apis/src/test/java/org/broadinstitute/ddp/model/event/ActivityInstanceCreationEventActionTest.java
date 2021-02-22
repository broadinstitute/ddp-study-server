package org.broadinstitute.ddp.model.event;

import static org.junit.Assert.assertEquals;
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
                    testData.getStudyId(), EventTriggerType.DSM_NOTIFICATION);
            var action = new ActivityInstanceCreationEventAction(null, nestedAct.getActivityId());

            try {
                action.doAction(handle, signal);
                fail("Expected exception not thrown");
            } catch (Exception e) {
                assertTrue(e instanceof DDPException);
                assertTrue(e.getMessage().contains("ACTIVITY_STATUS"));
            }

            handle.rollback();
        });
    }

    @Test
    public void testCreatedNestActivityInstance() {
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
                    parentInstanceId, parentAct.getActivityId(), testData.getStudyId(), InstanceStatusType.CREATED);
            var action = new ActivityInstanceCreationEventAction(null, nestedAct.getActivityId());
            action.doAction(handle, signal);

            List<ActivityInstanceDto> actualInstances = handle.attach(JdbiActivityInstance.class)
                    .findAllByUserGuidAndActivityCode(testData.getUserGuid(), nestedAct.getActivityCode(), testData.getStudyId());
            assertEquals(1, actualInstances.size());
            assertEquals(nestedAct.getActivityId(), (Long) actualInstances.get(0).getActivityId());
            assertEquals((Long) parentInstanceId, actualInstances.get(0).getParentInstanceId());

            handle.rollback();
        });
    }
}
