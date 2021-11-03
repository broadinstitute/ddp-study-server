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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.ComponentBlock;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.instance.MailingAddressComponent;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.NestedActivityRenderHint;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.jdbi.v3.core.Handle;
import org.junit.Test;
import spark.HaltException;

public class PutFormAnswersRouteStandaloneTest extends PutFormAnswersRouteStandaloneTestAbstract {

    @Test
    public void testParentActivity_parentReadOnlyPreventsChildPut() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            assertEquals(1, handle.attach(JdbiActivityInstance.class)
                    .updateIsReadonlyByGuid(true, parentInstanceDto.getGuid()));
            return insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
        });
        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", instanceDto.getGuid())
                    .when().put(urlTemplate).then().assertThat()
                    .statusCode(422).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY))
                    .body("message", containsString("Parent activity instance"))
                    .body("message", containsString("read-only"));
        } finally {
            TransactionWrapper.useTxn(handle -> assertEquals(1, handle.attach(JdbiActivityInstance.class)
                    .updateIsReadonlyByGuid(null, parentInstanceDto.getGuid())));
        }
    }

    @Test
    public void testParentActivity_parentPutChecksChildComplete() {
        // Nested toggleable child activity starts off as shown,
        // so there's a required question not answered yet.
        given().auth().oauth2(token)
                .pathParam("instanceGuid", parentInstanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET))
                .body("message", containsString("child instance"));

        // Now hide the nested activity.
        TransactionWrapper.useTxn(handle -> {
            long exprId = nestedToggleableBlock.getShownExprId();
            assertEquals(1, handle.attach(JdbiExpression.class).updateById(exprId, "false"));
        });

        ActivityDefStore.getInstance().clear();

        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", parentInstanceDto.getGuid())
                    .when().put(urlTemplate).then().assertThat()
                    .statusCode(200);
        } finally {
            TransactionWrapper.useTxn(handle -> {
                long exprId = nestedToggleableBlock.getShownExprId();
                assertEquals(1, handle.attach(JdbiExpression.class).updateById(exprId, "true"));
            });
        }
    }

    @Test
    public void testParentActivity_childPutUpdatesParentStatusToInProgress() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            var instanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
            instanceStatusDao.deleteAllByInstanceGuid(parentInstanceDto.getGuid());
            instanceStatusDao.insertStatus(parentInstanceDto.getGuid(), InstanceStatusType.CREATED,
                    Instant.now().toEpochMilli(), testData.getUserGuid());
            return insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200);

        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDto actualParentInstanceDto = handle.attach(JdbiActivityInstance.class)
                    .getByActivityInstanceGuid(parentInstanceDto.getGuid()).get();
            assertEquals(InstanceStatusType.IN_PROGRESS, actualParentInstanceDto.getStatusType());
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

        ActivityDefStore.getInstance().clearCachedActivityData();

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
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli() - 1000,
                    user.getUserId(), "make required");
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
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli() - 1000, user.getUserId(),
                    "make required");
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

        ActivityDefStore.getInstance().clearCachedActivityData();

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
            CacheService.getInstance().resetAllCaches();
            ActivityDefStore.getInstance().clearCachedActivityValidationDtos(form.getActivityId());
            callEndpoint(instanceDto.getGuid())
                    .then()
                    .assertThat()
                    .statusCode(422).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.ACTIVITY_VALIDATION));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(JdbiActivity.class).deleteValidationsByCode(form.getActivityId());
            });
            CacheService.getInstance().resetAllCaches();
            ActivityDefStore.getInstance().clearCachedActivityValidationDtos(form.getActivityId());
        }
    }

    @Test
    public void testCheckAddressRequirements_noRequirements() {
        var mockHandle = mock(Handle.class);
        var form = new FormInstance(1L, 1L, 1L, "", FormType.GENERAL, "", "", "", "CREATED",
                null, null, null, null, null, 1L, null, null, null, false, false, false, false, 0);
        form.addBodySections(List.of(new FormSection(List.of(
                new ComponentBlock(new MailingAddressComponent(1L, 1L, false, false, false))))));
        new PutFormAnswersRoute(null, null, null, null, null)
                .checkAddressRequirements(mockHandle, "", form);
        // all good!
    }

    @Test
    public void testCheckAddressRequirements_requireVerified() {
        var mockHandle = mock(Handle.class);
        var mockAddrDao = mock(JdbiMailAddress.class);
        doReturn(mockAddrDao).when(mockHandle).attach(JdbiMailAddress.class);

        var form = new FormInstance(1L, 1L, 1L, "", FormType.GENERAL, "", "", "", "CREATED",
                null, null, null, null, null, 1L, null, null, null, false, false, false, false, 0);
        form.addBodySections(List.of(new FormSection(List.of(
                new ComponentBlock(new MailingAddressComponent(1L, 1L, false, true, false))))));
        var route = new PutFormAnswersRoute(null, null, null, null, null);

        var addr = new MailAddress("", "", "", "", "", "", "", "", "", "",
                DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS, true);
        doReturn(Optional.of(addr)).when(mockAddrDao).findDefaultAddressForParticipant(anyString());

        try {
            route.checkAddressRequirements(mockHandle, "", form);
            fail("expected exception not thrown");
        } catch (HaltException halt) {
            assertEquals(422, halt.statusCode());
            assertTrue(halt.body().contains("verified"));
        }

        addr = new MailAddress("", "", "", "", "", "", "", "", "", "",
                DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS, true);
        doReturn(Optional.of(addr)).when(mockAddrDao).findDefaultAddressForParticipant(anyString());

        route.checkAddressRequirements(mockHandle, "", form);
        // Should not have thrown! Not catching it so it gets handled by junit itself.
    }

    @Test
    public void testCheckAddressRequirements_requirePhone() {
        var mockHandle = mock(Handle.class);
        var mockAddrDao = mock(JdbiMailAddress.class);
        doReturn(mockAddrDao).when(mockHandle).attach(JdbiMailAddress.class);

        var form = new FormInstance(1L, 1L, 1L, "", FormType.GENERAL, "", "", "", "CREATED",
                null, null, null, null, null, 1L, null, null, null, false, false, false, false, 0);
        form.addBodySections(List.of(new FormSection(List.of(
                new ComponentBlock(new MailingAddressComponent(1L, 1L, false, true, true))))));
        var route = new PutFormAnswersRoute(null, null, null, null, null);

        var addr = new MailAddress("", "", "", "", "", "", "", "", "", "",
                DsmAddressValidationStatus.DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS, true);
        doReturn(Optional.of(addr)).when(mockAddrDao).findDefaultAddressForParticipant(anyString());

        try {
            route.checkAddressRequirements(mockHandle, "", form);
            fail("expected exception not thrown");
        } catch (HaltException halt) {
            assertEquals(422, halt.statusCode());
            assertTrue(halt.body().contains("phone"));
        }

        addr = new MailAddress("", "", "", "", "", "", "", "111-222-3333", "", "",
                DsmAddressValidationStatus.DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS, true);
        doReturn(Optional.of(addr)).when(mockAddrDao).findDefaultAddressForParticipant(anyString());

        route.checkAddressRequirements(mockHandle, "", form);
        // Should not have thrown! Not catching it so it gets handled by junit itself.
    }

    @Test
    public void testHiddenAnswersAreDeleted() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, form.getActivityId()));

        // Answer the hidden question.
        Answer answer = TransactionWrapper.withTxn(handle -> {
            var question = ((QuestionBlockDef) conditionalBlock.getNested().get(0)).getQuestion();
            return handle.attach(AnswerDao.class).createAnswer(
                    testData.getUserId(),
                    instanceDto.getId(),
                    question.getQuestionId(),
                    new BoolAnswer(null, question.getStableId(), null, true));
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200);

        TransactionWrapper.useTxn(handle -> {
            var result = handle.attach(AnswerDao.class).findAnswerById(answer.getAnswerId());
            assertTrue("hidden answer should have been deleted", result.isEmpty());
        });
    }

    @Test
    public void testHiddenChildInstancesAreDeleted() {
        // Setup data
        var parentActCode = "PARENT" + Instant.now().toEpochMilli();
        var childActCode = "CHILD" + Instant.now().toEpochMilli();
        var nestedActBlockDef = new NestedActivityBlockDef(
                childActCode, NestedActivityRenderHint.EMBEDDED, true, Template.text("add button"));
        nestedActBlockDef.setShownExpr("false");    // Make child instances hidden.

        var question = TextQuestionDef
                .builder(TextInputType.TEXT, childActCode + "Q1", Template.text("q1"))
                .build();
        var parentAct = FormActivityDef.generalFormBuilder(parentActCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "parent"))
                .addSection(new FormSectionDef(null, List.of(nestedActBlockDef)))
                .build();
        var childAct = FormActivityDef.generalFormBuilder(childActCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "child"))
                .setParentActivityCode(parentActCode)
                .setCanDeleteInstances(true)
                .setCanDeleteFirstInstance(false)
                .addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(question))))
                .build();

        var parentInstanceDto = TransactionWrapper.withTxn(handle -> {
            handle.attach(ActivityDao.class).insertActivity(
                    parentAct, List.of(childAct), RevisionMetadata.now(testData.getUserId(), "test"));
            var instanceDao = handle.attach(ActivityInstanceDao.class);
            var parentInstance = instanceDao.insertInstance(parentAct.getActivityId(), testData.getUserGuid());

            var answerDao = handle.attach(AnswerDao.class);
            var childInstance1 = instanceDao.insertInstance(childAct.getActivityId(), testData.getUserGuid(),
                    testData.getUserGuid(), parentInstance.getId());
            answerDao.createAnswer(testData.getUserId(), childInstance1.getId(),
                    new TextAnswer(null, question.getStableId(), null, "child1"));

            var childInstance2 = instanceDao.insertInstance(childAct.getActivityId(), testData.getUserGuid(),
                    testData.getUserGuid(), parentInstance.getId());
            answerDao.createAnswer(testData.getUserId(), childInstance2.getId(),
                    new TextAnswer(null, question.getStableId(), null, "child2"));

            return parentInstance;
        });

        // Run test
        given().auth().oauth2(token)
                .pathParam("instanceGuid", parentInstanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(200);

        // Verify instances
        TransactionWrapper.useTxn(handle -> {
            var instanceDao = handle.attach(ActivityInstanceDao.class);
            var instances = instanceDao.findFormResponsesSubsetWithAnswersByUserId(
                    testData.getUserId(), Set.of(childAct.getActivityId()))
                    .collect(Collectors.toList());
            assertEquals("first instance should not be deleted", 1, instances.size());
            assertTrue("first instance answers should be cleared", instances.get(0).getAnswers().isEmpty());
        });
    }

    @Test
    public void testStudyAdmin_readOnlyInstance() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            ActivityInstanceDto dto = insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
            assertEquals(1, handle.attach(ActivityInstanceDao.class)
                    .bulkUpdateReadOnlyByActivityIds(testData.getUserId(), true, Set.of(form.getActivityId())));
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
            return dto;
        });
        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", instanceDto.getGuid())
                    .when().put(urlTemplate).then().assertThat()
                    .log().all()
                    .statusCode(200);
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
            });
        }
    }
}
