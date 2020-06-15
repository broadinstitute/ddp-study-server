package org.broadinstitute.ddp.service;

import java.time.Instant;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.route.RouteTestUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityValidationServiceTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;
    private static String activityInstanceGuid;
    private static long activityId;
    private static long langCodeId;
    private static FormActivityDef activity;
    private static ActivityVersionDto activityVersionDto;
    private static String activityCode;
    private static PexInterpreter interpreter;
    private static ActivityValidationService actValidationService = new ActivityValidationService();
    private static TextQuestionDef txt1;
    private static TextQuestionDef txt2;

    @BeforeClass
    public static void setup() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = testData.getUserGuid();
            setupActivityAndInstance(handle);
        });
        interpreter = new TreeWalkInterpreter();
    }

    private static void setupActivityAndInstance(Handle handle) throws Exception {
        txt1 = TextQuestionDef.builder(TextInputType.TEXT, "TEXT_Q1", newTemplate("TEXT_Q1_TMPL1"))
                .build();
        txt2 = TextQuestionDef.builder(TextInputType.TEXT, "TEXT_Q2", newTemplate("TEXT_Q1_TMPL2"))
                .build();
        FormSectionDef textSection = new FormSectionDef(null, TestUtil.wrapQuestions(txt1, txt2));
        // Inserting activity with questions
        activityCode = "ACT_VALID_SERVICE_TEST_ACT" + Instant.now().toEpochMilli();
        activity = FormActivityDef.generalFormBuilder(activityCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + activityCode))
                .addSections(List.of(textSection))
                .build();
        activityVersionDto = handle.attach(ActivityDao.class).insertActivity(
                activity, RevisionMetadata.now(testData.getUserId(), "add " + activityCode)
        );
        activityId = activity.getActivityId();
        Assert.assertNotNull(activityId);
        // Inserting activity instance
        ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class).insertInstance(activity.getActivityId(), userGuid);
        activityInstanceGuid = instanceDto.getGuid();
        // Inserting answers
        var answerDao = handle.attach(AnswerDao.class);
        answerDao.createAnswer(testData.getUserId(), instanceDto.getId(),
                new TextAnswer(null, txt1.getStableId(), null, "answer to question 1"));
        answerDao.createAnswer(testData.getUserId(), instanceDto.getId(),
                new TextAnswer(null, txt2.getStableId(), null, "answer to question 2"));
        langCodeId = LanguageStore.getOrComputeDefault(handle).getId();
    }

    private static Template newTemplate(String templateCode) {
        return new Template(TemplateType.TEXT, templateCode, "Test message");
    }

    @Test
    public void given_oneSuccessfulExpr_whenServiceIsCalled_thenItReturnsNoFailures() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, "false", "Should never fail");
            List<ActivityValidationFailure> validationFailures = actValidationService.validate(
                    handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId
            );
            Assert.assertTrue(validationFailures.isEmpty());
            handle.rollback();
        });
    }

    @Test
    public void given_oneFailingExpr_whenServiceIsCalled_thenItReturnsOneFailureWithCorrectErrorMessage() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, "true", "Should always fail");
            List<ActivityValidationFailure> validationFailures = actValidationService.validate(
                    handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId
            );
            Assert.assertEquals(1, validationFailures.size());
            Assert.assertNotNull(validationFailures.get(0));
            Assert.assertNotNull(validationFailures.get(0).getErrorMessage());
            Assert.assertEquals("Should always fail", validationFailures.get(0).getErrorMessage());
            handle.rollback();
        });
    }

    @Test
    public void given_oneFailingExprWithTwoAffectedQuestionStableIds_whenServiceIsCalled_thenItReturnsOneFailureAndBothAffectedFields() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, "true", "Should always fail");
            Assert.assertEquals(
                    2,
                    actValidationService.validate(
                            handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId
                    ).get(0).getAffectedQuestionStableIds().size()
            );
            handle.rollback();
        });
    }

    @Test
    public void given_twoFailingExpr_whenServiceIsCalled_thenItReturnsFailuresForBoth() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, "true", "Should always fail");
            insertValidation(handle, "true", "Should always fail");
            Assert.assertEquals(
                    2,
                    actValidationService.validate(handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId).size()
            );
            handle.rollback();
        });
    }

    @Test
    public void given_oneSuccessfulAndOneFailingExpr_whenServiceIsCalled_thenItReturnsFailuresOnlyForFailingExpr() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, "false", "Should never fail");
            insertValidation(handle, "true", "Should always fail");
            Assert.assertEquals(
                    1,
                    actValidationService.validate(handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId).size()
            );
            handle.rollback();
        });
    }

    @Test
    public void given_oneExprCausingPexException_whenServiceIsCalled_thenItIgnoresExceptionAndReturnsNoFailures() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, "true & true", "Should fail - syntax");
            Assert.assertTrue(
                    actValidationService.validate(handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId).isEmpty()
            );
            handle.rollback();
        });
    }

    @Test
    public void given_oneSuccessfulPreconditionAndOneFailingExpr_whenServiceIsCalled_thenExprGetsEvaluatedAndOneFailureIsReturned() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, "true", "true", "Precondition is TRUE - expression should fail");
            Assert.assertEquals(
                    1,
                    actValidationService.validate(handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId).size()
            );
            handle.rollback();
        });
    }

    @Test
    public void given_oneFailingPreconditionAndOneFailingExpr_whenServiceIsCalled_thenItReturnsNoFailures() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, "false", "true", "Precondition is FALSE - expression should NOT fail");
            Assert.assertTrue(
                    actValidationService.validate(handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId).isEmpty()
            );
            handle.rollback();
        });
    }

    @Test
    public void given_oneUndefinedPreconditionAndOneFailingExpr_whenServiceIsCalled_thenExprGetsEvaluatedAndOneFailureIsReturned() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, null, "true", "Precondition is NULL - expression should NOT fail");
            Assert.assertEquals(
                    1,
                    actValidationService.validate(handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId).size()
            );
            handle.rollback();
        });
    }

    @Test
    public void given_oneMalformedPreconditionAndOneFailingExpr_whenServiceIsCalled_thenExprNeverGetsExecutedAndThusDoesntFail() {
        TransactionWrapper.useTxn(handle -> {
            insertValidation(handle, "true & true", "true", "Precondition is malformed - expression should NOT fail");
            Assert.assertTrue(
                    actValidationService.validate(handle, interpreter, userGuid, activityInstanceGuid, activityId, langCodeId).isEmpty()
            );
            handle.rollback();
        });
    }

    private void insertValidation(Handle handle, String expr, String errMsg) {
        insertValidation(handle, null, expr, errMsg);
    }

    private void insertValidation(Handle handle, String precondition, String expr, String errMsg) {
        handle.attach(JdbiActivity.class).insertValidation(
                RouteTestUtil.createActivityValidationDto(
                                activity,
                                precondition,
                                expr,
                                errMsg,
                                List.of(txt1.getStableId(), txt2.getStableId()
                        )
                ),
                testData.getUserId(),
                testData.getStudyId(),
                activityVersionDto.getRevId()
        );
    }
}
