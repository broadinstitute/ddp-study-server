package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.constants.RouteConstants.Header.DDP_CONTENT_STYLE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserAnnouncementDao;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.user.UserAnnouncement;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetUserAnnouncementsRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String url;
    private static long revisionId;
    private static Template msgTemplate;
    private static String msgPlainText;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();

            msgPlainText = "thank you!";
            msgTemplate = Template.html("<strong>thank you!</strong>");
            revisionId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), testData.getUserId(), "test");
            handle.attach(TemplateDao.class).insertTemplate(msgTemplate, revisionId);
            assertNotNull(msgTemplate.getTemplateId());

            // Start fresh with no announcements.
            handle.attach(UserAnnouncementDao.class).deleteAllForParticipantAndStudy(testData.getUserId(), testData.getStudyId());
        });

        String endpoint = API.USER_STUDY_ANNOUNCEMENTS
                .replace(PathParam.USER_GUID, "{userGuid}")
                .replace(PathParam.STUDY_GUID, "{studyGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @After
    public void cleanup() {
        TransactionWrapper.useTxn(handle -> handle.attach(UserAnnouncementDao.class)
                .deleteAllForParticipantAndStudy(testData.getUserId(), testData.getStudyId()));
    }

    @Test
    public void test_nonExistentUser_returns401() {
        given().auth().oauth2(token)
                .pathParam("userGuid", "non-existent-user")
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(401);
    }

    @Test
    public void test_nonExistentStudy_returns401() {
        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", "non-existent-study")
                .when().get(url)
                .then().assertThat()
                .statusCode(401);
    }

    @Test
    public void test_noAnnouncements_returnEmptyList() {
        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(0));
    }

    @Test
    public void test_singleAnnouncement_deletedAfterRead() {
        TransactionWrapper.useTxn(handle -> handle.attach(UserAnnouncementDao.class)
                .insert(testData.getUserId(), testData.getStudyId(), msgTemplate.getTemplateId(), false));

        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .body("[0].permanent", equalTo(false))
                .body("[0].message", equalTo(msgTemplate.getTemplateText()));

        TransactionWrapper.useTxn(handle -> {
            long numFound = handle.attach(UserAnnouncementDao.class)
                    .findAllForParticipantAndStudy(testData.getUserId(), testData.getStudyId())
                    .count();
            assertEquals(0, numFound);
        });
    }

    @Test
    public void test_multipleAnnouncements_oldestOneFirst_andDeletedAfterRead() {
        Template oldestTemplate = TransactionWrapper.withTxn(handle -> {
            Template oldest = Template.html("<p>i am old</p>");
            handle.attach(TemplateDao.class).insertTemplate(oldest, revisionId);
            assertNotNull(oldest.getTemplateId());

            long nowMillis = Instant.now().toEpochMilli();
            UserAnnouncementDao announcementDao = handle.attach(UserAnnouncementDao.class);

            announcementDao.insert("guid1", testData.getUserId(), testData.getStudyId(), oldest.getTemplateId(), false, nowMillis - 1000);
            announcementDao.insert("guid2", testData.getUserId(), testData.getStudyId(), msgTemplate.getTemplateId(), false, nowMillis);

            return oldest;
        });

        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(2))
                .body("[0].message", equalTo(oldestTemplate.getTemplateText()))
                .body("[1].message", equalTo(msgTemplate.getTemplateText()));

        TransactionWrapper.useTxn(handle -> {
            long numFound = handle.attach(UserAnnouncementDao.class)
                    .findAllForParticipantAndStudy(testData.getUserId(), testData.getStudyId())
                    .count();
            assertEquals(0, numFound);
        });
    }

    @Test
    public void test_malformedContentStyle_returns400() {
        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .header(new Header(DDP_CONTENT_STYLE, "foobar"))
                .when().get(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.MALFORMED_HEADER))
                .body("message", containsString("foobar"));
    }

    @Test
    public void test_basicContentStyle() {
        TransactionWrapper.useTxn(handle -> handle.attach(UserAnnouncementDao.class)
                .insert(testData.getUserId(), testData.getStudyId(), msgTemplate.getTemplateId(), false));

        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .header(new Header(DDP_CONTENT_STYLE, ContentStyle.BASIC.name()))
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .body("[0].message", equalTo(msgPlainText));
    }

    @Test
    public void test_permanentAnnouncements_notDeletedAfterRead() {
        TransactionWrapper.useTxn(handle -> handle.attach(UserAnnouncementDao.class)
                .insert(testData.getUserId(), testData.getStudyId(), msgTemplate.getTemplateId(), true));

        String guid = given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .body("[0].guid", not(isEmptyOrNullString()))
                .body("[0].permanent", equalTo(true))
                .body("[0].message", equalTo(msgTemplate.getTemplateText()))
                .extract().path("[0].guid");

        TransactionWrapper.useTxn(handle -> {
            List<UserAnnouncement> found = handle.attach(UserAnnouncementDao.class)
                    .findAllForParticipantAndStudy(testData.getUserId(), testData.getStudyId())
                    .collect(Collectors.toList());
            assertEquals(1, found.size());
            assertEquals(guid, found.get(0).getGuid());
        });

        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .body("[0].guid", equalTo(guid));
    }
}
