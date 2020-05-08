package org.broadinstitute.ddp.model.event;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CreateInvitationEventActionTest extends TxnAwareBaseTest {

    private static final String CONTACT_EMAIL_SID = "Q_CONTACT_EMAIL";

    private static TestDataSetupUtil.GeneratedTestData testData;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void test_noContactEmailAnswer() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("answer for contact email question " + CONTACT_EMAIL_SID));

        TransactionWrapper.useTxn(handle -> {
            newContactEmailActivity(handle);

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getStudyId(), EventTriggerType.REACHED_AOM_PREP);
            var action = new CreateInvitationEventAction(null, CONTACT_EMAIL_SID, false);
            action.doAction(null, handle, signal);

            fail("expected exception should have been thrown");
        });
    }

    @Test
    public void test_contactEmailAnswerIsNotValidEmail() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("not a valid email"));

        TransactionWrapper.useTxn(handle -> {
            FormActivityDef activity = newContactEmailActivity(handle);
            ActivityInstanceDto instance = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activity.getActivityId(), testData.getUserGuid());
            var ans = new TextAnswer(null, CONTACT_EMAIL_SID, null, "not-email");
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instance.getId(), ans);

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getStudyId(), EventTriggerType.REACHED_AOM_PREP);
            var action = new CreateInvitationEventAction(null, CONTACT_EMAIL_SID, false);
            action.doAction(null, handle, signal);

            fail("expected exception should have been thrown");
        });
    }

    @Test
    public void test_createInvitation_fromLatestActivityInstanceContactEmail() {
        TransactionWrapper.useTxn(handle -> {
            var invitationDao = handle.attach(InvitationDao.class);
            assertTrue(invitationDao.findInvitations(testData.getStudyId(), testData.getUserId()).isEmpty());

            AnswerDao answerDao = handle.attach(AnswerDao.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);

            FormActivityDef activity = newContactEmailActivity(handle);
            var instance1 = instanceDao.insertInstance(activity.getActivityId(), testData.getUserGuid());
            var ans1 = new TextAnswer(null, CONTACT_EMAIL_SID, null, "not-email");
            answerDao.createAnswer(testData.getUserId(), instance1.getId(), ans1);
            var instance2 = instanceDao.insertInstance(activity.getActivityId(), testData.getUserGuid());
            var ans2 = new TextAnswer(null, CONTACT_EMAIL_SID, null, "test-invitations@datadonationplatform.org");
            answerDao.createAnswer(testData.getUserId(), instance2.getId(), ans2);

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getStudyId(), EventTriggerType.REACHED_AOM_PREP);
            var action = new CreateInvitationEventAction(null, CONTACT_EMAIL_SID, false);
            action.doAction(null, handle, signal);

            List<InvitationDto> invitations = invitationDao.findInvitations(testData.getStudyId(), testData.getUserId());
            assertEquals(1, invitations.size());
            assertEquals(ans2.getValue(), invitations.get(0).getContactEmail());

            handle.rollback();
        });
    }

    @Test
    public void test_createInvitation_fromTriggeredActivityInstanceContactEmail() {
        TransactionWrapper.useTxn(handle -> {
            var invitationDao = handle.attach(InvitationDao.class);
            assertTrue(invitationDao.findInvitations(testData.getStudyId(), testData.getUserId()).isEmpty());

            AnswerDao answerDao = handle.attach(AnswerDao.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);

            FormActivityDef activity = newContactEmailActivity(handle);
            var instance1 = instanceDao.insertInstance(activity.getActivityId(), testData.getUserGuid());
            var ans1 = new TextAnswer(null, CONTACT_EMAIL_SID, null, "test-invitations@datadonationplatform.org");
            answerDao.createAnswer(testData.getUserId(), instance1.getId(), ans1);
            var instance2 = instanceDao.insertInstance(activity.getActivityId(), testData.getUserGuid());
            var ans2 = new TextAnswer(null, CONTACT_EMAIL_SID, null, "not-email");
            answerDao.createAnswer(testData.getUserId(), instance2.getId(), ans2);

            var signal = new ActivityInstanceStatusChangeSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    instance1.getId(), instance1.getActivityId(), testData.getStudyId(), InstanceStatusType.COMPLETE);
            var action = new CreateInvitationEventAction(null, CONTACT_EMAIL_SID, false);
            action.doAction(null, handle, signal);

            List<InvitationDto> invitations = invitationDao.findInvitations(testData.getStudyId(), testData.getUserId());
            assertEquals(1, invitations.size());
            assertEquals(ans1.getValue(), invitations.get(0).getContactEmail());

            handle.rollback();
        });
    }

    @Test
    public void test_voidsOldInvitations() {
        TransactionWrapper.useTxn(handle -> {
            String oldInvitationGuid = handle.attach(InvitationFactory.class).createInvitation(
                    InvitationType.AGE_UP, testData.getStudyId(), testData.getUserId(), "t1@datadonationplatform.org")
                    .getInvitationGuid();

            FormActivityDef activity = newContactEmailActivity(handle);
            var instance1 = handle.attach(ActivityInstanceDao.class).insertInstance(activity.getActivityId(), testData.getUserGuid());
            var ans1 = new TextAnswer(null, CONTACT_EMAIL_SID, null, "test-invitations@datadonationplatform.org");
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instance1.getId(), ans1);

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getStudyId(), EventTriggerType.REACHED_AOM_PREP);
            var action = new CreateInvitationEventAction(null, CONTACT_EMAIL_SID, true);
            action.doAction(null, handle, signal);

            InvitationDto oldInvitation = handle.attach(InvitationDao.class)
                    .findByInvitationGuid(testData.getStudyId(), oldInvitationGuid).get();
            assertTrue(oldInvitation.isVoid());

            handle.rollback();
        });
    }

    @Test
    public void test_triggersDownstreamEvents() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef activity = newContactEmailActivity(handle);
            var instance1 = handle.attach(ActivityInstanceDao.class).insertInstance(activity.getActivityId(), testData.getUserGuid());
            var ans1 = new TextAnswer(null, CONTACT_EMAIL_SID, null, "test-invitations@datadonationplatform.org");
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instance1.getId(), ans1);

            // setup downstream event
            long triggerId = handle.attach(EventTriggerDao.class).insertStaticTrigger(EventTriggerType.INVITATION_CREATED);
            long actionId = handle.attach(EventActionDao.class).insertMarkActivitiesReadOnlyAction(Set.of(activity.getActivityId()));
            handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId, testData.getStudyId(),
                    Instant.now().toEpochMilli(), null, null, null, null, false, 1);

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getStudyId(), EventTriggerType.REACHED_AOM_PREP);
            var action = new CreateInvitationEventAction(null, CONTACT_EMAIL_SID, true);

            InvitationCreatedSignal nextSignal = action.run(handle, signal);
            assertNotNull("should provide new signal for triggering downstream events", nextSignal);
            assertNotNull("should have new invitation as context", nextSignal.getInvitationDto());
            assertEquals(ans1.getValue(), nextSignal.getInvitationDto().getContactEmail());

            // do it for real
            action.doAction(null, handle, signal);
            assertTrue(handle.attach(JdbiActivityInstance.class).getByActivityInstanceId(instance1.getId()).get().isReadonly());

            handle.rollback();
        });
    }

    private FormActivityDef newContactEmailActivity(Handle handle) {
        TextQuestionDef question = TextQuestionDef
                .builder(TextInputType.TEXT, CONTACT_EMAIL_SID, Template.text("enter email"))
                .build();
        FormActivityDef form = FormActivityDef
                .generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                .addName(new Translation("en", "test activity"))
                .addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(question))))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add test activity"));
        assertNotNull(form.getActivityId());
        return form;
    }
}
