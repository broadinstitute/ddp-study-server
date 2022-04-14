package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.DateQuestionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiDateQuestionFieldOrderTest extends TxnAwareBaseTest {

    private static final String SID_DATE_TEXT = "DATE_F84700E19899";

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testGetOrderedFieldsByQuestionId() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act = insertDateActivity(handle, testData.getUserGuid(), testData.getStudyGuid());
            assertFieldOrdering(handle, act, SID_DATE_TEXT, DateFieldType.MONTH, DateFieldType.YEAR);
            handle.rollback();
        });
    }

    private void assertFieldOrdering(Handle handle, FormActivityDef act, String stableId, DateFieldType... expected) {
        long questionId = extractQuestion(act, stableId).getQuestionId();

        List<DateFieldType> fields = handle.attach(JdbiQuestion.class)
                .findQuestionDtoById(questionId)
                .map(dto -> ((DateQuestionDto) dto).getFields())
                .orElse(null);
        assertNotNull(fields);

        assertEquals(expected.length, fields.size());
        assertArrayEquals(expected, fields.toArray());
    }

    private DateQuestionDef extractQuestion(FormActivityDef activity, String stableId) {
        return activity.getSections().get(0).getBlocks().stream()
                .map(block -> (DateQuestionDef) ((QuestionBlockDef) block).getQuestion())
                .filter(question -> question.getStableId().equals(stableId))
                .findFirst().get();
    }

    private FormActivityDef insertDateActivity(Handle handle, String userGuid, String studyGuid) {
        DateQuestionDef d1 = DateQuestionDef.builder().setStableId(SID_DATE_TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "d1"))
                .setRenderMode(DateRenderMode.TEXT)
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .build();
        FormActivityDef form = FormActivityDef.generalFormBuilder("act", "v1", studyGuid)
                .addName(new Translation("en", "date test activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(d1)))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add date activity"));
        assertNotNull(form.getActivityId());
        return form;
    }
}
