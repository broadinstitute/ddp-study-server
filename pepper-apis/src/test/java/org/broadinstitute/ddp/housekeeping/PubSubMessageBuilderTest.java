package org.broadinstitute.ddp.housekeeping;

import static org.broadinstitute.ddp.Housekeeping.DDP_MESSAGE_ID;
import static org.broadinstitute.ddp.Housekeeping.DDP_STUDY_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PROXY_FIRST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PROXY_LAST_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import com.google.gson.Gson;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.NotificationDetailsDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
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
        PubSubMessageBuilder builder = new PubSubMessageBuilder(cfg);

        QueuedEventDto event = new QueuedNotificationDto(
                new QueuedEventDto(1L, 1L, null, testData.getUserGuid(), testData.getUserHruid(),
                        EventActionType.NOTIFICATION, "1.0", null, "topic", null, null, testData.getStudyGuid()),
                new NotificationDetailsDto(NotificationType.EMAIL, NotificationServiceType.SENDGRID, "apiKey",
                        "fromName", "fromEmail", "salutation", "first", "last", "template", null, "url", null));

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
                    new QueuedEventDto(1L, 1L, testData.getUserId(), gov.getGovernedUserGuid(), "hruid",
                            EventActionType.NOTIFICATION, "1.0", null, "topic", null, null, testData.getStudyGuid()),
                    new NotificationDetailsDto(NotificationType.EMAIL, NotificationServiceType.SENDGRID, "apiKey",
                            "fromName", "fromEmail", "salutation", "first", "last", "template", null, "url", null));

            PubsubMessage msg = new PubSubMessageBuilder(cfg).createMessage("test", event, handle);
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
                    new QueuedEventDto(1L, 1L, operator.getId(), gov.getGovernedUserGuid(), "hruid",
                            EventActionType.NOTIFICATION, "1.0", null, "topic", null, null, testData.getStudyGuid()),
                    new NotificationDetailsDto(NotificationType.EMAIL, NotificationServiceType.SENDGRID, "apiKey",
                            "fromName", "fromEmail", "salutation", "first", "last", "template", null, "url", null));

            PubsubMessage msg = new PubSubMessageBuilder(cfg).createMessage("test", event, handle);
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
}
