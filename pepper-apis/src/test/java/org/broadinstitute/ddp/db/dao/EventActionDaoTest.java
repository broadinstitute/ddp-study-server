package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.Instant;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.CopyAnswerEventActionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.event.CopyLocationType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class EventActionDaoTest extends TxnAwareBaseTest {
    private static String textQuestionStableCode;
    private static TestDataSetupUtil.GeneratedTestData data;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            setupActivityAndInstance(handle);

        });
    }

    private static void setupActivityAndInstance(Handle handle) {
        long timestamp = Instant.now().toEpochMilli();

        textQuestionStableCode = "ANS_TEXT_" + timestamp;
        TextQuestionDef textDef = buildTextQuestionDef(textQuestionStableCode);

        String activityCode = "ANS_ACT_" + timestamp;
        FormActivityDef form = FormActivityDef.generalFormBuilder(activityCode, "v1", data.getStudyGuid())
                .addName(new Translation("en", "test activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(textDef)))
                .build();

        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(data.getTestingUser().getUserId(), "insert test "
                + "activity"));
        assertNotNull(form.getActivityId());
    }

    private static TextQuestionDef buildTextQuestionDef(String stableId) {
        return TextQuestionDef.builder().setStableId(stableId)
                .setInputType(TextInputType.TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "text prompt"))
                .build();
    }

    @Test
    public void testInsertCopyAnswerEventAction() {
        TransactionWrapper.useTxn(handle -> {
            EventActionDao eventActionDao = handle.attach(EventActionDao.class);
            CopyLocationType copyTarget = CopyLocationType.PARTICIPANT_PROFILE_LAST_NAME;
            long savedId = eventActionDao.insertCopyAnswerAction(data.getStudyId(), textQuestionStableCode,
                    CopyLocationType.PARTICIPANT_PROFILE_LAST_NAME);
            CopyAnswerEventActionDto savedAction = eventActionDao.findCopyAnswerAction(savedId);

            assertNotNull(savedAction);

            assertEquals(textQuestionStableCode, savedAction.getCopySourceStableCode());
            assertEquals(copyTarget, savedAction.getCopyAnswerTarget());
            assertEquals(savedId, savedAction.getId());
            assertNull(savedAction.getMessageDestinationId());
        });
    }


}
