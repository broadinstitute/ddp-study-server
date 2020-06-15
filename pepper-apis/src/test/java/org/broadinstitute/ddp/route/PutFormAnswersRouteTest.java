package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiWorkflowTransition;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class PutFormAnswersRouteTest extends IntegrationTestSuite.TestCase {

    private static ActivityVersionDto activityVersionDto;
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Auth0Util.TestingUser user;
    private static FormActivityDef form;
    private static ConditionalBlockDef conditionalBlock;
    private static long studyId;
    private static String stableId;
    private static String token;
    private static String urlTemplate;
    private static FormActivityDef compositeQuestionForm;

    private List<String> instanceGuidsToDelete = new ArrayList<>();
    private List<Long> transitionIdsToDelete = new ArrayList<>();
    //allow tests to add something unique to cleanup
    private List<Consumer<Handle>> testCleanupTasks = new ArrayList<>();

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            user = testData.getTestingUser();
            token = testData.getTestingUser().getToken();
            studyId = testData.getStudyId();
            stableId = "PUT_STATUS_Q" + Instant.now().toEpochMilli();
            form = setupActivity(handle, user.getUserId(), testData.getStudyGuid(), stableId);
        });
        String endpoint = API.USER_ACTIVITY_ANSWERS
                .replace(PathParam.USER_GUID, user.getUserGuid())
                .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(PathParam.INSTANCE_GUID, "{instanceGuid}");
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static FormActivityDef setupActivity(Handle handle, long userId, String studyGuid, String stableId) {

        TextQuestionDef control = TextQuestionDef.builder(TextInputType.TEXT, stableId + "CONTROL",
                new Template(TemplateType.TEXT, null, "control"))
                //since these tests are about final activity PUT, this rule should simply be ignored
                .addValidation(new LengthRuleDef(null, 0, 1000))
                .build();
        QuestionBlockDef requiredChild = new QuestionBlockDef(BoolQuestionDef.builder(stableId + "REQ_NESTED",
                new Template(TemplateType.TEXT, null, "required child"), new Template(TemplateType.TEXT, null, "yes"),
                new Template(TemplateType.TEXT, null, "no"))
                .addValidation(new RequiredRuleDef(null))
                .build());
        requiredChild.setShownExpr("false");
        conditionalBlock = new ConditionalBlockDef(control);
        conditionalBlock.getNested().add(requiredChild);


        Template prompt = new Template(TemplateType.TEXT, null, "prompt");

        LengthRuleDef allowSaveTrueLengthRuleDef = new LengthRuleDef(null, 2, 500);
        allowSaveTrueLengthRuleDef.setAllowSave(true);
        TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, stableId, prompt)
                // another rule that should not be executed in PUT
                .addValidation(new LengthRuleDef(null, 0, 1000))
                .addValidation(allowSaveTrueLengthRuleDef)
                .build();
        String code = "PUT_STATUS_ACT" + Instant.now().toEpochMilli();
        FormActivityDef form = FormActivityDef.generalFormBuilder(code, "v1", studyGuid)
                .addName(new Translation("en", "test activity"))
                .addSubtitle(new Translation("en", "subtitle of activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(question)))
                .addSection(new FormSectionDef(null, Collections.singletonList(conditionalBlock)))
                .setSnapshotSubstitutionsOnSubmit(true)
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(userId, "test"));
        return form;
    }

    private static FormActivityDef setupActivityWithCompositeQuestion(Handle handle, long userId, String studyGuid) {

        TextQuestionDef textQuestion1 = createBasicTextQuestionBuild("RequiredText")
                .build();
        LengthRuleDef lengthRuleWithAllowSaveTrue = new LengthRuleDef(new Template(TemplateType.TEXT, null, "You gave me more than 7 "
                + "characters!"), 0, 7);
        lengthRuleWithAllowSaveTrue.setAllowSave(true);
        TextQuestionDef textQuestion2 = createBasicTextQuestionBuild("Some chars please!")
                .addValidation(new LengthRuleDef(new Template(TemplateType.TEXT, null, "You did not give me 5 to 300"
                        + "characters!"), 5, 300))
                .addValidation(lengthRuleWithAllowSaveTrue)
                .build();

        QuestionDef compositeDef = createCompositeQuestionDef("This is the parent question", "Add Another One",
                "More data below", true, textQuestion1, textQuestion2);

        FormSectionDef compositeSection = new FormSectionDef(null, TestUtil.wrapQuestions(compositeDef));

        String code = "COMPOSITE_SAMP" + Instant.now().toEpochMilli();
        FormActivityDef compositeFormActivityDef = FormActivityDef.generalFormBuilder(code, "v1", studyGuid)
                .addName(new Translation("en", "activity " + code))
                .addSections(Arrays.asList(compositeSection))
                .build();
        activityVersionDto = handle.attach(ActivityDao.class).insertActivity(
                compositeFormActivityDef, RevisionMetadata.now(userId, "test")
        );
        assertNotNull(compositeFormActivityDef.getActivityId());
        return compositeFormActivityDef;

    }

    private static TextQuestionDef.Builder createBasicTextQuestionBuild(String promptText) {
        Template textPrompt = new Template(TemplateType.TEXT, null, promptText);
        String textStableId = UUID.randomUUID().toString();
        return TextQuestionDef.builder(TextInputType.TEXT, textStableId, textPrompt);
        //             .addValidation(new LengthRuleDef(null, 5, 300))
        //            .build();

    }

    private static QuestionDef createCompositeQuestionDef(String prompText, String addButtonText, String additionalItemText,
                                                          boolean allowMultiple, QuestionDef... childQuestions) {
        String compositeQuestionId = "COMP_" + Instant.now().toEpochMilli();

        Template addButtonTextTemplate = new Template(TemplateType.TEXT, null, addButtonText);
        Template additionalItemTemplate = new Template(TemplateType.TEXT, null, additionalItemText);
        Template promptTemplate = new Template(TemplateType.TEXT, null, prompText);
        return CompositeQuestionDef.builder()
                .setStableId(compositeQuestionId)
                .setPrompt(promptTemplate)
                .addChildrenQuestions(childQuestions)
                .setAllowMultiple(allowMultiple)
                .setAddButtonTemplate(addButtonTextTemplate)
                .setAdditionalItemTemplate(additionalItemTemplate)
                .build();
    }

    @Before
    public void setupBeforeEachTest() {
        //having some difficulty enabling/disabling  and creating Rules, so lets just do the setup each time per test.
        TransactionWrapper.useTxn(handle -> {
            compositeQuestionForm = setupActivityWithCompositeQuestion(handle, user.getUserId(), testData.getStudyGuid());
        });
    }

    @After
    public void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            testCleanupTasks.forEach((task) -> task.accept(handle));
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            for (String instanceGuid : instanceGuidsToDelete) {
                instanceDao.deleteByInstanceGuid(instanceGuid);
            }
            instanceGuidsToDelete.clear();

            JdbiWorkflowTransition jdbiTrans = handle.attach(JdbiWorkflowTransition.class);
            for (long transitionId : transitionIdsToDelete) {
                assertEquals(1, jdbiTrans.deleteById(transitionId));
            }
            transitionIdsToDelete.clear();
        });
    }

    @Test
    public void testActivityInstanceNotFound() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", "ABC123XYZ")
                .when().put(urlTemplate).then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                .body("message", containsString("activity instance with guid ABC123XYZ"));
    }

    @Test
    public void testActivityInstanceIsHidden() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            ActivityInstanceDto dto = insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
            assertEquals(1, handle.attach(ActivityInstanceDao.class)
                    .bulkUpdateIsHiddenByActivityIds(testData.getUserId(), true, Set.of(form.getActivityId())));
            return dto;
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                .body("message", containsString("is hidden"));
    }

    @Test
    public void testSetStatusToComplete_Success() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, form.getActivityId()));

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200);
    }

    @Test
    public void testTempUser_activityAccess_unauthorized() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            ActivityInstanceDto dto = insertNewInstanceAndDeferCleanup(handle, form.getActivityId());

            //set as temp user
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(user.getUserGuid()), Instant.now().toEpochMilli() + 10000);
            return dto;
        });

        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", instanceDto.getGuid())
                    .when().put(urlTemplate).then().assertThat()
                    .statusCode(401);
        } finally {
            //revert back temp user update
            TransactionWrapper.useTxn(handle -> {
                JdbiUser jdbiUser = handle.attach(JdbiUser.class);
                jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(user.getUserGuid()), null);
            });
        }
    }

    @Test
    public void testTempUser_activityAccess_authorized() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            ActivityInstanceDto dto = insertNewInstanceAndDeferCleanup(handle, form.getActivityId());

            //set as temp user and allow_unauthorized true
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(user.getUserGuid()), Instant.now().toEpochMilli() + 10000);
            handle.attach(JdbiActivity.class).updateAllowUnauthenticatedById(dto.getActivityId(), true);
            return dto;
        });

        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", instanceDto.getGuid())
                    .when().put(urlTemplate).then().assertThat()
                    .statusCode(200);
        } finally {
            //revert back temp user update
            TransactionWrapper.useTxn(handle -> {
                JdbiUser jdbiUser = handle.attach(JdbiUser.class);
                jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(user.getUserGuid()), null);
                handle.attach(JdbiActivity.class).updateAllowUnauthenticatedById(instanceDto.getActivityId(), false);
            });
        }
    }

    @Test
    public void testSetStatusToCompleteForActivityInstanceWithIncompleteAnswers_Failure() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            long questionId = ((QuestionBlockDef) form.getSections().get(0).getBlocks().get(0)).getQuestion().getQuestionId();
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), user.getUserId(), "make required");
            handle.attach(QuestionDao.class).addRequiredRule(questionId, new RequiredRuleDef(null), revId);
            return insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET));

        TransactionWrapper.useTxn(handle -> {
            long questionId = ((QuestionBlockDef) form.getSections().get(0).getBlocks().get(0)).getQuestion().getQuestionId();
            RevisionMetadata meta = RevisionMetadata.now(user.getUserId(), "remove required");
            handle.attach(QuestionDao.class).disableRequiredRule(questionId, meta);
        });
    }

    @Test
    public void testSetStatusToCompleteForActivityInstanceWithAllowSaveTrueFailedValidations_Failure() {
        AtomicReference<ActivityInstanceDto> instanceDtoRef = new AtomicReference<>();
        long ansId = TransactionWrapper.withTxn(handle -> {
            instanceDtoRef.set(insertNewInstanceAndDeferCleanup(handle, form.getActivityId()));
            String stableId = ((QuestionBlockDef) form.getSections().get(0).getBlocks().get(0)).getQuestion().getStableId();
            // answer should violate LengthRuleDef rule with min length of 2
            Answer ans = handle.attach(AnswerDao.class)
                    .createAnswer(user.getUserId(), instanceDtoRef.get().getId(),
                            new TextAnswer(null, stableId, null, "x"));
            assertNotNull(ans.getAnswerId());
            assertNotNull(ans.getAnswerGuid());
            return ans.getAnswerId();
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDtoRef.get().getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET));

        TransactionWrapper.useTxn(handle -> handle.attach(AnswerDao.class).deleteAnswer(ansId));
    }

    @Test
    public void testSetStatusToComplete_respectsNestedQuestions() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, form.getActivityId()));

        TransactionWrapper.useTxn(handle -> {
            long exprId = conditionalBlock.getNested().get(0).getShownExprId();
            assertEquals(1, handle.attach(JdbiExpression.class).updateById(exprId, "true"));
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET));

        long ansId = TransactionWrapper.withTxn(handle -> {
            String stableId = ((QuestionBlockDef) conditionalBlock.getNested().get(0)).getQuestion().getStableId();
            Answer ans = handle.attach(AnswerDao.class)
                    .createAnswer(user.getUserId(), instanceDto.getId(), new BoolAnswer(null, stableId, null, true));
            assertNotNull(ans.getAnswerId());
            assertNotNull(ans.getAnswerGuid());
            return ans.getAnswerId();
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200);

        TransactionWrapper.useTxn(handle -> {
            long exprId = conditionalBlock.getNested().get(0).getShownExprId();
            assertEquals(1, handle.attach(JdbiExpression.class).updateById(exprId, "false"));
            handle.attach(AnswerDao.class).deleteAnswer(ansId);
        });
    }

    @Test
    public void testWorkflowResponse_takesCurrentActivityAsFromState() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            FormActivityDef another = insertNewActivity(handle);
            ActivityState currentState = new ActivityState(form.getActivityId());
            ActivityState anotherState = new ActivityState(another.getActivityId());
            WorkflowTransition t1 = new WorkflowTransition(studyId, currentState, StaticState.done(), "true", 1);
            WorkflowTransition t2 = new WorkflowTransition(studyId, anotherState, StaticState.start(), "true", 1);
            insertTransitionsAndDeferCleanup(handle, t1, t2);
            return insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("workflow", is(notNullValue()))
                .body("workflow.next", equalTo(StateType.DONE.name()));
    }

    @Test
    public void testWorkflowResponse_unknownSuggestion_whenNoTransitions() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, form.getActivityId()));

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("workflow", is(notNullValue()))
                .body("workflow.next", equalTo("UNKNOWN"));
    }

    @Test
    public void testWorkflowResponse_unknownSuggestion_whenNoSatisfactoryTransitions() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            ActivityState currentState = new ActivityState(form.getActivityId());
            WorkflowTransition t1 = new WorkflowTransition(studyId, currentState, StaticState.done(), "false", 1);
            WorkflowTransition t2 = new WorkflowTransition(studyId, currentState, StaticState.start(), "true", 2);
            insertTransitionsAndDeferCleanup(handle, t1, t2);
            turnOffTransition(handle, t2);
            return insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("workflow", is(notNullValue()))
                .body("workflow.next", equalTo("UNKNOWN"));
    }

    @Test
    public void testWorkflowResponse_activityStateSuggestion_providesActivityCodeAndInstanceGuid() {
        FormActivityDef expectedActivity = TransactionWrapper.withTxn(handle -> {
            FormActivityDef another = insertNewActivity(handle);
            ActivityState currentState = new ActivityState(form.getActivityId());
            ActivityState anotherState = new ActivityState(another.getActivityId());
            WorkflowTransition t1 = new WorkflowTransition(studyId, currentState, anotherState, "true", 1);
            insertTransitionsAndDeferCleanup(handle, t1);
            return another;
        });
        String expectedActivityCode = expectedActivity.getActivityCode();

        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, form.getActivityId()));

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("workflow", is(notNullValue()))
                .body("workflow.next", equalTo(StateType.ACTIVITY.name()))
                .body("workflow.activityCode", equalTo(expectedActivityCode))
                .body("workflow.instanceGuid", is(notNullValue()));
    }

    @Test
    public void testCompositeQuestionNotRequiredAndEmpty() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            return insertNewInstanceAndDeferCleanup(handle, compositeQuestionForm.getActivityId());
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON);
    }

    @Test
    public void testCompositeQuestionRequiredAndEmpty() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            long questionId =
                    ((QuestionBlockDef) compositeQuestionForm.getSections().get(0).getBlocks().get(0)).getQuestion().getQuestionId();
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), user.getUserId(), "make required");
            handle.attach(QuestionDao.class).addRequiredRule(questionId, new RequiredRuleDef(null), revId);
            return insertNewInstanceAndDeferCleanup(handle, compositeQuestionForm.getActivityId());
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET));

    }

    @Test
    public void testCompositeQuestionWithAValidAnswer() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            CompositeQuestionDef questionDef =
                    (CompositeQuestionDef) ((QuestionBlockDef) compositeQuestionForm.getSections().get(0).getBlocks().get(0)).getQuestion();
            long questionId =
                    ((QuestionBlockDef) compositeQuestionForm.getSections().get(0).getBlocks().get(0)).getQuestion().getQuestionId();
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), user.getUserId(), "make required");
            handle.attach(QuestionDao.class).addRequiredRule(questionId, new RequiredRuleDef(null), revId);
            ActivityInstanceDto newInstanceDto = insertNewInstanceAndDeferCleanup(handle, compositeQuestionForm.getActivityId());
            CompositeAnswer parentCompAnswer = new CompositeAnswer(null, questionDef.getStableId(), null);
            List<Answer> childAnswers = questionDef.getChildren().stream().map((QuestionDef childQuestionDef) -> {
                if (childQuestionDef.getQuestionType() != QuestionType.TEXT) {
                    fail("I was expecting a text question here!");
                }
                return new TextAnswer(null, childQuestionDef.getStableId(), null, "Hello");

            }).collect(toList());
            parentCompAnswer.addRowOfChildAnswers(childAnswers);
            long id = handle.attach(AnswerDao.class).createAnswer(user.getUserId(), newInstanceDto.getId(), parentCompAnswer).getAnswerId();

            testCleanupTasks.add(cleanupHandle -> cleanupHandle.attach(AnswerDao.class).deleteAnswer(id));
            return newInstanceDto;
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when()
                .put(urlTemplate)
                .then()
                .assertThat()
                .statusCode(200).contentType(ContentType.JSON);
    }

    @Test
    public void testCompositeQuestionWithFailedAllowSaveTrueRule() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            CompositeQuestionDef questionDef =
                    (CompositeQuestionDef) ((QuestionBlockDef) compositeQuestionForm.getSections().get(0).getBlocks().get(0)).getQuestion();
            long questionId =
                    ((QuestionBlockDef) compositeQuestionForm.getSections().get(0).getBlocks().get(0)).getQuestion().getQuestionId();
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), user.getUserId(), "make required");
            handle.attach(QuestionDao.class).addRequiredRule(questionId, new RequiredRuleDef(null), revId);
            ActivityInstanceDto newInstanceDto = insertNewInstanceAndDeferCleanup(handle, compositeQuestionForm.getActivityId());
            CompositeAnswer parentCompAnswer = new CompositeAnswer(null, questionDef.getStableId(), null);
            List<Answer> childAnswers = questionDef.getChildren().stream().map((QuestionDef childQuestionDef) -> {
                if (childQuestionDef.getQuestionType() != QuestionType.TEXT) {
                    fail("I was expecting a text question here!");
                }
                // answer is longer than 7, which should violate our allowSave=true rule
                return new TextAnswer(null, childQuestionDef.getStableId(), null, "Hello, World!");

            }).collect(toList());
            parentCompAnswer.addRowOfChildAnswers(childAnswers);
            long id = handle.attach(AnswerDao.class).createAnswer(user.getUserId(), newInstanceDto.getId(), parentCompAnswer).getAnswerId();

            testCleanupTasks.add(cleanupHandle -> cleanupHandle.attach(AnswerDao.class).deleteAnswer(id));
            return newInstanceDto;
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when()
                .put(urlTemplate)
                .then()
                .assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET));
    }

    @Test
    public void testCompositeQuestionWithAMissingRequiredChildAnswer() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            CompositeQuestionDef questionDef =
                    (CompositeQuestionDef) ((QuestionBlockDef) compositeQuestionForm.getSections().get(0).getBlocks().get(0)).getQuestion();
            //making second question required
            QuestionDef childQuestionToMakeRequired = questionDef.getChildren().get(1);
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), user.getUserId(), "make required");
            handle.attach(QuestionDao.class).addRequiredRule(childQuestionToMakeRequired.getQuestionId(), new RequiredRuleDef(null), revId);

            ActivityInstanceDto newInstanceDto = insertNewInstanceAndDeferCleanup(handle, compositeQuestionForm.getActivityId());
            CompositeAnswer parentCompAnswer = new CompositeAnswer(null, questionDef.getStableId(), null);
            //Put an answer for the first, non-required one
            QuestionDef firstQuestionDef = questionDef.getChildren().get(0);
            TextAnswer firstAnswer = new TextAnswer(null, firstQuestionDef.getStableId(), null, "Hello");
            parentCompAnswer.addRowOfChildAnswers(firstAnswer);
            long id = handle.attach(AnswerDao.class).createAnswer(user.getUserId(), newInstanceDto.getId(), parentCompAnswer).getAnswerId();
            testCleanupTasks.add(cleanupHandle -> cleanupHandle.attach(AnswerDao.class).deleteAnswer(id));
            return newInstanceDto;
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET));

    }

    @Test
    public void testWorkflowResponse_activityStateSuggestion_providesInstanceGuidWhenAvailable() {
        FormActivityDef expectedActivity = TransactionWrapper.withTxn(handle -> {
            FormActivityDef another = insertNewActivity(handle);
            ActivityState currentState = new ActivityState(form.getActivityId());
            ActivityState anotherState = new ActivityState(another.getActivityId());
            WorkflowTransition t1 = new WorkflowTransition(studyId, currentState, anotherState, "true", 1);
            insertTransitionsAndDeferCleanup(handle, t1);
            return another;
        });
        ActivityInstanceDto expectedInstance = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, expectedActivity.getActivityId()));
        String expectedActivityCode = expectedActivity.getActivityCode();
        String expectedInstanceGuid = expectedInstance.getGuid();

        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, form.getActivityId()));

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("workflow", is(notNullValue()))
                .body("workflow.next", equalTo(StateType.ACTIVITY.name()))
                .body("workflow.activityCode", equalTo(expectedActivityCode))
                .body("workflow.instanceGuid", equalTo(expectedInstanceGuid));
    }

    @Test
    public void given_oneTrueExpr_whenRouteIsCalled_thenItReturnsOK() {
        try {
            ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
                handle.attach(JdbiActivity.class).insertValidation(
                        RouteTestUtil.createActivityValidationDto(
                                form, "false", "Should never fail", List.of(stableId)
                        ),
                        testData.getUserId(),
                        testData.getStudyId(),
                        activityVersionDto.getRevId()
                );
                return insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
            });
            callEndpoint(instanceDto.getGuid())
                    .then()
                    .assertThat()
                    .statusCode(200).contentType(ContentType.JSON);
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(JdbiActivity.class).deleteValidationsByCode(form.getActivityId());
            });
        }
    }

    @Test
    public void given_oneTrueExpr_whenRouteIsCalled_thenItReturns422_AndRespContainsCorrectErrorCode() {
        try {
            ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
                handle.attach(JdbiActivity.class).insertValidation(
                        RouteTestUtil.createActivityValidationDto(
                                form, "true", "Should always fail", List.of(stableId)
                        ),
                        testData.getUserId(),
                        testData.getStudyId(),
                        activityVersionDto.getRevId()
                );
                return insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
            });
            callEndpoint(instanceDto.getGuid())
                    .then()
                    .assertThat()
                    .statusCode(422).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.ACTIVITY_VALIDATION));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(JdbiActivity.class).deleteValidationsByCode(form.getActivityId());
            });
        }
    }

    @Test
    public void testSnapshotSubstitutions() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, form.getActivityId()));

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200);

        TransactionWrapper.useTxn(handle -> {
            Map<String, String> subs = handle.attach(ActivityInstanceDao.class).findSubstitutions(instanceDto.getId());
            assertNotNull(subs);
            assertFalse(subs.isEmpty());
            assertTrue(subs.containsKey(I18nTemplateConstants.Snapshot.DATE));
            assertTrue(subs.containsKey(I18nTemplateConstants.Snapshot.PARTICIPANT_FIRST_NAME));
            assertTrue(subs.containsKey(I18nTemplateConstants.Snapshot.PARTICIPANT_LAST_NAME));
        });
    }

    private io.restassured.response.Response callEndpoint(String instanceGuid) {
        return given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceGuid)
                .when()
                .put(urlTemplate);
    }

    private FormActivityDef insertNewActivity(Handle handle) {
        String code = "PUT_ACT_" + Instant.now().toEpochMilli();
        FormActivityDef form = FormActivityDef.generalFormBuilder(code, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "dummy activity " + code))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add " + code));
        assertNotNull(form.getActivityId());
        return form;
    }

    private ActivityInstanceDto insertNewInstanceAndDeferCleanup(Handle handle, long activityId) {
        ActivityInstanceDto dto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityId, user.getUserGuid());
        instanceGuidsToDelete.add(dto.getGuid());
        return dto;
    }

    private void insertTransitionsAndDeferCleanup(Handle handle, WorkflowTransition... transitions) {
        handle.attach(WorkflowDao.class).insertTransitions(Arrays.asList(transitions));
        Arrays.stream(transitions).forEach(trans -> {
            assertNotNull(trans.getId());
            transitionIdsToDelete.add(trans.getId());
        });
    }

    private void turnOffTransition(Handle handle, WorkflowTransition transition) {
        assertEquals(1, handle.attach(JdbiWorkflowTransition.class).updateIsActiveById(transition.getId(), false));
    }
}
