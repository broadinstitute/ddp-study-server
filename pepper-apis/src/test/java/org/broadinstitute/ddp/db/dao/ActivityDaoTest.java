package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.NoSuchElementException;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ActivityDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testInsertActivity() {
        TransactionWrapper.useTxn(handle -> {
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiActivityVersion jdbiVersion = handle.attach(JdbiActivityVersion.class);

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity"))
                    .build();
            ActivityVersionDto versionDto = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            assertNotNull(form.getActivityId());
            assertEquals(form.getVersionId(), (Long) versionDto.getId());
            assertEquals(form.getVersionTag(), versionDto.getVersionTag());

            ActivityVersionDto actual = jdbiVersion.getActiveVersion(form.getActivityId()).get();
            assertEquals(versionDto.getId(), actual.getId());
            assertEquals(form.getVersionTag(), actual.getVersionTag());
            assertEquals(versionDto.getRevStart(), actual.getRevStart());

            handle.rollback();
        });
    }

    @Test
    public void testChangeVersion() {
        TransactionWrapper.useTxn(handle -> {
            ActivityDao actDao = handle.attach(ActivityDao.class);
            JdbiActivityVersion jdbiVersion = handle.attach(JdbiActivityVersion.class);

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity"))
                    .build();
            ActivityVersionDto versionDto = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            RevisionMetadata meta = new RevisionMetadata(versionDto.getRevStart() + 10, testData.getUserId(), "test");
            actDao.changeVersion(form.getActivityId(), "v2", meta);

            versionDto = jdbiVersion.getActiveVersion(form.getActivityId()).get();
            assertEquals("v2", versionDto.getVersionTag());
            assertEquals(2, jdbiVersion.findAllVersionsInAscendingOrder(form.getActivityId()).size());

            handle.rollback();
        });
    }

    @Test
    public void testChangeVersion_notFound() {
        thrown.expect(NoSuchElementException.class);
        TransactionWrapper.useTxn(handle -> {
            ActivityDao actDao = handle.attach(ActivityDao.class);
            actDao.changeVersion(12345L, "v2", RevisionMetadata.now(1, "test"));
        });
    }

    @Test
    public void testChangeVersion_invalidTimestamp() {
        thrown.expect(IllegalArgumentException.class);
        TransactionWrapper.useTxn(handle -> {
            ActivityDao actDao = handle.attach(ActivityDao.class);

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "activity"))
                    .build();
            ActivityVersionDto versionDto = actDao.insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            RevisionMetadata meta = new RevisionMetadata(versionDto.getRevStart() - 10, testData.getUserId(), "test");
            actDao.changeVersion(form.getActivityId(), "v2", meta);

            handle.rollback();
        });
    }
}
