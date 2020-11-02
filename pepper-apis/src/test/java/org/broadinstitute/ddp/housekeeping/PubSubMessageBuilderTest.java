package org.broadinstitute.ddp.housekeeping;

import static org.broadinstitute.ddp.Housekeeping.DDP_MESSAGE_ID;
import static org.broadinstitute.ddp.Housekeeping.DDP_STUDY_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PROXY_FIRST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PROXY_LAST_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.NotificationDetailsDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.exception.NoSendableEmailAddressException;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationTemplate;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class PubSubMessageBuilderTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Gson gson;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        gson = GsonUtil.standardGson();
    }

    @Test
    public void testCreateMessage_participantHasAuth0Account() {
        PubSubMessageBuilder builder = spy(new PubSubMessageBuilder(cfg));
        doReturn(new NotificationTemplate(1L, "template", false, 1L, "en"))
                .when(builder).determineEmailTemplate(any(), anyLong(), any(), any());

        QueuedEventDto event = new QueuedNotificationDto(
                new QueuedEventDto(1L, testData.getUserId(), testData.getUserGuid(), testData.getUserHruid(),
                        testData.getUserGuid(), 1L, EventTriggerType.REACHED_AOM, EventActionType.NOTIFICATION, null, null,
                        "topic", null, null, testData.getStudyGuid()),
                new NotificationDetailsDto(NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                        null, null, "url", "apiKey", "fromName", "fromEmail",
                        "salutation", "first", "last"));

        PubsubMessage msg = TransactionWrapper.withTxn(handle -> builder.createMessage("test", event, handle));
        assertEquals("test", msg.getAttributesOrThrow(DDP_MESSAGE_ID));
        assertEquals(testData.getStudyGuid(), msg.getAttributesOrThrow(DDP_STUDY_GUID));

        NotificationMessage content = gson.fromJson(msg.getData().toStringUtf8(), NotificationMessage.class);
        assertEquals(testData.getUserGuid(), content.getParticipantGuid());
        assertEquals(1, content.getDistributionList().size());
        assertEquals(testData.getTestingUser().getEmail(), content.getDistributionList().iterator().next());
    }

    @Test
    public void testCreateMessage_participantHasOperatorAsProxy() {
        TransactionWrapper.useTxn(handle -> {
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            Governance gov = userGovernanceDao.createGovernedUserWithGuidAlias(testData.getClientId(), testData.getUserId());
            userGovernanceDao.grantGovernedStudy(gov.getId(), testData.getStudyId());

            QueuedEventDto event = new QueuedNotificationDto(
                    new QueuedEventDto(1L, testData.getUserId(), gov.getGovernedUserGuid(), "hruid",
                            testData.getUserGuid(), 1L, EventTriggerType.REACHED_AOM, EventActionType.NOTIFICATION, null, null,
                            "topic", null, null, testData.getStudyGuid()),
                    new NotificationDetailsDto(NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                            null, null, "url", "apiKey", "fromName", "fromEmail",
                            "salutation", "first", "last"));

            PubSubMessageBuilder builder = spy(new PubSubMessageBuilder(cfg));
            doReturn(new NotificationTemplate(1L, "template", false, 1L, "en"))
                    .when(builder).determineEmailTemplate(any(), anyLong(), any(), any());

            PubsubMessage msg = builder.createMessage("test", event, handle);
            assertEquals("test", msg.getAttributesOrThrow(DDP_MESSAGE_ID));
            assertEquals(testData.getStudyGuid(), msg.getAttributesOrThrow(DDP_STUDY_GUID));

            NotificationMessage content = gson.fromJson(msg.getData().toStringUtf8(), NotificationMessage.class);
            assertEquals(gov.getGovernedUserGuid(), content.getParticipantGuid());
            assertEquals(1, content.getDistributionList().size());
            assertEquals(testData.getTestingUser().getEmail(), content.getDistributionList().iterator().next());

            Optional<String> proxyFirstName = content.getTemplateSubstitutionValue(DDP_PROXY_FIRST_NAME);
            assertTrue(proxyFirstName.isPresent());
            assertEquals(testData.getProfile().getFirstName(), proxyFirstName.get());

            Optional<String> proxyLastName = content.getTemplateSubstitutionValue(DDP_PROXY_LAST_NAME);
            assertTrue(proxyLastName.isPresent());
            assertEquals(testData.getProfile().getLastName(), proxyLastName.get());

            handle.rollback();
        });
    }

    @Test
    public void testCreateMessage_operatorNotProxy_usesFirstProxy() {
        TransactionWrapper.useTxn(handle -> {
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            Governance gov = userGovernanceDao.createGovernedUserWithGuidAlias(testData.getClientId(), testData.getUserId());
            userGovernanceDao.grantGovernedStudy(gov.getId(), testData.getStudyId());

            User operator = handle.attach(UserDao.class).createUser(testData.getClientId(), null);

            QueuedEventDto event = new QueuedNotificationDto(
                    new QueuedEventDto(1L, operator.getId(), gov.getGovernedUserGuid(), "hruid",
                            operator.getGuid(), 1L, EventTriggerType.REACHED_AOM, EventActionType.NOTIFICATION, null, null,
                            "topic", null, null, testData.getStudyGuid()),
                    new NotificationDetailsDto(NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                            null, null, "url", "apiKey", "fromName", "fromEmail",
                            "salutation", "first", "last"));

            PubSubMessageBuilder builder = spy(new PubSubMessageBuilder(cfg));
            doReturn(new NotificationTemplate(1L, "template", false, 1L, "en"))
                    .when(builder).determineEmailTemplate(any(), anyLong(), any(), any());

            PubsubMessage msg = builder.createMessage("test", event, handle);
            assertEquals("test", msg.getAttributesOrThrow(DDP_MESSAGE_ID));
            assertEquals(testData.getStudyGuid(), msg.getAttributesOrThrow(DDP_STUDY_GUID));

            NotificationMessage content = gson.fromJson(msg.getData().toStringUtf8(), NotificationMessage.class);
            assertEquals(gov.getGovernedUserGuid(), content.getParticipantGuid());
            assertEquals(1, content.getDistributionList().size());
            assertEquals(testData.getTestingUser().getEmail(), content.getDistributionList().iterator().next());

            Optional<String> proxyFirstName = content.getTemplateSubstitutionValue(DDP_PROXY_FIRST_NAME);
            assertTrue(proxyFirstName.isPresent());
            assertEquals(testData.getProfile().getFirstName(), proxyFirstName.get());

            Optional<String> proxyLastName = content.getTemplateSubstitutionValue(DDP_PROXY_LAST_NAME);
            assertTrue(proxyLastName.isPresent());
            assertEquals(testData.getProfile().getLastName(), proxyLastName.get());

            handle.rollback();
        });
    }

    @Test
    public void testCreateMessage_deleteUnsendableEmail() {
        TransactionWrapper.useTxn(handle -> {
            boolean shouldDeleteUnsendableEmails = true;
            boolean shouldDisplayLanguageChangePopup = false;
            handle.attach(StudyDao.class).addSettings(testData.getStudyId(), null, null, false, null,
                    shouldDeleteUnsendableEmails, shouldDisplayLanguageChangePopup);

            User user = handle.attach(UserDao.class).createUser(testData.getClientId(), null);
            QueuedEventDto event = new QueuedNotificationDto(
                    new QueuedEventDto(1L, user.getId(), user.getGuid(), user.getHruid(),
                            user.getGuid(), 1L, EventTriggerType.ACTIVITY_STATUS, EventActionType.NOTIFICATION, null, null,
                            "topic", null, null, testData.getStudyGuid()),
                    new NotificationDetailsDto(NotificationType.EMAIL, NotificationServiceType.SENDGRID,
                            null, null, "url", "apiKey", "fromName", "fromEmail",
                            "salutation", "first", "last"));

            try {
                PubSubMessageBuilder builder = new PubSubMessageBuilder(cfg);
                builder.createMessage("test", event, handle);
            } catch (NoSendableEmailAddressException e) {
                assertTrue(e.getMessage().contains("participant " + user.getGuid()));
                assertTrue(e.getMessage().contains("study " + testData.getStudyGuid()));
                handle.rollback();
                return;
            }

            fail("Expected exception not thrown");
        });
    }

    @Test
    public void testDetermineEmailTemplate() {
        List<NotificationTemplate> templates = List.of(
                new NotificationTemplate(1L, "t1", false, 1L, "en"),
                new NotificationTemplate(2L, "t2", false, 2L, "fr"));

        Handle mockHandle = mock(Handle.class);
        EventDao mockEvenDao = mock(EventDao.class);
        doReturn(mockEvenDao).when(mockHandle).attach(EventDao.class);
        doReturn(templates).when(mockEvenDao).getNotificationTemplatesForEvent(anyLong());

        PubSubMessageBuilder builder = new PubSubMessageBuilder(cfg);
        NotificationTemplate actual = builder.determineEmailTemplate(mockHandle, 1L, testData.getStudyGuid(), "fr");

        assertNotNull(actual);
        assertEquals("t2", actual.getTemplateKey());
        assertEquals("fr", actual.getLanguageCode());
    }
}
