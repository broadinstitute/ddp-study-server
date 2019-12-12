package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.AnswerDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityStatusQuery.FormQuestionRequirementStatus;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiFormActivityStatusQueryTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    private String sid;
    private Template prompt;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Before
    public void refreshTestData() {
        sid = "SID" + Instant.now().toEpochMilli();
        prompt = new Template(TemplateType.TEXT, null, "question prompt");
    }

    @Test
    public void testQueryByActivityInstanceGuid_boolean() {
        Template trueTmpl = new Template(TemplateType.TEXT, null, "yup");
        Template falseTmpl = new Template(TemplateType.TEXT, null, "nope");
        BoolQuestionDef question = BoolQuestionDef.builder(sid, prompt, trueTmpl, falseTmpl)
                .addValidation(new RequiredRuleDef(null))
                .build();
        BoolAnswer answer = new BoolAnswer(null, sid, null, true);
        runStatusQueryTest(question, answer);
    }

    @Test
    public void testQueryByActivityInstanceGuid_text() {
        TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                .addValidation(new RequiredRuleDef(null))
                .build();
        TextAnswer answer = new TextAnswer(null, sid, null, "test value");
        runStatusQueryTest(question, answer);
    }

    @Test
    public void testQueryByActivityInstanceGuid_picklist() {
        PicklistQuestionDef question = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOption(new PicklistOptionDef("PO1", new Template(TemplateType.TEXT, null, "option1")))
                .addValidation(new RequiredRuleDef(null))
                .build();
        PicklistAnswer answer = new PicklistAnswer(null, sid, null, Collections.singletonList(new SelectedPicklistOption("PO1")));
        runStatusQueryTest(question, answer);
    }

    private void runStatusQueryTest(QuestionDef question, Answer<?> answer) {
        TransactionWrapper.useTxn(handle -> {
            JdbiFormActivityStatusQuery jdbiStatusQuery = handle.attach(JdbiFormActivityStatusQuery.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);

            // Another question that have no requirements.
            TextQuestionDef another = TextQuestionDef.builder(TextInputType.TEXT, "another123",
                    new Template(TemplateType.TEXT, null, "another question")).build();

            FormActivityDef form = setupActivity(handle, testData.getStudyGuid(), new QuestionBlockDef(question),
                    new QuestionBlockDef(another));
            ActivityInstanceDto instanceDto = instanceDao.insertInstance(form.getActivityId(), testData.getUserGuid());

            // Should have unmet requirements.
            List<FormQuestionRequirementStatus> reqs = jdbiStatusQuery.queryByActivityInstanceGuid(instanceDto.getGuid());
            assertFalse(reqs.isEmpty());
            for (FormQuestionRequirementStatus req : reqs) {
                assertEquals(question.getStableId(), req.getStableId());
                assertTrue(req.getStableId() + " should have an unmet requirement", req.hasUnmetAnswerRequirement());
            }

            // Answer the question.
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, testData.getUserGuid(), instanceDto.getGuid());

            // Should meet requirements.
            reqs = jdbiStatusQuery.queryByActivityInstanceGuid(instanceDto.getGuid());
            assertFalse(reqs.isEmpty());
            for (FormQuestionRequirementStatus req : reqs) {
                assertEquals(question.getStableId(), req.getStableId());
                assertFalse(req.hasUnmetAnswerRequirement());
            }

            handle.rollback();
        });
    }

    @Test
    public void testQueryByActivityInstanceGuid_whenQuestionIsRevisioned() {
        TransactionWrapper.useTxn(handle -> {
            JdbiFormActivityStatusQuery jdbiStatusQuery = handle.attach(JdbiFormActivityStatusQuery.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);

            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                    .addValidation(new RequiredRuleDef(null))
                    .build();

            FormActivityDef form = setupActivity(handle, testData.getStudyGuid(), new QuestionBlockDef(question));
            ActivityInstanceDto instWithReqs = instanceDao.insertInstance(form.getActivityId(), testData.getUserGuid());

            List<FormQuestionRequirementStatus> reqs = jdbiStatusQuery.queryByActivityInstanceGuid(instWithReqs.getGuid());
            assertFalse(reqs.isEmpty());
            for (FormQuestionRequirementStatus req : reqs) {
                assertEquals(question.getStableId(), req.getStableId());
                assertTrue(req.hasUnmetAnswerRequirement());
            }

            handle.attach(QuestionDao.class).disableRequiredRule(question.getQuestionId(),
                    RevisionMetadata.now(testData.getUserId(), "remove required"));
            ActivityInstanceDto instWithoutReqs = instanceDao.insertInstance(form.getActivityId(), testData.getUserGuid());

            reqs = jdbiStatusQuery.queryByActivityInstanceGuid(instWithoutReqs.getGuid());
            assertTrue(reqs.isEmpty());

            handle.rollback();
        });
    }

    private FormActivityDef setupActivity(Handle handle, String studyGuid, QuestionBlockDef... questions) {
        String code = "ACT" + Instant.now().toEpochMilli();
        FormActivityDef form = FormActivityDef.generalFormBuilder(code, "v1", studyGuid)
                .addName(new Translation("en", "dummy activity"))
                .addSection(new FormSectionDef(null, Arrays.asList(questions)))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add dummy form"));
        assertNotNull(form.getActivityId());
        return form;
    }

    private FormQuestionRequirementStatus buildForBoolean(boolean hasAnswer) {
        return new FormQuestionRequirementStatus("q",
                QuestionType.BOOLEAN.name(),
                false,
                hasAnswer,
                null,
                0L);
    }

    private FormQuestionRequirementStatus buildForText(boolean hasAnswer) {
        return new FormQuestionRequirementStatus("q",
                QuestionType.TEXT.name(),
                hasAnswer,
                false,
                null,
                0L);
    }

    private FormQuestionRequirementStatus buildForPicklist(int minSelectionsRequired, long actualSelections) {
        return new FormQuestionRequirementStatus("q",
                QuestionType.PICKLIST.name(),
                false,
                false,
                minSelectionsRequired,
                actualSelections);
    }

    @Test
    public void testRequiredBoolean() {
        assertFalse(buildForBoolean(true).hasUnmetAnswerRequirement());
        Assert.assertTrue(buildForBoolean(false).hasUnmetAnswerRequirement());
    }

    @Test
    public void testRequiredText() {
        assertFalse(buildForText(true).hasUnmetAnswerRequirement());
        Assert.assertTrue(buildForText(false).hasUnmetAnswerRequirement());
    }

    @Test
    public void testRequiredPicklist() {
        assertFalse(buildForPicklist(0, 0).hasUnmetAnswerRequirement());
        assertFalse(buildForPicklist(0, 2).hasUnmetAnswerRequirement());
        assertFalse(buildForPicklist(1, 1).hasUnmetAnswerRequirement());
        assertFalse(buildForPicklist(1, 3).hasUnmetAnswerRequirement());

        Assert.assertTrue(buildForPicklist(1, 0).hasUnmetAnswerRequirement());
        Assert.assertTrue(buildForPicklist(2, 1).hasUnmetAnswerRequirement());
    }
}
