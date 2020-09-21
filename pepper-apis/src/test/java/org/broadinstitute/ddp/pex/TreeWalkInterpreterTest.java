package org.broadinstitute.ddp.pex;

import static org.broadinstitute.ddp.pex.RetrievedActivityInstanceType.LATEST;
import static org.broadinstitute.ddp.pex.RetrievedActivityInstanceType.SPECIFIC;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
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
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.dsm.TestResult;
import org.broadinstitute.ddp.model.event.ActivityInstanceStatusChangeSignal;
import org.broadinstitute.ddp.model.event.DsmNotificationSignal;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TreeWalkInterpreterTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static StudyDto testStudy;
    private static String userGuid;
    private static String studyGuid;
    private static String activityCode;
    private static ActivityInstanceDto firstInstance;
    private static ActivityInstanceDto secondInstance;
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
            testStudy = TestDataSetupUtil.generateTestStudy(handle, ConfigManager.getInstance().getConfig());
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
        firstInstance = activityInstanceDao.insertInstance(form.getActivityId(), userGuid);
        secondInstance = activityInstanceDao.insertInstance(form.getActivityId(), userGuid);

        activityInstanceDao.saveSubstitutions(firstInstance.getId(), Map.of(
                I18nTemplateConstants.Snapshot.TEST_RESULT_CODE, "NEGATIVE"));
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
    public void testEval_finalResultNonBool() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("result of expression needs to be a bool value"));
        String expr = "-125";
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
    public void testEval_relationExpr() {
        assertTrue(run("1 < 2"));
        assertTrue(run("1 <= 2"));
        assertTrue(run("3 > 2"));
        assertTrue(run("3 >= 2"));
    }

    @Test
    public void testEval_equalityExpr() {
        assertTrue(run("true == true"));
        assertTrue(run("1 != \"a\""));
        assertTrue(run("5 < 0 == false"));
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
    public void testEval_notExpr_complements() {
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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            var answerDao = handle.attach(AnswerDao.class);

            // verify an age that is old enough
            var oldEnoughAnswer = answerDao.createAnswer(testData.getUserId(), firstInstance.getId(),
                    new DateAnswer(null, dateStableId, null, new DateValue(
                            rightNow.getYear() - minAge, rightNow.getMonthValue(), rightNow.getDayOfMonth())));

            assertTrue(run(handle, expr));

            // verify age that is not old enough
            long answerId = oldEnoughAnswer.getAnswerId();
            var notOldEnoughAnswer = new DateAnswer(answerId, dateStableId, null, new DateValue(
                    rightNow.getYear() - (minAge - 1), rightNow.getMonthValue(), rightNow.getDayOfMonth()));

            answerDao.updateAnswer(testData.getUserId(), answerId, notOldEnoughAnswer);

            assertFalse(run(handle, expr));

            // verify that a missing date is considered false in the context of having a minimal age
            answerDao.deleteAnswer(answerId);
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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

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
            var answerDao = handle.attach(AnswerDao.class);
            var answer = (NumericIntegerAnswer) answerDao.createAnswer(testData.getUserId(), firstInstance.getId(),
                    new NumericIntegerAnswer(null, numericStableId, null, 7L));
            assertTrue(run(handle, expr));

            answer.setValue(21L);
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), answer);
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
            var answerDao = handle.attach(AnswerDao.class);
            var answer = (NumericIntegerAnswer) answerDao.createAnswer(testData.getUserId(), firstInstance.getId(),
                    new NumericIntegerAnswer(null, numericStableId, null, 7L));
            assertTrue(run(handle, expr));

            answer.setValue(18L);
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), answer);
            assertTrue(run(handle, expr));

            answer.setValue(21L);
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), answer);
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
            AnswerDao answerDao = handle.attach(AnswerDao.class);
            var answer = (NumericIntegerAnswer) answerDao.createAnswer(testData.getUserId(), firstInstance.getId(),
                    new NumericIntegerAnswer(null, numericStableId, null, 21L));
            assertTrue(run(handle, expr));

            answer.setValue(7L);
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), answer);
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
            var answerDao = handle.attach(AnswerDao.class);
            var answer = (NumericIntegerAnswer) answerDao.createAnswer(testData.getUserId(), firstInstance.getId(),
                    new NumericIntegerAnswer(null, numericStableId, null, 21L));
            assertTrue(run(handle, expr));

            answer.setValue(18L);
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), answer);
            assertTrue(run(handle, expr));

            answer.setValue(7L);
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), answer);
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
            var answerDao = handle.attach(AnswerDao.class);
            var answer = (NumericIntegerAnswer) answerDao.createAnswer(testData.getUserId(), firstInstance.getId(),
                    new NumericIntegerAnswer(null, numericStableId, null, 7L));
            assertFalse(run(handle, expr));

            answer.setValue(18L);
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), answer);
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
            var answerDao = handle.attach(AnswerDao.class);
            var answer = (NumericIntegerAnswer) answerDao.createAnswer(testData.getUserId(), firstInstance.getId(),
                    new NumericIntegerAnswer(null, numericStableId, null, 7L));
            assertTrue(run(handle, expr));

            answer.setValue(18L);
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), answer);
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

    @Test
    public void testEval_formInstanceQuery_snapshotSubstitution() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].instances[specific]"
                        + ".snapshotSubstitution(\"DDP_TEST_RESULT_CODE\") == \"NEGATIVE\"",
                studyGuid, activityCode);
        assertTrue(run(expr));
    }

    private void testEval_textAnswerQuery(String expr, boolean expectedTestResult) {
        TransactionWrapper.useTxn(handle -> {
            var answerDao = handle.attach(AnswerDao.class);
            var answer = new TextAnswer(null, textStableId, null, "test");
            answerDao.createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            answer = new TextAnswer(null, textStableId, null, null);
            answerDao.createAnswer(testData.getUserId(), secondInstance.getId(), answer);

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
            var answerDao = handle.attach(AnswerDao.class);
            var answer = new DateAnswer(null, dateStableId, null, new DateValue(rightNow.getYear(),
                    rightNow.getMonthValue(), rightNow.getDayOfMonth()));
            answerDao.createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            answer = new DateAnswer(null, dateStableId, null, new DateValue(rightNow.getYear(),
                    rightNow.getMonthValue(), rightNow.getDayOfMonth()));
            answerDao.createAnswer(testData.getUserId(), secondInstance.getId(), answer);

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
            var answerDao = handle.attach(AnswerDao.class);
            var answer = new BoolAnswer(null, boolStableId, null, true);
            answerDao.createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            answer = new BoolAnswer(null, boolStableId, null, false);
            answerDao.createAnswer(testData.getUserId(), secondInstance.getId(), answer);

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
            var answer = new PicklistAnswer(null, picklistStableId, null, selected);
            var answerDao = handle.attach(AnswerDao.class);
            answerDao.createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            answer = new PicklistAnswer(null, picklistStableId, null, selected.subList(0, 1));
            answerDao.createAnswer(testData.getUserId(), secondInstance.getId(), answer);

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

    @Test
    public void testEval_questionQuery_unknownActivity() {
        thrown.expect(PexFetchException.class);
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].isAnswered()",
                "abc", "xyz", boolStableId);
        run(expr);
    }

    @Test
    public void testEval_questionQuery_isAnswered() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].isAnswered()",
                studyGuid, activityCode, boolStableId);
        assertFalse("should be false because question is not answered yet", run(expr));

        TransactionWrapper.useTxn(handle -> {
            var answerDao = handle.attach(AnswerDao.class);
            var answer = new BoolAnswer(null, boolStableId, null, false);
            answerDao.createAnswer(testData.getUserId(), firstInstance.getId(), answer);

            assertFalse("should be false because first instance is not latest", run(handle, expr));

            answer = new BoolAnswer(null, boolStableId, null, false);
            answerDao.createAnswer(testData.getUserId(), secondInstance.getId(), answer);

            assertTrue("should be true because latest instance is answered", run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_questionQuery_isAnswered_nonNullAnswer() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].isAnswered()",
                studyGuid, activityCode, numericStableId);
        assertFalse("should be false because question is not answered yet", run(expr));

        TransactionWrapper.useTxn(handle -> {
            var answerDao = handle.attach(AnswerDao.class);
            var answer = new NumericIntegerAnswer(null, numericStableId, null, 25L);
            answerDao.createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            assertFalse("should be false because first instance is not latest", run(handle, expr));

            answer = (NumericIntegerAnswer) answerDao.createAnswer(testData.getUserId(), secondInstance.getId(),
                    new NumericIntegerAnswer(null, numericStableId, null, null));
            assertFalse("should be false because answer is null", run(handle, expr));

            var newAnswer = new NumericIntegerAnswer(null, textStableId, null, 50L);
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), newAnswer);
            assertTrue("should be true because answer is non-null", run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_questionQuery_isAnswered_picklistNonEmpty() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].isAnswered()",
                studyGuid, activityCode, picklistStableId);
        assertFalse("should be false because question is not answered yet", run(expr));

        TransactionWrapper.useTxn(handle -> {
            var answerDao = handle.attach(AnswerDao.class);
            var answer = answerDao.createAnswer(testData.getUserId(), secondInstance.getId(),
                    new PicklistAnswer(null, picklistStableId, null, List.of()));
            assertFalse("should be false because picklist selection is empty", run(handle, expr));

            var newAnswer = new PicklistAnswer(null, picklistStableId, null, List.of(
                    new SelectedPicklistOption("OPTION_YES")));
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), newAnswer);
            assertTrue("should be true because picklist selection is non-empty", run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_questionQuery_isAnswered_textNonEmpty() {
        String expr = String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].isAnswered()",
                studyGuid, activityCode, textStableId);
        assertFalse("should be false because question is not answered yet", run(expr));

        TransactionWrapper.useTxn(handle -> {
            var answerDao = handle.attach(AnswerDao.class);
            var answer = answerDao.createAnswer(testData.getUserId(), secondInstance.getId(),
                    new TextAnswer(null, textStableId, null, ""));
            assertFalse("should be false because text answer is empty", run(handle, expr));

            var newAnswer = new TextAnswer(null, textStableId, null, "abc");
            answerDao.updateAnswer(testData.getUserId(), answer.getAnswerId(), newAnswer);
            assertTrue("should be true because text answer is non-empty", run(handle, expr));

            handle.rollback();
        });
    }

    @Test(expected = PexFetchException.class)
    public void test_givenMissingGovernancePolicy_whenHasAgedUpIsEvaluated_thenItThrowsAnException() {
        String expr = String.format("user.studies[\"%s\"].hasAgedUp()", studyGuid);
        run(expr);
    }

    @Test
    public void test_givenParticipantReachedAgeOfMajority_whenHasAgedUpIsEvaluated_thenItReturnsTrue() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(UserProfileDao.class).getUserProfileSql().upsertBirthDate(
                    testData.getTestingUser().getUserId(), LocalDate.now().minusYears(20)
            );
            GovernancePolicy policy = new GovernancePolicy(testData.getStudyId(), new Expression("true"));
            policy.addAgeOfMajorityRule(
                    new AgeOfMajorityRule("true", 18, null)
            );
            StudyGovernanceDao studyGovernanceDao = handle.attach(StudyGovernanceDao.class);
            studyGovernanceDao.createPolicy(policy);
            String expr = String.format("user.studies[\"%s\"].hasAgedUp()", studyGuid);
            assertTrue(run(handle, expr));
            handle.rollback();
        });
    }

    @Test
    public void test_givenParticipantDidntReachAgeOfMajority_whenHasAgedUpIsEvaluated_thenItReturnsFalse() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(UserProfileDao.class).getUserProfileSql().upsertBirthDate(
                    testData.getTestingUser().getUserId(), LocalDate.now().minusYears(12)
            );
            GovernancePolicy policy = new GovernancePolicy(testData.getStudyId(), new Expression("true"));
            policy.addAgeOfMajorityRule(
                    new AgeOfMajorityRule("true", 18, null)
            );
            StudyGovernanceDao studyGovernanceDao = handle.attach(StudyGovernanceDao.class);
            studyGovernanceDao.createPolicy(policy);
            String expr = String.format("user.studies[\"%s\"].hasAgedUp()", studyGuid);
            assertFalse(run(handle, expr));
            handle.rollback();
        });
    }

    @Test
    public void testEval_valueQuery_bool() {
        String expr = String.format(
                "false == user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, boolStableId);
        TransactionWrapper.useTxn(handle -> {
            var answer = new BoolAnswer(null, boolStableId, null, false);
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            assertTrue(run(handle, expr));
            handle.rollback();
        });
    }

    @Test
    public void testEval_valueQuery_bool_noAnswer() {
        thrown.expect(PexFetchException.class);
        thrown.expectMessage(containsString("does not have boolean answer"));
        String expr = String.format(
                "false == user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, boolStableId);
        run(expr);
    }

    @Test
    public void testEval_valueQuery_text() {
        String expr = String.format(
                "\"foo\" == user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, textStableId);
        TransactionWrapper.useTxn(handle -> {
            var answer = new TextAnswer(null, textStableId, null, "foo");
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            assertTrue(run(handle, expr));
            handle.rollback();
        });
    }

    @Test
    public void testEval_valueQuery_text_noAnswer() {
        thrown.expect(PexFetchException.class);
        thrown.expectMessage(containsString("does not have text answer"));
        String expr = String.format(
                "false == user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, textStableId);
        run(expr);
    }

    @Test
    public void testEval_valueQuery_date() {
        String expr = String.format(
                "1 != user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, dateStableId);
        TransactionWrapper.useTxn(handle -> {
            var answer = new DateAnswer(null, dateStableId, null, 2012, 3, 14);
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            assertTrue(run(handle, expr));
            handle.rollback();
        });
    }

    @Test
    public void testEval_valueQuery_date_notFullDate() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("Could not convert date answer"));
        String expr = String.format(
                "1 != user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, dateStableId);
        TransactionWrapper.useTxn(handle -> {
            var answer = new DateAnswer(null, dateStableId, null, 2012, null, 14);
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            run(handle, expr);
            handle.rollback();
        });
    }

    @Test
    public void testEval_valueQuery_date_noAnswer() {
        thrown.expect(PexFetchException.class);
        thrown.expectMessage(containsString("does not have date answer"));
        String expr = String.format(
                "1 != user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, dateStableId);
        run(expr);
    }

    @Test
    public void testEval_valueQuery_numeric() {
        String expr = String.format(
                "18 <= user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, numericStableId);
        TransactionWrapper.useTxn(handle -> {
            var answer = new NumericIntegerAnswer(null, numericStableId, null, 21L);
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);
            assertTrue(run(handle, expr));
            handle.rollback();
        });
    }

    @Test
    public void testEval_valueQuery_numeric_noAnswer() {
        thrown.expect(PexFetchException.class);
        thrown.expectMessage(containsString("does not have numeric answer"));
        String expr = String.format(
                "18 <= user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, numericStableId);
        run(expr);
    }

    @Test
    public void testEval_valueQuery_picklist_notSupported() {
        thrown.expect(PexUnsupportedException.class);
        thrown.expectMessage(containsString("picklist answer value"));
        String expr = String.format(
                "18 != user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.value()",
                studyGuid, activityCode, picklistStableId);
        run(expr);
    }

    @Test
    public void testEval_profileQuery_noProfile() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(UserProfileDao.class).getUserProfileSql().deleteByUserId(testData.getUserId());

            try {
                assertTrue(run(handle, "user.profile.birthDate()"));
                fail("expected exception not thrown");
            } catch (PexFetchException e) {
                assertTrue(e.getMessage().contains("Could not find profile"));
            }

            handle.rollback();
        });
    }

    @Test
    public void testEval_profileQuery_birthDate() {
        TransactionWrapper.useTxn(handle -> {
            var profileDao = handle.attach(UserProfileDao.class);
            assertTrue(profileDao.getUserProfileSql().upsertBirthDate(testData.getUserId(), LocalDate.of(2002, 3, 14)));

            var answer = new DateAnswer(null, dateStableId, null, 2002, 3, 14);
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), firstInstance.getId(), answer);

            String expr = String.format("user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"]"
                            + ".answers.value() == user.profile.birthDate()",
                    studyGuid, activityCode, dateStableId);
            assertTrue(run(handle, expr));
            handle.rollback();
        });
    }

    @Test
    public void testEval_profileQuery_birthDate_none() {
        TransactionWrapper.useTxn(handle -> {
            var profileDao = handle.attach(UserProfileDao.class);
            assertTrue(profileDao.getUserProfileSql().upsertBirthDate(testData.getUserId(), null));

            try {
                assertTrue(run(handle, "user.profile.birthDate()"));
                fail("expected exception not thrown");
            } catch (PexFetchException e) {
                assertTrue(e.getMessage().contains("does not have birth date"));
            }

            handle.rollback();
        });
    }

    @Test
    public void testEval_hasInvitation() {
        TransactionWrapper.useTxn(handle -> {
            String fmt = "user.studies[\"%s\"].hasInvitation(\"%s\")";
            String expr = String.format(fmt, testStudy.getGuid(), InvitationType.RECRUITMENT);
            assertFalse(run(handle, expr));

            var factory = handle.attach(InvitationFactory.class);
            var inviteDao = handle.attach(InvitationDao.class);
            InvitationDto invitation = factory.createRecruitmentInvitation(testStudy.getId(), "invite" + System.currentTimeMillis());
            inviteDao.assignAcceptingUser(invitation.getInvitationId(), testData.getUserId(), Instant.now());
            assertTrue(run(handle, expr));

            expr = String.format(fmt, testStudy.getGuid(), InvitationType.AGE_UP);
            assertFalse(run(handle, expr));

            factory.createAgeUpInvitation(testStudy.getId(), testData.getUserId(),
                    "invite" + System.currentTimeMillis() + "@datadonationplatform.org");
            assertTrue(run(handle, expr));

            handle.rollback();
        });
    }

    @Test
    public void testEval_eventTestResult() {
        String expr = "user.event.testResult.isCorrected()";
        EventSignal signal = newDsmEventSignal(new TestResult("NEGATIVE", Instant.now(), true));
        assertTrue(runEvalEventSignal(expr, signal));

        expr = "user.event.testResult.isCorrected()";
        signal = newDsmEventSignal(new TestResult("NEGATIVE", Instant.now(), false));
        assertFalse(runEvalEventSignal(expr, signal));

        expr = "user.event.testResult.isPositive()";
        signal = newDsmEventSignal(new TestResult("Positive", Instant.now(), false));
        assertTrue(runEvalEventSignal(expr, signal));

        expr = "user.event.testResult.isPositive()";
        signal = newDsmEventSignal(new TestResult("INCONCLUSIVE", Instant.now(), false));
        assertFalse(runEvalEventSignal(expr, signal));

        expr = "user.event.testResult.isCorrected() && user.event.testResult.isPositive()";
        signal = newDsmEventSignal(new TestResult("POS", Instant.now(), true));
        assertTrue(runEvalEventSignal(expr, signal));

        expr = "user.event.testResult.isCorrected() && !user.event.testResult.isPositive()";
        signal = newDsmEventSignal(new TestResult("NEG", Instant.now(), true));
        assertTrue(runEvalEventSignal(expr, signal));
    }

    @Test
    public void testEval_eventTestResult_notEvent() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("Expected event signal"));
        String expr = "user.event.testResult.isCorrected()";
        assertTrue(run(expr));
    }

    @Test
    public void testEval_eventTestResult_notDsmTestResult() {
        thrown.expect(PexRuntimeException.class);
        thrown.expectMessage(containsString("Expected DSM notification"));
        String expr = "user.event.testResult.isCorrected()";
        EventSignal signal = new ActivityInstanceStatusChangeSignal(1L, 1L, "guid", 2L, 3L, 4L, InstanceStatusType.COMPLETE);
        assertTrue(runEvalEventSignal(expr, signal));
    }

    private boolean run(String expr) {
        return TransactionWrapper.withTxn(handle -> new TreeWalkInterpreter().eval(expr, handle, userGuid, firstInstance.getGuid()));
    }

    private boolean run(Handle handle, String expr) {
        return new TreeWalkInterpreter().eval(expr, handle, userGuid, firstInstance.getGuid());
    }

    private boolean runEvalEventSignal(String expr, EventSignal signal) {
        return TransactionWrapper.withTxn(handle -> new TreeWalkInterpreter()
                .eval(expr, handle, userGuid, firstInstance.getGuid(), null, signal));
    }

    private DsmNotificationSignal newDsmEventSignal(TestResult testResult) {
        return new DsmNotificationSignal(
                testData.getUserId(),
                testData.getUserId(),
                userGuid,
                testData.getStudyId(),
                DsmNotificationEventType.TEST_RESULT,
                testResult);
    }
}
