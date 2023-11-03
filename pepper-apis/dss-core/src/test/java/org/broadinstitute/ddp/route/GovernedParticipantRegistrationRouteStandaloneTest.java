package org.broadinstitute.ddp.route;

import com.google.api.core.SettableApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.event.publish.pubsub.PubSubPublisherInitializer;
import org.broadinstitute.ddp.json.GovernedUserRegistrationPayload;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class GovernedParticipantRegistrationRouteStandaloneTest extends IntegrationTestSuite.TestCase {

    private static String token;
    private static String url;
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Set<String> userGuidsToDelete = new HashSet<>();
    private static Publisher mockPublisher;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            //insert study languages
            StudyLanguageDao studyLanguageDao = handle.attach(StudyLanguageDao.class);
            studyLanguageDao.insert(testData.getStudyGuid(), "en", "English");
            studyLanguageDao.insert(testData.getStudyGuid(), "es", "Spanish");
            studyLanguageDao.setAsDefaultLanguage(testData.getStudyId(), "en");
        });

        String endpoint = RouteConstants.API.USER_STUDY_PARTICIPANTS
                .replace(RouteConstants.PathParam.USER_GUID, "{userGuid}")
                .replace(RouteConstants.PathParam.STUDY_GUID, "{studyGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;

        mockPublisher = mock(Publisher.class);
        String topicName = ConfigManager.getInstance().getConfig().getString(ConfigFile.PUBSUB_DSM_TASKS_TOPIC);
        PubSubPublisherInitializer.setPublisher(topicName, mockPublisher);
    }

    @AfterClass
    public static void tearDown() {
        String topicName = ConfigManager.getInstance().getConfig().getString(ConfigFile.PUBSUB_DSM_TASKS_TOPIC);
        PubSubPublisherInitializer.setPublisher(topicName, null);
    }

    @Before
    public void setupEach() {
        Mockito.reset(mockPublisher);
        var future = SettableApiFuture.create();
        future.set("some-message-id");
        doReturn(future).when(mockPublisher).publish(any());
    }

    @After
    public void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            var jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            var profileDao = handle.attach(UserProfileDao.class);
            var dataExportDao = handle.attach(DataExportDao.class);
            for (var userGuid : userGuidsToDelete) {
                dataExportDao.deleteDataSyncRequestsForUser(userGuid);
                jdbiEnrollment.deleteByUserGuidStudyGuid(userGuid, testData.getStudyGuid());
                profileDao.getUserProfileSql().deleteByUserGuid(userGuid);
            }
            handle.attach(UserGovernanceDao.class).deleteAllGovernancesForProxy(testData.getUserId());
            handle.attach(JdbiUser.class).deleteAllByGuids(userGuidsToDelete);
            userGuidsToDelete.clear();
        });
    }

    @Test
    public void invalidStudy() {
        GovernedUserRegistrationPayload payload = new GovernedUserRegistrationPayload("it", "John", "Doe",
                "Europe/Amsterdam");
        postRequest("invalid-study-guid", payload)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void createFew() {
        GovernedUserRegistrationPayload payload1 = new GovernedUserRegistrationPayload("es", "John", "Doe",
                "Europe/Amsterdam");
        GovernedUserRegistrationPayload payload2 = new GovernedUserRegistrationPayload("en", "Frank", "Johnson",
                "Europe/Moscow");

        //no payload
        GovernedUserRegistrationPayload payload3 = new GovernedUserRegistrationPayload(null, null, null, null);

        List<String> governedUserGuids = new ArrayList<>();
        String governedUser1Guid = postRequest(testData.getStudyGuid(), payload1)
                .then().assertThat()
                .statusCode(200)
                .extract().path("ddpUserGuid");
        governedUserGuids.add(governedUser1Guid);
        //check governed users preferred language
        UserProfile child1Profile = TransactionWrapper.withTxn(handle ->
                handle.attach(UserProfileDao.class).findProfileByUserGuid(governedUser1Guid).get());
        assertTrue("Governed User preferred language does not match",
                child1Profile.getPreferredLangCode().equals("es"));

        governedUserGuids.add(postRequest(testData.getStudyGuid(), payload2)
                .then().assertThat()
                .statusCode(200)
                .extract().path("ddpUserGuid"));

        //update parents preferred language to "es"
        UserProfile parentProfile = testData.getProfile();
        UserProfile newProfileES = UserProfile.builder()
                .userId(parentProfile.getUserId())
                .firstName(parentProfile.getFirstName())
                .lastName(parentProfile.getLastName())
                .birthDate(parentProfile.getBirthDate())
                .doNotContact(parentProfile.getDoNotContact())
                .preferredLangCode("es")
                .preferredLangId(LanguageStore.get("es").getId())
                .build();

        parentProfile = TransactionWrapper.withTxn(handle ->
                handle.attach(UserProfileDao.class).updateProfile(newProfileES));

        String governedUserParentLanguageGuid = postRequest(testData.getStudyGuid(), payload3)
                .then().assertThat()
                .statusCode(200)
                .extract().path("ddpUserGuid");

        governedUserGuids.add(governedUserParentLanguageGuid);
        UserProfile childProfile = TransactionWrapper.withTxn(handle ->
                handle.attach(UserProfileDao.class).findProfileByUserGuid(governedUserParentLanguageGuid).get());
        assertTrue("Governed User preferred language doesnt match parent preferred language",
                childProfile.getPreferredLangCode().equals(parentProfile.getPreferredLangCode()));

        userGuidsToDelete.addAll(governedUserGuids);
        List<Governance> governances = TransactionWrapper.withTxn(handle -> handle.attach(UserGovernanceDao.class)
                .findActiveGovernancesByProxyAndStudyGuids(testData.getUserGuid(), testData.getStudyGuid())
                .collect(Collectors.toList()));
        for (Governance governance : governances) {
            assertTrue("There is unexpected governance in the list", governedUserGuids.contains(governance.getGovernedUserGuid()));
            governedUserGuids.remove(governance.getGovernedUserGuid());
        }
        assertTrue("Governed user is not found in the governances list", governedUserGuids.isEmpty());

        verify(mockPublisher, times(2)).publish(any());
    }

    private Response postRequest(String studyGuid, GovernedUserRegistrationPayload payload) {
        return given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", studyGuid)
                .body(payload, ObjectMapperType.GSON)
                .when().post(url);
    }
}
