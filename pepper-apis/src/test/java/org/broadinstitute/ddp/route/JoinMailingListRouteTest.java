package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.restassured.mapper.ObjectMapperType;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiUmbrella;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.json.JoinMailingListPayload;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoinMailingListRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(JoinMailingListRouteTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;

    private static String umbrellaGuid;

    private static String studyGuid;

    private static String addUserToMailingListUrl;

    private static JoinMailingListPayload firstUser;

    private static JoinMailingListPayload secondUser;

    private static String mailingListTemplateKey;

    private static Long insertedEventConfigId;

    @BeforeClass
    public static void setup() {
        mailingListTemplateKey = ConfigUtil.getTestingSendgridTemplates(RouteTestUtil.getConfig()).getConfig(
                "joinMailingList").getString(ConfigFile.Sendgrid.TEMPLATE);
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            setupMailingListEmailEvent(handle);
            umbrellaGuid = handle.attach(JdbiUmbrella.class).findById(
                    testData.getTestingStudy().getUmbrellaId()
            ).get().getGuid();
        });
        studyGuid = testData.getStudyGuid();
        addUserToMailingListUrl = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.JOIN_MAILING_LIST;
        addUserToMailingListUrl = addUserToMailingListUrl.replace(RouteConstants.PathParam.STUDY_GUID,
                studyGuid);

        String firstEmail = "test" + System.currentTimeMillis() + "@datadonationplatform.org";
        firstUser = new JoinMailingListPayload("First" + System.currentTimeMillis(),
                "Last" + System.currentTimeMillis(),
                firstEmail,
                studyGuid,
                null,
                null);


        String secondEmail = "test2" + System.currentTimeMillis() + "@datadonationplatform.org";

        secondUser = new JoinMailingListPayload("First2" + System.currentTimeMillis(),
                "Last2" + System.currentTimeMillis(),
                secondEmail,
                studyGuid,
                null,
                null);

        // add the user to the mailing list so we can re-attempt adding in a subsequent test
        addUserAndAssertStatusCode(firstUser, HttpStatus.SC_NO_CONTENT);
    }

    private static void setupMailingListEmailEvent(Handle handle) {
        EventActionDao eventActionDao = handle.attach(EventActionDao.class);
        JdbiEventConfiguration jdbiEventConfig = handle.attach(JdbiEventConfiguration.class);

        EventTriggerDao eventTriggerDao = handle.attach(EventTriggerDao.class);

        SendgridEmailEventActionDto eventAction = new SendgridEmailEventActionDto(mailingListTemplateKey, "en");
        long emailActionId = eventActionDao.insertNotificationAction(eventAction);

        long eventTriggerId = eventTriggerDao.insertMailingListTrigger();
        insertedEventConfigId = jdbiEventConfig.insert(eventTriggerId, emailActionId, testData.getStudyId(),
                Instant.now().toEpochMilli(), null, null, null,
                null, true, 1);

    }

    private static void addUserAndAssertStatusCode(JoinMailingListPayload user, int statusCode) {
        given().body(user, ObjectMapperType.GSON)
                .when().post(addUserToMailingListUrl).then().assertThat()
                .statusCode(statusCode);
    }

    @Test
    public void testAddNewUser() {
        addUserAndAssertStatusCode(secondUser, HttpStatus.SC_NO_CONTENT);
        verifyUserWasSaved(secondUser);
    }

    private static void verifyUserWasSaved(JoinMailingListPayload user) {
        JdbiMailingList.MailingListEntryDto mailingListEntry = TransactionWrapper.withTxn(handle -> {
            JdbiMailingList jdbiMailingList = handle.attach(JdbiMailingList.class);
            return jdbiMailingList.findByEmailAndStudy(user.getEmailAddress(), studyGuid).get();
        });
        assertNotNull(mailingListEntry);
        assertEquals(user.getFirstName(), mailingListEntry.getFirstName());
        assertEquals(user.getLastName(), mailingListEntry.getLastName());
        assertEquals(user.getEmailAddress(), mailingListEntry.getEmail());

        AtomicBoolean queuedEmail = new AtomicBoolean(false);
        TransactionWrapper.useTxn(handle -> {
            EventDao eventDao = handle.attach(EventDao.class);
            List<QueuedEventDto> queuedEvents = eventDao.findPublishableQueuedEvents();

            for (QueuedEventDto queuedEvent : queuedEvents) {
                if (queuedEvent instanceof QueuedNotificationDto) {
                    QueuedNotificationDto queuedNotification = (QueuedNotificationDto) queuedEvent;

                    LOG.info("Comparing {} against {}", user.getEmailAddress(), queuedNotification.getToEmail());
                    if (user.getEmailAddress().equals(queuedNotification.getToEmail())) {
                        queuedEmail.set(true);
                    }
                }
            }
        });

        Assert.assertTrue("Did not queue email notification for " + user.getEmailAddress(), queuedEmail.get());
    }

    private static void verifyUserWasSavedByUmbrellaGuid(JoinMailingListPayload user) {
        JdbiMailingList.MailingListEntryDto mailingListEntry = TransactionWrapper.withTxn(handle -> {
            JdbiMailingList jdbiMailingList = handle.attach(JdbiMailingList.class);
            return jdbiMailingList.findByEmailAndUmbrellaGuid(user.getEmailAddress(), user.getUmbrellaGuid()).get();
        });
        assertNotNull(mailingListEntry);
        assertEquals(user.getFirstName(), mailingListEntry.getFirstName());
        assertEquals(user.getLastName(), mailingListEntry.getLastName());
        assertEquals(user.getEmailAddress(), mailingListEntry.getEmail());
    }

    @Test
    public void testAddUserThatAlreadyExists() {
        // firstUser should have been added during setup
        addUserAndAssertStatusCode(firstUser, HttpStatus.SC_NO_CONTENT);
        verifyUserWasSaved(firstUser);
    }

    @Test
    public void testAddUserWithBothUmbrellaAndStudy() {
        JoinMailingListPayload badJoinMailingListPayload = new JoinMailingListPayload(
                "firstname", "lastname", "test3@broadinstitute.org", studyGuid,
                new ArrayList<>(), umbrellaGuid);

        addUserAndAssertStatusCode(badJoinMailingListPayload, HttpStatus.SC_BAD_REQUEST);
        verifyUserWasNotSaved(badJoinMailingListPayload);
    }

    @Test
    public void testAddUserWithNeitherUmbrellaNorStudy() {
        JoinMailingListPayload badJoinMailingListPayload = new JoinMailingListPayload(
                "firstname", "lastname", "test3@broadinstitute.org",
                null, new ArrayList<>(), null);

        addUserAndAssertStatusCode(badJoinMailingListPayload, HttpStatus.SC_BAD_REQUEST);
        verifyUserWasNotSaved(badJoinMailingListPayload);
    }

    @Test
    public void testAddUserWithUmbrella() {
        JoinMailingListPayload goodJoinMailingListPayload = new JoinMailingListPayload(
                "firstname", "lastname", "test3@broadinstitute.org", null,
                new ArrayList<>(),  umbrellaGuid);

        addUserAndAssertStatusCode(goodJoinMailingListPayload, HttpStatus.SC_NO_CONTENT);
        verifyUserWasSavedByUmbrellaGuid(goodJoinMailingListPayload);
    }

    @Test
    public void testAddBadEmail() {
        String badEmailAddress = "not a real email address";
        JoinMailingListPayload user = new JoinMailingListPayload("first",
                "lastName",
                badEmailAddress,
                studyGuid,
                null,
                null);
        addUserAndAssertStatusCode(user,
                422);
        verifyUserWasNotSaved(user);
    }

    private void verifyUserWasNotSaved(JoinMailingListPayload user) {
        Optional<JdbiMailingList.MailingListEntryDto> mailingListEntry = TransactionWrapper.withTxn(handle -> {
            JdbiMailingList jdbiMailingList = handle.attach(JdbiMailingList.class);
            return jdbiMailingList.findByEmailAndStudy(user.getEmailAddress(), studyGuid);
        });
        Assert.assertFalse(mailingListEntry.isPresent());
    }

    @Test
    public void testAddMissingFieldsFails() {
        addUserAndAssertStatusCode(new JoinMailingListPayload(null,
                        "lastName",
                        "foo@datadonationplatform.org",
                        studyGuid,
                        null,
                        null),
                422);
        addUserAndAssertStatusCode(new JoinMailingListPayload("first name",
                        "",
                        "foo@datadonationplatform.org",
                        studyGuid,
                        null,
                        null),
                422);
        addUserAndAssertStatusCode(new JoinMailingListPayload("first",
                        "last",
                        null,
                        studyGuid,
                        null,
                        null),
                422);
    }

    @AfterClass
    public static void afterClass() {
        TransactionWrapper.useTxn(handle -> {
            QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
            queuedEventDao.deleteQueuedEventsByEventConfigurationId(insertedEventConfigId);
        });
    }
}
