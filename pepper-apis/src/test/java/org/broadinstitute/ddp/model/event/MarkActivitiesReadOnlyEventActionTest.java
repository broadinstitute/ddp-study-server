package org.broadinstitute.ddp.model.event;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Set;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class MarkActivitiesReadOnlyEventActionTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void test_noTargetActivityInstances() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef activity = newTestActivity(handle);

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), testData.getStudyId(), EventTriggerType.GOVERNED_USER_REGISTERED);
            var action = new MarkActivitiesReadOnlyEventAction(null, Set.of(activity.getActivityId()));
            action.doAction(null, handle, signal);
            // all good!

            handle.rollback();
        });
    }

    @Test
    public void test_targetActivityInstancesTurnsReadOnly() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act1 = newTestActivity(handle);
            FormActivityDef act2 = newTestActivity(handle);

            var instanceDao = handle.attach(ActivityInstanceDao.class);
            ActivityInstanceDto instance1 = instanceDao.insertInstance(act1.getActivityId(), testData.getUserGuid());
            ActivityInstanceDto instance2 = instanceDao.insertInstance(act2.getActivityId(), testData.getUserGuid());

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), testData.getStudyId(), EventTriggerType.GOVERNED_USER_REGISTERED);
            var action = new MarkActivitiesReadOnlyEventAction(null, Set.of(act1.getActivityId(), act2.getActivityId()));
            action.doAction(null, handle, signal);

            var jdbiInstance = handle.attach(JdbiActivityInstance.class);
            assertTrue(jdbiInstance.getByActivityInstanceId(instance1.getId()).get().getReadonly());
            assertTrue(jdbiInstance.getByActivityInstanceId(instance2.getId()).get().getReadonly());

            handle.rollback();
        });
    }

    @Test
    public void test_multipleInstancesForTargetActivity() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act1 = newTestActivity(handle);

            var instanceDao = handle.attach(ActivityInstanceDao.class);
            ActivityInstanceDto instance1 = instanceDao.insertInstance(act1.getActivityId(), testData.getUserGuid());
            ActivityInstanceDto instance2 = instanceDao.insertInstance(act1.getActivityId(), testData.getUserGuid());

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), testData.getStudyId(), EventTriggerType.GOVERNED_USER_REGISTERED);
            var action = new MarkActivitiesReadOnlyEventAction(null, Set.of(act1.getActivityId()));
            action.doAction(null, handle, signal);

            var jdbiInstance = handle.attach(JdbiActivityInstance.class);
            assertTrue(jdbiInstance.getByActivityInstanceId(instance1.getId()).get().getReadonly());
            assertTrue(jdbiInstance.getByActivityInstanceId(instance2.getId()).get().getReadonly());

            handle.rollback();
        });
    }

    @Test
    public void test_nonTargetedActivityInstancesNotAffected() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act1 = newTestActivity(handle);
            FormActivityDef act2 = newTestActivity(handle);

            var instanceDao = handle.attach(ActivityInstanceDao.class);
            ActivityInstanceDto instance1 = instanceDao.insertInstance(act1.getActivityId(), testData.getUserGuid());
            ActivityInstanceDto instance2 = instanceDao.insertInstance(act2.getActivityId(), testData.getUserGuid());

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), testData.getStudyId(), EventTriggerType.GOVERNED_USER_REGISTERED);
            var action = new MarkActivitiesReadOnlyEventAction(null, Set.of(act1.getActivityId()));
            action.doAction(null, handle, signal);

            var jdbiInstance = handle.attach(JdbiActivityInstance.class);
            assertTrue(jdbiInstance.getByActivityInstanceId(instance1.getId()).get().getReadonly());
            assertFalse(jdbiInstance.getByActivityInstanceId(instance2.getId()).get().getReadonly());

            handle.rollback();
        });
    }

    private FormActivityDef newTestActivity(Handle handle) {
        FormActivityDef form = FormActivityDef
                .generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                .addName(new Translation("en", "test activity"))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }
}
