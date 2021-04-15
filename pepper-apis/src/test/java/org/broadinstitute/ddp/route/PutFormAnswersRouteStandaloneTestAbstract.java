package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiWorkflowTransition;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.NestedActivityRenderHint;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public class PutFormAnswersRouteStandaloneTestAbstract extends IntegrationTestSuite.TestCaseWithCacheEnabled {

    protected static ActivityVersionDto activityVersionDto;
    protected static TestDataSetupUtil.GeneratedTestData testData;
    protected static Auth0Util.TestingUser user;
    protected static FormActivityDef parentForm;
    protected static FormActivityDef form;
    protected static ConditionalBlockDef conditionalBlock;
    protected static long studyId;
    protected static String stableId;
    protected static String token;
    protected static String urlTemplate;
    protected static FormActivityDef compositeQuestionForm;
    protected static ActivityInstanceDto parentInstanceDto;
    protected static ActivityInstanceDto nestedToggleableInstanceDto;
    protected static NestedActivityBlockDef nestedToggleableBlock;

    protected List<String> instanceGuidsToDelete = new ArrayList<>();
    protected List<Long> transitionIdsToDelete = new ArrayList<>();
    //allow tests to add something unique to cleanup
    protected List<Consumer<Handle>> testCleanupTasks = new ArrayList<>();

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

    protected static FormActivityDef setupActivity(Handle handle, long userId, String studyGuid, String stableId) {

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

        String parentActCode = "PUT_STATUS_PARENT" + Instant.now().toEpochMilli();
        String nestedActCode = "PUT_STATUS_ACT" + Instant.now().toEpochMilli();
        String nestedToggleableActCode = nestedActCode + "_hidden";

        FormActivityDef form = FormActivityDef.generalFormBuilder(nestedActCode, "v1", studyGuid)
                .addName(new Translation("en", "test activity"))
                .setParentActivityCode(parentActCode)
                .addSubtitle(new Translation("en", "subtitle of activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(question)))
                .addSection(new FormSectionDef(null, Collections.singletonList(conditionalBlock)))
                .setSnapshotSubstitutionsOnSubmit(true)
                .build();
        FormActivityDef toggleable = FormActivityDef.generalFormBuilder(nestedToggleableActCode, "v1", studyGuid)
                .addName(new Translation("en", "another nested activity that can be hidden and has required question"))
                .setParentActivityCode(parentActCode)
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(TextQuestionDef
                        .builder(TextInputType.TEXT, nestedToggleableActCode + "_q", Template.text("required"))
                        .addValidations(List.of(new RequiredRuleDef(null)))
                        .build())))
                .build();

        nestedToggleableBlock = new NestedActivityBlockDef(nestedToggleableActCode, NestedActivityRenderHint.EMBEDDED, true, null);
        nestedToggleableBlock.setShownExpr("true"); // start with shown, toggle later in tests
        parentForm = FormActivityDef.generalFormBuilder(parentActCode, "v1", studyGuid)
                .addName(new Translation("en", "parent test activity"))
                .addSection(new FormSectionDef(null, List.of(
                        new NestedActivityBlockDef(nestedActCode, NestedActivityRenderHint.EMBEDDED, true, null),
                        nestedToggleableBlock)))
                .build();

        handle.attach(ActivityDao.class).insertActivity(parentForm, List.of(form, toggleable), RevisionMetadata.now(userId, "test"));
        parentInstanceDto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(parentForm.getActivityId(), user.getUserGuid());
        nestedToggleableInstanceDto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(toggleable.getActivityId(), user.getUserGuid(), user.getUserGuid(), parentInstanceDto.getId());
        return form;
    }

    protected static FormActivityDef setupActivityWithCompositeQuestion(Handle handle, long userId, String studyGuid) {

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

    protected static TextQuestionDef.Builder createBasicTextQuestionBuild(String promptText) {
        Template textPrompt = new Template(TemplateType.TEXT, null, promptText);
        String textStableId = UUID.randomUUID().toString();
        return TextQuestionDef.builder(TextInputType.TEXT, textStableId, textPrompt);
        //             .addValidation(new LengthRuleDef(null, 5, 300))
        //            .build();

    }

    protected static QuestionDef createCompositeQuestionDef(String prompText, String addButtonText, String additionalItemText,
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
            JdbiActivityInstance jdbiInstance = handle.attach(JdbiActivityInstance.class);
            AnswerDao answerDao = handle.attach(AnswerDao.class);
            for (String instanceGuid : instanceGuidsToDelete) {
                long instanceId = jdbiInstance.getActivityInstanceId(instanceGuid);
                answerDao.deleteAllByInstanceIds(Set.of(instanceId));
                instanceDao.deleteByInstanceGuid(instanceGuid);
            }
            instanceGuidsToDelete.clear();

            JdbiWorkflowTransition jdbiTrans = handle.attach(JdbiWorkflowTransition.class);
            for (long transitionId : transitionIdsToDelete) {
                assertEquals(1, jdbiTrans.deleteById(transitionId));
            }
            transitionIdsToDelete.clear();
        });
        ActivityDefStore.getInstance().clear();
    }

    protected io.restassured.response.Response callEndpoint(String instanceGuid) {
        return given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceGuid)
                .when()
                .put(urlTemplate);
    }

    protected FormActivityDef insertNewActivity(Handle handle) {
        String code = "PUT_ACT_" + Instant.now().toEpochMilli();
        FormActivityDef form = FormActivityDef.generalFormBuilder(code, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "dummy activity " + code))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add " + code));
        assertNotNull(form.getActivityId());
        return form;
    }

    protected ActivityInstanceDto insertNewInstanceAndDeferCleanup(Handle handle, long activityId) {
        ActivityInstanceDto dto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityId, user.getUserGuid(), user.getUserGuid(), parentInstanceDto.getId());
        instanceGuidsToDelete.add(dto.getGuid());
        return dto;
    }

    protected void insertTransitionsAndDeferCleanup(Handle handle, WorkflowTransition... transitions) {
        handle.attach(WorkflowDao.class).insertTransitions(Arrays.asList(transitions));
        Arrays.stream(transitions).forEach(trans -> {
            assertNotNull(trans.getId());
            transitionIdsToDelete.add(trans.getId());
        });
    }

    protected void turnOffTransition(Handle handle, WorkflowTransition transition) {
        assertEquals(1, handle.attach(JdbiWorkflowTransition.class).updateIsActiveById(transition.getId(), false));
    }
}
