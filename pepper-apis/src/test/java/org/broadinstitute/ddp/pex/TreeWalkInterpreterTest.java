package org.broadinstitute.ddp.pex;

import static org.broadinstitute.ddp.pex.RetrievedActivityInstanceType.LATEST;
import static org.broadinstitute.ddp.pex.RetrievedActivityInstanceType.SPECIFIC;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.AnswerDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TreeWalkInterpreterTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;
    private static String studyGuid;
    private static String activityCode;
    private static String firstInstanceGuid;
    private static String secondInstanceGuid;
    private static String boolStableId;
    private static String textStableId;
    private static String dateStableId;
    private static String picklistStableId;
    private static String conditionalControlStableId;
    private static String conditionalNestedStableId;
    private static String numericStableId;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = testData.getTestingUser().getUserGuid();
            setupActivityAndInstance(handle);
        });
    }

    private static void setupActivityAndInstance(Handle handle) {
        long timestamp = Instant.now().toEpochMilli();
        studyGuid = testData.getStudyGuid();

        boolStableId = "PEX_BOOL_" + timestamp;
        BoolQuestionDef boolDef = BoolQuestionDef.builder().setStableId(boolStableId)
                .setPrompt(new Template(TemplateType.TEXT, null, "bool prompt"))
                .setTrueTemplate(new Template(TemplateType.TEXT, null, "yes"))
                .setFalseTemplate(new Template(TemplateType.TEXT, null, "no"))
                .build();

        textStableId = "PEX_TEXT_" + timestamp;
        TextQuestionDef textDef = TextQuestionDef.builder().setStableId(textStableId)
                .setInputType(TextInputType.TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "text prompt"))
                .build();

        dateStableId = "PEX_DATE_" + timestamp;
        DateQuestionDef dateDef = DateQuestionDef.builder().setStableId(dateStableId)
                .setRenderMode(DateRenderMode.SINGLE_TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "date prompt"))
                .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                .build();

        picklistStableId = "PEX_PICKLIST_" + timestamp;
        PicklistQuestionDef picklistDef = PicklistQuestionDef.builder().setStableId(picklistStableId)
                .setSelectMode(PicklistSelectMode.MULTIPLE)
                .setRenderMode(PicklistRenderMode.LIST)
                .setPrompt(new Template(TemplateType.TEXT, null, "picklist prompt"))
                .addOption(new PicklistOptionDef("OPTION_YES", new Template(TemplateType.TEXT, null, "yes")))
                .addOption(new PicklistOptionDef("OPTION_NO", new Template(TemplateType.TEXT, null, "no")))
                .addOption(new PicklistOptionDef("OPTION_NA", new Template(TemplateType.TEXT, null, "n/a")))
                .build();

        numericStableId = "PEX_NUMERIC_" + timestamp;
        NumericQuestionDef numericDef = NumericQuestionDef.builder().setStableId(numericStableId)
                .setPrompt(Template.text("numeric prompt"))
                .setNumericType(NumericType.INTEGER)
                .build();

        conditionalControlStableId = "PEX_COND_CONTROL_TEXT_" + timestamp;
        conditionalNestedStableId = "PEX_COND_NESTED_TEXT_" + timestamp;
        ConditionalBlockDef condDef = new ConditionalBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, conditionalControlStableId, Template.text("control prompt")
                ).build());
        condDef.getNested().add(new QuestionBlockDef(
                TextQuestionDef.builder(
                        TextInputType.TEXT, conditionalNestedStableId, Template.text("nested prompt")
                )
                        .build()));

        activityCode = "PEX_ACT_" + timestamp;
        FormActivityDef form = FormActivityDef.generalFormBuilder(activityCode, "v1", studyGuid)
                .addName(new Translation("en", "pex test activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(boolDef, textDef, picklistDef, dateDef, numericDef)))
                .addSection(new FormSectionDef(null, Collections.singletonList(condDef)))
                .build();

        handle.attach(ActivityDao.class).insertActivity(form,
                RevisionMetadata.now(testData.getTestingUser().getUserId(), "insert pex test activity"));
        assertNotNull(form.getActivityId());

        ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
        firstInstanceGuid = activityInstanceDao.insertInstance(form.getActivityId(), userGuid).getGuid();
        secondInstanceGuid = activityInstanceDao.insertInstance(form.getActivityId(), userGuid).getGuid();
    }

    @Test
    public void testEval_lexingError() {
        thrown.expect(PexLexicalException.class);
        String expr = "unsupported chars @#$*";
        run(expr);
    }

    @Test
    public void testEval_parseError() {
        thrown.expect(PexParseException.class);
        String expr = "user.studies[\"A\"]";
        run(expr);
    }

    @Test
    public void testEval_missingData() {
        thrown.expect(PexFetchException.class);
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasTrue()",
                studyGuid, activityCode, "NON_EXIST_QUESTION");
        run(expr);
    }

    @Test
    public void testEval_boolLiteralExpr() {
        assertTrue(run("true"));
        assertFalse(run("false"));

        assertTrue(run("(true)"));
        assertTrue(run("true && true"));
        assertTrue(run("true || false"));
        assertTrue(run("false || (true && (false || true))"));
    }

    @Test
    public void testEval_groupExpr() {
        String expr = String.format(
                "(user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasTrue())",
                studyGuid, activityCode, boolStableId);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_andExpr_whenLeftIsTrue_rightIsEvaluated() {
        assertFalse(run("true && false"));
        assertTrue(run("true && true"));
    }

    @Test
    public void testEval_andExpr_whenLeftIsFalse_rightIsSkipped() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasTrue() && true",
                studyGuid, activityCode, boolStableId);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_orExpr_whenLeftIsTrue_rightIsSkipped() {
        assertTrue(run("true || false"));
    }

    @Test
    public void testEval_orExpr_whenLeftIsFalse_rightIsEvaluated() {
        String expr = String.format(
                "user.studies[\"%1$s\"].forms[\"%2$s\"].questions[\"%3$s\"].answers.hasTrue() || "
                        + "user.studies[\"%1$s\"].forms[\"%2$s\"].questions[\"%3$s\"].answers.hasFalse()",
                studyGuid, activityCode, boolStableId);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_notExpr_negates() {
        assertTrue(run("!false"));
    }

    @Test
    public void testEval_notExpr_precedence() {
        assertTrue(run("!false && !true || !false"));
    }

    @Test
    public void testEval_notExpr_withQuery() {
        String expr = String.format(
                "!user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasOption(\"OPTION_YES\")",
                studyGuid, activityCode, picklistStableId);
        TransactionWrapper.useTxn(handle -> {
            // No answers yet, so should satisfy expression
            assertTrue(run(handle, expr));

            SelectedPicklistOption option = new SelectedPicklistOption("OPTION_NO");
            PicklistAnswer answer = new PicklistAnswer(null, picklistStableId, null, Collections.singletonList(option));
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            // Should still satisfy expression
            assertTrue(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_invalidPredicateOnDefaultLatestAnswerQueryContext() {
        thrown.expect(PexUnsupportedException.class);
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasTrue()",
                studyGuid, activityCode, textStableId);
        run(expr);
    }

    @Test
    public void testEval_defaultLatestAnswerQueryContextQuery_conditionalBlock() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasText()",
                studyGuid, activityCode, conditionalControlStableId);
        assertFalse(run(expr));

        TransactionWrapper.useTxn(handle -> {
            TextAnswer answer = new TextAnswer(null, conditionalNestedStableId, null, "some answer");
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertTrue(run(handle, String.format(
                    "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasText()",
                    studyGuid, activityCode, conditionalNestedStableId)));

            handle.rollback();
        });
    }

    @Test
    public void testEval_boolDefaultLatestAnswerQueryContextQuery() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasTrue()",
                studyGuid, activityCode, boolStableId);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_boolDefaultLatestAnswerQueryContextQuery_hasTrueAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasTrue()",
                studyGuid, activityCode, boolStableId);
        TransactionWrapper.useTxn(handle -> {
            BoolAnswer answer = new BoolAnswer(null, boolStableId, null, true);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertTrue(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_boolDefaultLatestAnswerQueryContextQuery_hasFalseAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasFalse()",
                studyGuid, activityCode, boolStableId);
        TransactionWrapper.useTxn(handle -> {
            BoolAnswer answer = new BoolAnswer(null, boolStableId, null, false);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertTrue(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_dateDefaultLatestAnswerQueryContextQuery_ageAtLeast() {
        int minAge = 18;
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.ageAtLeast(" + minAge + ",YEARS)",
                studyGuid, activityCode, dateStableId);
        LocalDateTime rightNow = LocalDateTime.now();
        TransactionWrapper.useTxn(handle -> {

            // verify an age that is old enough
            DateAnswer oldEnoughAnswer = new DateAnswer(null, dateStableId, null, new DateValue(
                    rightNow.getYear() - minAge, rightNow.getMonthValue(), rightNow.getDayOfMonth()));
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            String answerGuid = answerDao.createAnswer(handle, oldEnoughAnswer, userGuid, firstInstanceGuid);
            assertNotNull(oldEnoughAnswer.getAnswerId());

            assertTrue(run(handle, expr));

            // verify age that is not old enough
            long answerId = answerDao.getAnswerIdByGuids(handle, firstInstanceGuid, answerGuid);
            DateAnswer notOldEnoughAnswer = new DateAnswer(answerId, dateStableId, null, new DateValue(
                    rightNow.getYear() - (minAge - 1), rightNow.getMonthValue(), rightNow.getDayOfMonth()));

            answerDao.updateAnswerById(handle, firstInstanceGuid, answerId, notOldEnoughAnswer, userGuid);

            assertFalse(run(handle, expr));

            // verify that a missing date is considered false in the context of having a minimal age
            answerDao.deleteAnswerByIdAndType(handle, answerId, QuestionType.DATE);
            assertFalse(run(handle, expr));


            handle.rollback();
        });
    }

    @Test
    public void testEval_dateDefaultLatestAnswerQueryContextQuery_hasDateAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasDate()",
                studyGuid, activityCode, dateStableId);
        LocalDateTime rightNow = LocalDateTime.now();
        TransactionWrapper.useTxn(handle -> {
            DateAnswer answer = new DateAnswer(null, dateStableId, null, new DateValue(rightNow.getYear(),
                    rightNow.getMonthValue(), rightNow.getDayOfMonth()));
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertTrue(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_textDefaultLatestAnswerQueryContextQuery_hasNoneBlankAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasText()",
                studyGuid, activityCode, textStableId);
        TransactionWrapper.useTxn(handle -> {
            TextAnswer answer = new TextAnswer(null, textStableId, null, "test");
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertTrue(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_textDefaultLatestAnswerQueryContextQuery_noAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasText()",
                studyGuid, activityCode, textStableId);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_picklistDefaultLatestAnswerQueryContextQuery_noAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasOption(\"OPTION_YES\")",
                studyGuid, activityCode, picklistStableId);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_picklistDefaultLatestAnswerQueryContextQuery_doesNotMatchAnswerWithSingleOption() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasOption(\"OPTION_YES\")",
                studyGuid, activityCode, picklistStableId);
        TransactionWrapper.useTxn(handle -> {
            SelectedPicklistOption option = new SelectedPicklistOption("OPTION_NO");
            PicklistAnswer answer = new PicklistAnswer(null, picklistStableId, null, Collections.singletonList(option));
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertFalse(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_picklistDefaultLatestAnswerQueryContextQuery_doesNotMatchAnswerWithMultipleOptions() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasOption(\"OPTION_YES\")",
                studyGuid, activityCode, picklistStableId);
        TransactionWrapper.useTxn(handle -> {
            List<SelectedPicklistOption> selected = new ArrayList<>();
            selected.add(new SelectedPicklistOption("OPTION_NO"));
            selected.add(new SelectedPicklistOption("OPTION_NA"));
            PicklistAnswer answer = new PicklistAnswer(null, picklistStableId, null, selected);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertFalse(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_picklistDefaultLatestAnswerQueryContextQuery_matchesAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasOption(\"OPTION_YES\")",
                studyGuid, activityCode, picklistStableId);
        TransactionWrapper.useTxn(handle -> {
            SelectedPicklistOption option = new SelectedPicklistOption("OPTION_YES");
            PicklistAnswer answer = new PicklistAnswer(null, picklistStableId, null, Collections.singletonList(option));
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertTrue(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_picklist_defaultLatestAnswer_hasAnyOption_whenNoOptions() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasAnyOption(\"OPTION_YES\", \"OPTION_NO\")",
                studyGuid, activityCode, picklistStableId);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_picklist_defaultLatestAnswer_hasAnyOption_whenChoiceMatch() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasAnyOption(\"OPTION_YES\", \"OPTION_NO\")",
                studyGuid, activityCode, picklistStableId);
        TransactionWrapper.useTxn(handle -> {
            SelectedPicklistOption option = new SelectedPicklistOption("OPTION_YES");
            PicklistAnswer answer = new PicklistAnswer(null, picklistStableId, null, Collections.singletonList(option));
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertTrue(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_picklist_defaultLatestAnswer_hasAnyOption_whenChoiceDoesNotMatch() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasAnyOption(\"OPTION_YES\", \"OPTION_NO\")",
                studyGuid, activityCode, picklistStableId);
        TransactionWrapper.useTxn(handle -> {
            SelectedPicklistOption option = new SelectedPicklistOption("OPTION_NA");
            PicklistAnswer answer = new PicklistAnswer(null, picklistStableId, null, Collections.singletonList(option));
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertFalse(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_numeric_defaultLatestAnswer_compare_noAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value() < 18",
                studyGuid, activityCode, numericStableId);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_numeric_defaultLatestAnswer_compare_nullAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value() < 18",
                studyGuid, activityCode, numericStableId);
        TransactionWrapper.useTxn(handle -> {
            NumericAnswer answer = new NumericIntegerAnswer(null, numericStableId, null, null);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertFalse(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_numeric_defaultLatestAnswer_compare_less() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value() < 18",
                studyGuid, activityCode, numericStableId);
        TransactionWrapper.useTxn(handle -> {
            NumericIntegerAnswer answer = new NumericIntegerAnswer(null, numericStableId, null, 7L);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            assertTrue(run(handle, expr));

            answer.setValue(21L);
            answerDao.updateAnswerById(handle, firstInstanceGuid, answer.getAnswerId(), answer, userGuid);
            assertFalse(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_numeric_defaultLatestAnswer_compare_lessThanOrEqual() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value() <= 18",
                studyGuid, activityCode, numericStableId);
        TransactionWrapper.useTxn(handle -> {
            NumericIntegerAnswer answer = new NumericIntegerAnswer(null, numericStableId, null, 7L);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            assertTrue(run(handle, expr));

            answer.setValue(18L);
            answerDao.updateAnswerById(handle, firstInstanceGuid, answer.getAnswerId(), answer, userGuid);
            assertTrue(run(handle, expr));

            answer.setValue(21L);
            answerDao.updateAnswerById(handle, firstInstanceGuid, answer.getAnswerId(), answer, userGuid);
            assertFalse(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_numeric_defaultLatestAnswer_compare_greater() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value() > 18",
                studyGuid, activityCode, numericStableId);
        TransactionWrapper.useTxn(handle -> {
            NumericIntegerAnswer answer = new NumericIntegerAnswer(null, numericStableId, null, 21L);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            assertTrue(run(handle, expr));

            answer.setValue(7L);
            answerDao.updateAnswerById(handle, firstInstanceGuid, answer.getAnswerId(), answer, userGuid);
            assertFalse(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_numeric_defaultLatestAnswer_compare_greaterThanOrEqual() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value() >= 18",
                studyGuid, activityCode, numericStableId);
        TransactionWrapper.useTxn(handle -> {
            NumericIntegerAnswer answer = new NumericIntegerAnswer(null, numericStableId, null, 21L);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            assertTrue(run(handle, expr));

            answer.setValue(18L);
            answerDao.updateAnswerById(handle, firstInstanceGuid, answer.getAnswerId(), answer, userGuid);
            assertTrue(run(handle, expr));

            answer.setValue(7L);
            answerDao.updateAnswerById(handle, firstInstanceGuid, answer.getAnswerId(), answer, userGuid);
            assertFalse(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_numeric_defaultLatestAnswer_compare_equals() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value() == 18",
                studyGuid, activityCode, numericStableId);
        TransactionWrapper.useTxn(handle -> {
            NumericIntegerAnswer answer = new NumericIntegerAnswer(null, numericStableId, null, 7L);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            assertFalse(run(handle, expr));

            answer.setValue(18L);
            answerDao.updateAnswerById(handle, firstInstanceGuid, answer.getAnswerId(), answer, userGuid);
            assertTrue(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_numeric_defaultLatestAnswer_compare_notEquals() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value() != 18",
                studyGuid, activityCode, numericStableId);
        TransactionWrapper.useTxn(handle -> {
            NumericIntegerAnswer answer = new NumericIntegerAnswer(null, numericStableId, null, 7L);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            assertTrue(run(handle, expr));

            answer.setValue(18L);
            answerDao.updateAnswerById(handle, firstInstanceGuid, answer.getAnswerId(), answer, userGuid);
            assertFalse(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_formQuery_success() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].isStatus(\"CREATED\")",
                studyGuid, activityCode);
        assertTrue(run(expr));
    }

    @Test
    public void testEval_formQuery_instanceNotHaveExpectedStatus() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].isStatus(\"COMPLETE\")",
                studyGuid, activityCode);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_formQuery_caseInsensitiveStatus() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].isStatus(\"created\")",
                studyGuid, activityCode);
        assertTrue(run(expr));
    }

    @Test
    public void testEval_formQuery_invalidStatus() {
        thrown.expect(PexUnsupportedException.class);
        thrown.expectMessage("Invalid status");
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].isStatus(\"some_unknown_status\")",
                studyGuid, activityCode);
        run(expr);
    }

    @Test
    public void testEval_formQuery_userDoesNotHaveInstance() {
        thrown.expect(PexFetchException.class);
        thrown.expectCause(instanceOf(NoSuchElementException.class));
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = FormActivityDef.generalFormBuilder("PEX_ANOTHER_ACT", "v1", studyGuid)
                    .addName(new Translation("en", "another pex test activity"))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form,
                    RevisionMetadata.now(testData.getTestingUser().getUserId(), "insert another pex test activity"));
            assertNotNull(form.getActivityId());
            String expr = String.format(
                    "user.studies[\"%s\"].forms[\"%s\"].isStatus(\"CREATED\")",
                    studyGuid, form.getActivityCode());
            run(handle, expr);
            fail("Expected exception was not thrown");
        });
    }

    @Test
    public void testEval_formQuery_isStatus_checksMultipleStatuses() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].isStatus(\"CREATED\", \"IN_PROGRESS\")",
                studyGuid, activityCode);
        assertTrue(run(expr));
        expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].isStatus(\"IN_PROGRESS\", \"COMPLETE\")",
                studyGuid, activityCode);
        assertFalse(run(expr));
    }

    @Test
    public void testEval_formQuery_hasInstance_exists() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].hasInstance()",
                studyGuid, activityCode);
        assertTrue(run(expr));
    }

    @Test
    public void testEval_formQuery_hasInstance_doesNotExists() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = FormActivityDef.generalFormBuilder("PEX_ANOTHER_ACT", "v1", studyGuid)
                    .addName(new Translation("en", "another pex test activity"))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form,
                    RevisionMetadata.now(testData.getTestingUser().getUserId(), "insert another pex test activity"));
            assertNotNull(form.getActivityId());
            String expr = String.format(
                    "user.studies[\"%s\"].forms[\"%s\"].hasInstance()",
                    studyGuid, form.getActivityCode());
            assertFalse(run(handle, expr));
            handle.rollback();
        });
    }

    @Test
    public void testEval_formQuery_hasInstance_nonExistActivityHasNoInstances() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].hasInstance()",
                studyGuid, "blah");
        assertFalse(run(expr));
    }

    private void testEval_textAnswerQuery(String expr, boolean expectedTestResult) {
        TransactionWrapper.useTxn(handle -> {
            TextAnswer answer = new TextAnswer(null, textStableId, null, "test");
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            answer = new TextAnswer(null, textStableId, null, null);
            answerDao.createAnswer(handle, answer, userGuid, secondInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertEquals(expectedTestResult, run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_textAnswerQuery_hasLatestTextAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[%s].questions[\"%s\"].answers.hasText()",
                studyGuid, activityCode, LATEST, textStableId);
        testEval_textAnswerQuery(expr, false);
    }

    @Test
    public void testEval_textAnswerQuery_hasSpecificTextAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[%s].questions[\"%s\"].answers.hasText()",
                studyGuid, activityCode, SPECIFIC, textStableId);
        testEval_textAnswerQuery(expr, true);
    }

    private void testEval_dateAnswerQuery(String expr, boolean expectedTestResult) {
        LocalDateTime rightNow = LocalDateTime.now();
        TransactionWrapper.useTxn(handle -> {
            DateAnswer answer = new DateAnswer(null, dateStableId, null, new DateValue(rightNow.getYear(),
                    rightNow.getMonthValue(), rightNow.getDayOfMonth()));
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            answer = new DateAnswer(null, dateStableId, null, new DateValue(rightNow.getYear(),
                    rightNow.getMonthValue(), rightNow.getDayOfMonth()));
            answerDao.createAnswer(handle, answer, userGuid, secondInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertEquals(expectedTestResult, run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_dateAnswerQuery_hasLatestDateAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[%s].questions[\"%s\"].answers.hasDate()",
                studyGuid, activityCode, LATEST, dateStableId);
        testEval_dateAnswerQuery(expr, true);
    }

    @Test
    public void testEval_dateAnswerQuery_hasSpecificDateAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[%s].questions[\"%s\"].answers.hasDate()",
                studyGuid, activityCode, SPECIFIC, dateStableId);
        testEval_dateAnswerQuery(expr, true);
    }

    private void testEval_boolAnswerQuery(String expr, boolean expectedTestResult) {
        TransactionWrapper.useTxn(handle -> {
            BoolAnswer answer = new BoolAnswer(null, boolStableId, null, true);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            answer = new BoolAnswer(null, boolStableId, null, false);
            answerDao.createAnswer(handle, answer, userGuid, secondInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertEquals(expectedTestResult, run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_boolAnswerQuery_hasLatestBoolAnswer_true() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[%s].questions[\"%s\"].answers.hasTrue()",
                studyGuid, activityCode, LATEST, boolStableId);
        testEval_boolAnswerQuery(expr, false);
    }

    @Test
    public void testEval_boolAnswerQuery_hasLatestBoolAnswer_false() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[%s].questions[\"%s\"].answers.hasFalse()",
                studyGuid, activityCode, LATEST, boolStableId);
        testEval_boolAnswerQuery(expr, true);
    }

    @Test
    public void testEval_boolAnswerQuery_hasSpecificBoolAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[%s].questions[\"%s\"].answers.hasTrue()",
                studyGuid, activityCode, SPECIFIC, boolStableId);
        testEval_boolAnswerQuery(expr, true);
    }

    public void testEval_picklistAnswerQuery(String expr, boolean expectedTestResult) {
        TransactionWrapper.useTxn(handle -> {
            List<SelectedPicklistOption> selected = new ArrayList<>();
            selected.add(new SelectedPicklistOption("OPTION_NO"));
            selected.add(new SelectedPicklistOption("OPTION_NA"));
            PicklistAnswer answer = new PicklistAnswer(null, picklistStableId, null, selected);
            AnswerDao answerDao = AnswerDao.fromSqlConfig(sqlConfig);
            answerDao.createAnswer(handle, answer, userGuid, firstInstanceGuid);
            assertNotNull(answer.getAnswerId());
            answer = new PicklistAnswer(null, picklistStableId, null, selected.subList(0, 1));
            answerDao.createAnswer(handle, answer, userGuid, secondInstanceGuid);
            assertNotNull(answer.getAnswerId());

            assertEquals(expectedTestResult, run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_picklistAnswerQuery_hasLatestAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[%s].questions[\"%s\"].answers.hasOption(\"OPTION_NA\")",
                studyGuid, activityCode, LATEST, picklistStableId);
        testEval_picklistAnswerQuery(expr, false);
    }


    @Test
    public void testEval_picklistAnswerQuery_hasSpecificAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[%s].questions[\"%s\"].answers.hasOption(\"OPTION_NA\")",
                studyGuid, activityCode, SPECIFIC, picklistStableId);
        testEval_picklistAnswerQuery(expr, true);
    }

    private boolean run(String expr) {
        return TransactionWrapper.withTxn(handle -> new TreeWalkInterpreter().eval(expr, handle, userGuid, firstInstanceGuid));
    }

    private boolean run(Handle handle, String expr) {
        return new TreeWalkInterpreter().eval(expr, handle, userGuid, firstInstanceGuid);
    }
}
