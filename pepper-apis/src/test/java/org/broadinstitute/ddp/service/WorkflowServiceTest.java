package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.auth0.exception.Auth0Exception;
import com.typesafe.config.Config;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiWorkflowTransition;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.workflow.WorkflowActivityResponse;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class WorkflowServiceTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData data;
    private static String operatorGuid;
    private static String userGuid;
    private static String studyGuid;
    private static long studyId;

    private WorkflowService service;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        Config cfg = ConfigManager.getInstance().getConfig();
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);

        String sendgridApiKey = cfg.getString(ConfigFile.SENDGRID_API_KEY);
        String backendTestAuth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
        String backendTestSecret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
        String backendTestClientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
        String auth0domain = auth0Config.getString(ConfigFile.DOMAIN);
        String mgmtClientId = auth0Config.getString(Auth0Testing.AUTH0_MGMT_API_CLIENT_ID);
        String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
        String encryptionSecret = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);

        TransactionWrapper.useTxn(handle -> {

            data = TestDataSetupUtil.generateBasicUserTestData(handle, true, auth0domain,
                    backendTestClientName,
                    backendTestAuth0ClientId,
                    backendTestSecret,
                    encryptionSecret,
                    mgmtClientId,
                    mgmtSecret,
                    sendgridApiKey);
            userGuid = data.getUserGuid();
            operatorGuid = data.getUserGuid();
            studyGuid = data.getStudyGuid();
            studyId = data.getStudyId();
        });
    }

    @Before
    public void refresh() {
        service = new WorkflowService(new TreeWalkInterpreter());
    }

    @AfterClass
    public static void removeUser() throws Auth0Exception {
        TestDataSetupUtil.deleteGeneratedTestData();
    }

    @Test
    public void testSuggestNextState_noTransitions() {
        TransactionWrapper.useTxn(handle -> {
            Optional<WorkflowState> actual = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, StaticState.start());
            assertNotNull(actual);
            assertFalse(actual.isPresent());
        });
    }

    @Test
    public void testSuggestNextState_gracefullyCaptureUnusedStateAsNoTransitions() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            ActivityState actState = new ActivityState(form.getActivityId());

            WorkflowTransition t1 = new WorkflowTransition(studyId, StaticState.start(), actState, "true", 100);
            insertTransitions(handle, t1);

            Optional<WorkflowState> actual = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, StaticState.done());
            assertNotNull(actual);
            assertFalse(actual.isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testSuggestNextState_ignoreInactive() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            ActivityState actState = new ActivityState(form.getActivityId());

            WorkflowTransition t1 = new WorkflowTransition(studyId, StaticState.start(), StaticState.done(), "true", 1);
            WorkflowTransition t2 = new WorkflowTransition(studyId, StaticState.start(), actState, "true", 100);
            insertTransitions(handle, t1, t2);
            turnOffTransition(handle, t1);

            Optional<WorkflowState> actual = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, StaticState.start());
            assertNotNull(actual);
            assertTrue(actual.isPresent());
            assertTrue(actState.matches(actual.get()));

            handle.rollback();
        });
    }

    @Test
    public void testSuggestNextState_ignoreUnmetPrecondition() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            ActivityState actState = new ActivityState(form.getActivityId());

            WorkflowTransition t1 = new WorkflowTransition(studyId, StaticState.start(), StaticState.done(), "false", 1);
            WorkflowTransition t2 = new WorkflowTransition(studyId, StaticState.start(), actState, "true", 100);
            insertTransitions(handle, t1, t2);

            Optional<WorkflowState> actual = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, StaticState.start());
            assertNotNull(actual);
            assertTrue(actual.isPresent());
            assertTrue(actState.matches(actual.get()));

            handle.rollback();
        });
    }

    @Test
    public void testSuggestNextState_gracefullyCapturePexExceptionsAsFailedPrecondition() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            ActivityState actState = new ActivityState(form.getActivityId());

            String expr = "totally not supported expression";
            WorkflowTransition t1 = new WorkflowTransition(studyId, StaticState.start(), StaticState.done(), expr, 1);
            WorkflowTransition t2 = new WorkflowTransition(studyId, StaticState.start(), actState, "true", 100);
            insertTransitions(handle, t1, t2);

            Optional<WorkflowState> actual = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, StaticState.start());
            assertNotNull(actual);
            assertTrue(actual.isPresent());
            assertTrue(actState.matches(actual.get()));

            handle.rollback();
        });
    }

    @Test
    public void testSuggestNextState_evaluateTransitionsInOrder() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            ActivityState actState = new ActivityState(form.getActivityId());

            WorkflowTransition t1 = new WorkflowTransition(studyId, StaticState.start(), StaticState.done(), "true", 100);
            WorkflowTransition t2 = new WorkflowTransition(studyId, StaticState.start(), actState, "true", 1);
            insertTransitions(handle, t1, t2);

            Optional<WorkflowState> actual = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, StaticState.start());
            assertNotNull(actual);
            assertTrue(actual.isPresent());
            assertTrue(actState.matches(actual.get()));

            handle.rollback();
        });
    }

    @Test
    public void testSuggestNextState_simpleLinearTransitions() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            ActivityState actState = new ActivityState(form.getActivityId());

            // start --t1-> activity --t2-> done
            WorkflowTransition t1 = new WorkflowTransition(studyId, StaticState.start(), actState, "true", 1);
            WorkflowTransition t2 = new WorkflowTransition(studyId, actState, StaticState.done(), "true", 1);
            insertTransitions(handle, t1, t2);

            WorkflowState from = StaticState.start();
            Optional<WorkflowState> actual = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, from);
            assertNotNull(actual);
            assertTrue(actual.isPresent());
            assertTrue(actState.matches(actual.get()));

            from = actState;
            actual = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, from);
            assertNotNull(actual);
            assertTrue(actual.isPresent());
            assertTrue(StaticState.done().matches(actual.get()));

            from = StaticState.done();
            actual = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, from);
            assertNotNull(actual);
            assertFalse(actual.isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testBuildStateResponse_noState() {
        TransactionWrapper.useTxn(handle -> {
            WorkflowResponse resp = service.buildStateResponse(handle, userGuid, null);
            assertNotNull(resp);
            assertEquals("UNKNOWN", resp.getNext());
        });
    }

    @Test
    public void testBuildStateResponse_noActivity() {
        thrown.expect(NoSuchElementException.class);
        thrown.expectMessage("activityId=9999");
        TransactionWrapper.useTxn(handle -> service.buildStateResponse(handle, userGuid, new ActivityState(9999L)));
    }

    @Test
    public void testBuildStateResponse_staticState() {
        TransactionWrapper.useTxn(handle -> {
            WorkflowResponse resp = service.buildStateResponse(handle, userGuid, StaticState.start());
            assertNotNull(resp);
            assertEquals(StateType.START.name(), resp.getNext());

            resp = service.buildStateResponse(handle, userGuid, StaticState.done());
            assertNotNull(resp);
            assertEquals(StateType.DONE.name(), resp.getNext());
        });
    }

    @Test
    public void testBuildStateResponse_activityState() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            WorkflowState actState = new ActivityState(form.getActivityId());
            WorkflowResponse resp = service.buildStateResponse(handle, userGuid, actState);

            assertNotNull(resp);
            assertEquals(StateType.ACTIVITY.name(), resp.getNext());
            assertEquals(form.getActivityCode(), ((WorkflowActivityResponse) resp).getActivityCode());
            assertNull(((WorkflowActivityResponse) resp).getInstanceGuid());

            handle.rollback();
        });
    }

    @Test
    public void testBuildStateResponse_activityState_noInstanceForGivenUserWhileOtherUsersHaveInstances() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            insertNewInstance(handle, form.getActivityId());

            String otherUserGuid = TestConstants.TEST_USER_GUID;
            WorkflowState actState = new ActivityState(form.getActivityId());
            WorkflowResponse resp = service.buildStateResponse(handle, otherUserGuid, actState);

            assertNotNull(resp);
            assertEquals(StateType.ACTIVITY.name(), resp.getNext());
            assertEquals(form.getActivityCode(), ((WorkflowActivityResponse) resp).getActivityCode());
            assertNull(((WorkflowActivityResponse) resp).getInstanceGuid());

            handle.rollback();
        });
    }

    @Test
    public void testBuildStateResponse_activityState_providesInstanceGuidWhenAvailable() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            ActivityInstanceDto instanceDto = insertNewInstance(handle, form.getActivityId());

            WorkflowState actState = new ActivityState(form.getActivityId());
            WorkflowResponse resp = service.buildStateResponse(handle, userGuid, actState);

            assertNotNull(resp);
            assertEquals(StateType.ACTIVITY.name(), resp.getNext());
            assertEquals(form.getActivityCode(), ((WorkflowActivityResponse) resp).getActivityCode());
            assertEquals(instanceDto.getGuid(), ((WorkflowActivityResponse) resp).getInstanceGuid());

            handle.rollback();
        });
    }

    @Test
    public void testBuildStateResponse_activityState_providesMostRecentlyCreatedInstance() throws InterruptedException {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle);
            ActivityInstanceDto oldInstance = insertNewInstance(handle, form.getActivityId());
            TimeUnit.SECONDS.sleep(1);
            ActivityInstanceDto newInstance = insertNewInstance(handle, form.getActivityId());

            assertNotEquals(newInstance.getGuid(), oldInstance.getGuid());
            assertTrue(newInstance.getCreatedAtMillis() > oldInstance.getCreatedAtMillis());

            WorkflowState actState = new ActivityState(form.getActivityId());
            WorkflowResponse resp = service.buildStateResponse(handle, userGuid, actState);

            assertNotNull(resp);
            assertEquals(StateType.ACTIVITY.name(), resp.getNext());
            assertEquals(form.getActivityCode(), ((WorkflowActivityResponse) resp).getActivityCode());
            assertEquals(newInstance.getGuid(), ((WorkflowActivityResponse) resp).getInstanceGuid());

            handle.rollback();
        });
    }

    @Test
    public void testBuildStateResponse_activityState_providesActivityMetadata() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form = insertNewActivity(handle, builder -> builder.setAllowUnauthenticated(true));
            WorkflowState actState = new ActivityState(form.getActivityId());
            WorkflowResponse resp = service.buildStateResponse(handle, userGuid, actState);

            assertNotNull(resp);
            WorkflowActivityResponse activityResponse = (WorkflowActivityResponse) resp;

            assertEquals(StateType.ACTIVITY.name(), activityResponse.getNext());
            assertEquals(form.getActivityCode(), activityResponse.getActivityCode());
            assertTrue(activityResponse.isAllowUnauthenticated());

            handle.rollback();
        });
    }

    @Test
    public void test_givenNullMaxInstPerUser_andNextActivityDoesntHaveInst_whenSuggestNextStateCalled_thenInstIsCreated() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form1 = insertNewActivity(handle);
            FormActivityDef form2 = insertNewActivity(handle);
            handle.attach(JdbiActivity.class).updateMaxInstancesPerUserById(form2.getActivityId(), null);

            ActivityState actState1 = new ActivityState(form1.getActivityId());
            ActivityState actState2 = new ActivityState(form2.getActivityId());

            WorkflowTransition t1 = new WorkflowTransition(studyId, actState1, actState2, "true", 1);
            insertTransitions(handle, t1);

            Optional<WorkflowState> nextState = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, actState1);
            assertTrue(nextState.isPresent());
            WorkflowResponse workflowResponse = service.buildStateResponse(handle, userGuid, nextState.get());

            assertNotNull(workflowResponse);

            String createdInstanceGuid = ((WorkflowActivityResponse)workflowResponse).getInstanceGuid();
            Optional<String> latestInstanceGuid = handle.attach(JdbiActivityInstance.class)
                    .findLatestInstanceGuidByUserGuidAndActivityId(userGuid, form2.getActivityId());
            assertTrue(latestInstanceGuid.isPresent());
            assertEquals(createdInstanceGuid, latestInstanceGuid.get());

            handle.rollback();
        });
    }

    @Test
    public void test_givenZeroMaxInstPerUser_andNextActivityDoesntHaveInst_whenSuggestNextStateCalled_thenInstIsntCreated() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form1 = insertNewActivity(handle);
            FormActivityDef form2 = insertNewActivity(handle);
            handle.attach(JdbiActivity.class).updateMaxInstancesPerUserById(form2.getActivityId(), 0);

            ActivityState actState1 = new ActivityState(form1.getActivityId());
            ActivityState actState2 = new ActivityState(form2.getActivityId());

            WorkflowTransition t1 = new WorkflowTransition(studyId, actState1, actState2, "true", 1);
            insertTransitions(handle, t1);

            Optional<WorkflowState> nextState = service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, actState1);
            assertTrue(nextState.isPresent());
            WorkflowResponse workflowResponse = service.buildStateResponse(handle, userGuid, nextState.get());

            assertNotNull(workflowResponse);

            String createdInstanceGuid = ((WorkflowActivityResponse)workflowResponse).getInstanceGuid();
            assertNull(createdInstanceGuid);
            Optional<String> latestInstanceGuid = handle.attach(JdbiActivityInstance.class)
                    .findLatestInstanceGuidByUserGuidAndActivityId(userGuid, form2.getActivityId());
            assertFalse(latestInstanceGuid.isPresent());

            handle.rollback();
        });
    }

    @Test
    public void test_givenNextActivityAlreadyHasInstance_whenSuggestNextStateCalled_thenInstanceIsntCreated() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef form1 = insertNewActivity(handle);
            FormActivityDef form2 = insertNewActivity(handle);
            ActivityInstanceDto newInstance = insertNewInstance(handle, form2.getActivityId());

            ActivityState actState1 = new ActivityState(form1.getActivityId());
            ActivityState actState2 = new ActivityState(form2.getActivityId());

            WorkflowTransition t1 = new WorkflowTransition(studyId, actState1, actState2, "true", 1);
            insertTransitions(handle, t1);
            service.suggestNextState(handle, operatorGuid, userGuid, studyGuid, actState1);
            Optional<String> latestInstanceGuid = handle.attach(JdbiActivityInstance.class)
                    .findLatestInstanceGuidByUserGuidAndActivityId(userGuid, form2.getActivityId());
            assertTrue(latestInstanceGuid.isPresent());
            assertEquals(newInstance.getGuid(), latestInstanceGuid.get());

            handle.rollback();
        });
    }

    private FormActivityDef insertNewActivity(Handle handle) {
        return insertNewActivity(handle, null);
    }

    private FormActivityDef insertNewActivity(Handle handle, Consumer<FormActivityDef.FormBuilder> customizer) {
        String code = "WORKFLOW_ACT_" + Instant.now().toEpochMilli();
        FormActivityDef.FormBuilder builder = FormActivityDef.generalFormBuilder(code, "v1", data.getStudyGuid())
                .addName(new Translation("en", "dummy activity " + code));
        if (customizer != null) {
            customizer.accept(builder);
        }
        FormActivityDef form = builder.build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(data.getUserId(), "add " + code));
        assertNotNull(form.getActivityId());
        return form;
    }

    private ActivityInstanceDto insertNewInstance(Handle handle, long activityId) {
        return handle.attach(ActivityInstanceDao.class).insertInstance(activityId, userGuid);
    }

    private void insertTransitions(Handle handle, WorkflowTransition... transitions) {
        handle.attach(WorkflowDao.class).insertTransitions(Arrays.asList(transitions));
        Arrays.stream(transitions).forEach(trans -> assertNotNull(trans.getId()));
    }

    private void turnOffTransition(Handle handle, WorkflowTransition transition) {
        assertEquals(1, handle.attach(JdbiWorkflowTransition.class).updateIsActiveById(transition.getId(), false));
    }
}
