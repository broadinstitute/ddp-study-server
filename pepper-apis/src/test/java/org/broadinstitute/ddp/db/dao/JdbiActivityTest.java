package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiActivityTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testGetIdByActivityCode_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<Long> id = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(1, "abc");
            assertNotNull(id);
            assertFalse(id.isPresent());
        });
    }

    @Test
    public void testGetIdByActivityCode_found() {
        TransactionWrapper.useTxn(handle -> {
            insertDummyActivity(handle, testData.getUserGuid(), testData.getStudyGuid(), "CODE1234");

            Optional<Long> id = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(
                    testData.getStudyId(), "CODE1234");
            assertNotNull(id);
            assertTrue(id.isPresent());
            assertTrue(id.get() >= 0);

            handle.rollback();
        });
    }

    private ActivityDef insertDummyActivity(Handle handle, String userGuid, String studyGuid, String activityCode) {
        long millis = Instant.now().toEpochMilli();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        long revId = handle.attach(JdbiRevision.class).insert(userId, millis, null, "test");

        List<Translation> names = Collections.singletonList(new Translation("en", "dummy activity"));
        List<Translation> titles = Collections.singletonList(new Translation("en", "dummy title"));
        List<Translation> subtitles = Collections.singletonList(new Translation("en", "dummy subtitle"));
        List<Translation> descriptions = Collections.singletonList(new Translation("en", "dummy description"));
        List<SummaryTranslation> summaries = Collections.singletonList(
                new SummaryTranslation("en", "dummy dashboard summary", InstanceStatusType.CREATED)
        );
        List<FormSectionDef> sections = Collections.emptyList();
        Template readonlyHint = Template.html("Please contact your organization");
        FormActivityDef form = new FormActivityDef(
                FormType.GENERAL, activityCode, "v1", studyGuid, 1, 1, false,
                names, titles, subtitles, descriptions, summaries,
                readonlyHint, null, sections, null, null, null, false, false
        );
        handle.attach(FormActivityDao.class).insertActivity(form, revId);

        assertNotNull(form.getActivityId());
        return form;
    }
}
