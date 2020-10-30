package org.broadinstitute.ddp.model.event;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.time.Instant;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.NotificationTemplateVariables;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class NotificationEventActionTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void test_invitationEmail_noInvitations_fails() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("Could not find any non-voided invitations"));
        TransactionWrapper.useTxn(handle -> {
            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), testData.getStudyId(), EventTriggerType.REACHED_AOM);
            var dto = createEventConfigurationDto(handle, EventTriggerType.REACHED_AOM,
                    NotificationType.INVITATION_EMAIL, NotificationServiceType.SENDGRID, null);
            var action = new NotificationEventAction(new EventConfiguration(dto), dto);
            action.doAction(null, handle, signal);
            fail("expected exception should have been thrown");
        });
    }

    @Test
    public void test_invitationEmail_overridesRecipientEmail() {
        TransactionWrapper.useTxn(handle -> {
            var invitationFactory = handle.attach(InvitationFactory.class);
            InvitationDto invitationDto = invitationFactory.createAgeUpInvitation(
                    testData.getStudyId(), testData.getUserId(), "test@datadonationplatform.org");

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), testData.getStudyId(), EventTriggerType.REACHED_AOM);
            var dto = createEventConfigurationDto(handle, EventTriggerType.REACHED_AOM,
                    NotificationType.INVITATION_EMAIL, NotificationServiceType.SENDGRID, null);
            var action = new NotificationEventAction(new EventConfiguration(dto), dto);
            long queuedEventId = action.run(handle, signal);

            QueuedNotificationDto eventDto = fetchQueuedEvent(handle, queuedEventId);
            assertEquals(NotificationType.INVITATION_EMAIL, eventDto.getNotificationType());
            assertEquals(invitationDto.getContactEmail(), eventDto.getToEmail());

            handle.rollback();
        });
    }

    @Test
    public void test_invitationEmail_providesInvitationIdSubstitution() {
        TransactionWrapper.useTxn(handle -> {
            var invitationFactory = handle.attach(InvitationFactory.class);
            InvitationDto invitationDto = invitationFactory.createAgeUpInvitation(
                    testData.getStudyId(), testData.getUserId(), "test@datadonationplatform.org");

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), testData.getStudyId(), EventTriggerType.REACHED_AOM);
            var dto = createEventConfigurationDto(handle, EventTriggerType.REACHED_AOM,
                    NotificationType.INVITATION_EMAIL, NotificationServiceType.SENDGRID, null);
            var action = new NotificationEventAction(new EventConfiguration(dto), dto);
            long queuedEventId = action.run(handle, signal);

            QueuedNotificationDto eventDto = fetchQueuedEvent(handle, queuedEventId);
            assertEquals(NotificationType.INVITATION_EMAIL, eventDto.getNotificationType());

            NotificationTemplateSubstitutionDto substitutionDto = eventDto.getTemplateSubstitutions()
                    .stream()
                    .filter(sub -> sub.getVariableName().equals(NotificationTemplateVariables.DDP_INVITATION_ID))
                    .findFirst()
                    .orElse(null);
            assertNotNull(substitutionDto);
            assertEquals(invitationDto.getInvitationGuid(), substitutionDto.getValue());

            handle.rollback();
        });
    }

    @Test
    public void test_invitationEmail_usesLatestNonVoidedInvitation() {
        TransactionWrapper.useTxn(handle -> {
            var invitationFactory = handle.attach(InvitationFactory.class);
            InvitationDto invitation1 = invitationFactory.createAgeUpInvitation(
                    testData.getStudyId(), testData.getUserId(), "test111@datadonationplatform.org");
            InvitationDto invitation2 = invitationFactory.createAgeUpInvitation(
                    testData.getStudyId(), testData.getUserId(), "test222@datadonationplatform.org");
            InvitationDto invitation3 = invitationFactory.createAgeUpInvitation(
                    testData.getStudyId(), testData.getUserId(), "test333@datadonationplatform.org");

            var invitationDao = handle.attach(InvitationDao.class);
            invitationDao.markVoided(invitation1.getInvitationId(), Instant.now());

            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), testData.getStudyId(), EventTriggerType.REACHED_AOM);
            var dto = createEventConfigurationDto(handle, EventTriggerType.REACHED_AOM,
                    NotificationType.INVITATION_EMAIL, NotificationServiceType.SENDGRID, null);
            var action = new NotificationEventAction(new EventConfiguration(dto), dto);
            long queuedEventId = action.run(handle, signal);

            QueuedNotificationDto eventDto = fetchQueuedEvent(handle, queuedEventId);
            assertEquals(NotificationType.INVITATION_EMAIL, eventDto.getNotificationType());
            assertEquals(invitation3.getContactEmail(), eventDto.getToEmail());

            handle.rollback();
        });
    }

    @Test
    public void test_invitationEmail_usesInvitationProvidedInSignal() {
        TransactionWrapper.useTxn(handle -> {
            var invitationFactory = handle.attach(InvitationFactory.class);
            InvitationDto invitation1 = invitationFactory.createAgeUpInvitation(
                    testData.getStudyId(), testData.getUserId(), "test111@datadonationplatform.org");
            InvitationDto invitation2 = invitationFactory.createAgeUpInvitation(
                    testData.getStudyId(), testData.getUserId(), "test222@datadonationplatform.org");

            var signal = new InvitationCreatedSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getUserGuid(), testData.getStudyId(), invitation1);
            var dto = createEventConfigurationDto(handle, EventTriggerType.INVITATION_CREATED,
                    NotificationType.INVITATION_EMAIL, NotificationServiceType.SENDGRID, null);
            var action = new NotificationEventAction(new EventConfiguration(dto), dto);
            long queuedEventId = action.run(handle, signal);

            QueuedNotificationDto eventDto = fetchQueuedEvent(handle, queuedEventId);
            assertEquals(NotificationType.INVITATION_EMAIL, eventDto.getNotificationType());
            assertEquals(invitation1.getContactEmail(), eventDto.getToEmail());

            NotificationTemplateSubstitutionDto substitutionDto = eventDto.getTemplateSubstitutions()
                    .stream()
                    .filter(sub -> sub.getVariableName().equals(NotificationTemplateVariables.DDP_INVITATION_ID))
                    .findFirst()
                    .orElse(null);
            assertNotNull(substitutionDto);
            assertEquals(invitation1.getInvitationGuid(), substitutionDto.getValue());

            handle.rollback();
        });
    }

    private EventConfigurationDto createEventConfigurationDto(Handle handle, EventTriggerType triggerType,
                                                              NotificationType notificationType, NotificationServiceType serviceType,
                                                              Long linkedActivityId) {
        long eventConfigurationId = insertInvitationEmailEventConfiguration(handle, triggerType);
        return new EventConfigurationDto(eventConfigurationId, triggerType, EventActionType.NOTIFICATION, 0, true,
                null, null, null, 1, MessageDestination.PARTICIPANT_NOTIFICATION.name(),
                null, null, null, null, null, null, null, null,
                notificationType, serviceType, linkedActivityId,
                null, null, null, null, null, null);
    }

    private long insertInvitationEmailEventConfiguration(Handle handle, EventTriggerType staticTriggerType) {
        long triggerId = handle.attach(EventTriggerDao.class).insertStaticTrigger(staticTriggerType);
        long actionId = handle.attach(EventActionDao.class).insertInvitationEmailNotificationAction(
                new SendgridEmailEventActionDto("dummy-template-key", "en", false));
        return handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId, testData.getStudyId(),
                Instant.now().toEpochMilli(), null, 0, null, null, true, 1);
    }

    private QueuedNotificationDto fetchQueuedEvent(Handle handle, long queuedEventId) {
        QueuedEventDto queuedEventDto = handle.attach(EventDao.class)
                .findQueuedEventById(queuedEventId)
                .orElse(null);
        assertNotNull("queued event with id " + queuedEventId + " should exists", queuedEventDto);
        return (QueuedNotificationDto) queuedEventDto;
    }
}
