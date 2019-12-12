package org.broadinstitute.ddp.route;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDao;
import org.broadinstitute.ddp.db.UserDaoFactory;
import org.broadinstitute.ddp.json.Error;
import org.broadinstitute.ddp.json.Profile;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileRouteTest extends IntegrationTestSuite.TestCase {


    public static final String PEPPER_V1 = "/pepper/v1/";
    private static final Logger LOG = LoggerFactory.getLogger(ProfileRouteTest.class);
    //profile testing constants
    private static final Profile.Sex sex = Profile.Sex.FEMALE;
    private static final Integer birthMonth = 3;
    private static final Integer birthDayInMonth = 15;
    private static final Integer birthYear = 1995;
    private static final String firstName = "Fakie";
    private static final String lastName = "McFakerton";
    private static final String preferredLanguage = Locale.ENGLISH.getLanguage();
    private static final Collection<String> profileUserIdsToDelete = new HashSet<>();
    static Config sqlConfig;
    static UserDao userDao;
    private static String token;
    private static String guid;
    private static String url;
    private final Gson gson = new Gson();

    @BeforeClass
    public static void beforeClass() throws Exception {
        sqlConfig = ConfigFactory.parseResources("sql.conf");

        token = RouteTestUtil.loginStaticTestUserForToken();
        guid = RouteTestUtil.getUnverifiedUserGuidFromToken(token);

        url = RouteTestUtil.getTestingBaseUrl() + API.USER_PROFILE.replace(PathParam.USER_GUID, guid);
    }

    @Before
    public void setupData() {
        userDao = UserDaoFactory.createFromSqlConfig(sqlConfig);
        TransactionWrapper.useTxn(handle -> {
            if (guid != null) {
                try {
                    deleteProfile(handle.getConnection(), guid);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Beginning of functions used in Profile functions.
     */
    @After
    public void deleteTestingProfiles() throws SQLException {
        for (String guid : profileUserIdsToDelete) {
            TransactionWrapper.withTxn(handle -> {
                if (guid != null) {
                    deleteProfile(handle.getConnection(), guid);
                }
                return null;
            });
        }
    }

    private void deleteProfile(Connection conn, String guid) throws SQLException {
        LOG.info("Deleting test user {} ", guid);
        try (PreparedStatement stmt = conn.prepareStatement(
                "delete from user_profile where user_id = (select user_id from user where guid = ?)")) {
            stmt.setString(1, guid);
            int numRowsDeleted = stmt.executeUpdate();
            if (numRowsDeleted != 1) {
                LOG.error("Removed {} user_profile rows for user_id {}", numRowsDeleted, guid);
            }
        }
    }

    private Profile successfulAddPostCheck(Profile payload) throws IOException {
        Response response = RouteTestUtil.buildAuthorizedPostRequest(token, url, gson.toJson(payload)).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(201, res.getStatusLine().getStatusCode());

        //validate profile generatedTestData
        String bodyToString = EntityUtils.toString(res.getEntity());
        Profile queriedProfile = gson.fromJson(bodyToString, Profile.class);
        Assert.assertEquals(payload.getSex(), queriedProfile.getSex());
        Assert.assertEquals(payload.getBirthDayInMonth(), queriedProfile.getBirthDayInMonth());
        Assert.assertEquals(payload.getBirthMonth(), queriedProfile.getBirthMonth());
        Assert.assertEquals(payload.getBirthYear(), queriedProfile.getBirthYear());

        return queriedProfile;
    }

    private void failedAddCheck(Profile payload) throws IOException {
        Response response = RouteTestUtil.buildAuthorizedPostRequest(token, url, gson.toJson(payload)).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(400, res.getStatusLine().getStatusCode());
    }

    private void checkNumProfile(Connection conn, int expectedProfileNum) throws SQLException {
        try (ResultSet rs = conn.prepareStatement("SELECT COUNT(1) FROM user_profile WHERE user_id ="
                + " (SELECT user_id FROM user WHERE guid ='" + guid + "')").executeQuery()) {
            rs.next();
            Assert.assertEquals(expectedProfileNum, rs.getInt(1));   //makes sure no duplicates
        }
    }

    private void postDummyProfile() throws Exception {
        profileUserIdsToDelete.add(guid);
        Profile profile = new Profile(birthDayInMonth, birthMonth, birthYear, sex, preferredLanguage, firstName, lastName);
        successfulAddPostCheck(profile);
    }

    private JsonObject createProfileJsonObject(String sex, Integer birthDayInMonth, Integer birthMonth,
                                               Integer birthYear, String preferredLanguage,
                                               String firstName, String lastName) {
        JsonObject updatedProfile = new JsonObject();
        updatedProfile.addProperty(Profile.SEX, sex);
        updatedProfile.addProperty(Profile.BIRTH_DAY_IN_MONTH, birthDayInMonth);
        updatedProfile.addProperty(Profile.BIRTH_MONTH, birthMonth);
        updatedProfile.addProperty(Profile.BIRTH_YEAR, birthYear);
        updatedProfile.addProperty(Profile.PREFERRED_LANGUAGE, preferredLanguage);
        updatedProfile.addProperty(Profile.FIRST_NAME, firstName);
        updatedProfile.addProperty(Profile.LAST_NAME, lastName);

        return updatedProfile;
    }

    /**
     * Beginning of Profile tests.
     */

    //makes sure everything goes correctly when adding a unique profile that hasn't been added yet and user exists
    @Test
    public void testAddProfileGoodPayload() throws Exception {
        postDummyProfile();
        TransactionWrapper.withTxn((handle) -> {
            checkNumProfile(handle.getConnection(), 1);
            return null;
        });
    }

    /**
     * tests successful adding of null fields to a profile.
     */
    @Test
    public void testAddNullProfile() throws Exception {
        profileUserIdsToDelete.add(guid);
        JsonObject payload = createProfileJsonObject(null, null, null, null, null, null, null);
        Response response = RouteTestUtil.buildAuthorizedPostRequest(token, url, payload.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(201, res.getStatusLine().getStatusCode());

        String bodyToString = EntityUtils.toString(res.getEntity());
        Profile queriedProfile = gson.fromJson(bodyToString, Profile.class);
        Assert.assertNull(queriedProfile.getSex());
        Assert.assertNull(queriedProfile.getBirthDayInMonth());
        Assert.assertNull(queriedProfile.getBirthMonth());
        Assert.assertNull(queriedProfile.getBirthYear());
        Assert.assertNull(queriedProfile.getPreferredLanguage());

    }

    // tests to make sure a second entry isn't made when trying to add an existing profile
    @Test
    public void testAddProfileDuplicateUserId() throws Exception {
        postDummyProfile();
        failedAddCheck(new Profile(birthDayInMonth, birthMonth, birthYear, sex, preferredLanguage, firstName, lastName));
        TransactionWrapper.withTxn((handle) -> {
            checkNumProfile(handle.getConnection(), 1);
            return null;
        });
    }

    // tests to make sure doesn't add profile with missing body
    @Test
    public void testAddProfileMissingBody() throws Exception {
        profileUserIdsToDelete.add(guid);
        String payload = null;

        Response response = RouteTestUtil.buildAuthorizedPostRequest(token, url, gson.toJson(payload)).execute();
        HttpResponse res = response.returnResponse();

        Assert.assertEquals(400, res.getStatusLine().getStatusCode());

        TransactionWrapper.withTxn((handle) -> {
            checkNumProfile(handle.getConnection(), 0);
            return null;
        });
    }

    /**
     * tests to make sure that throws 400 Error when given a preferred language not in table.
     */
    @Test
    public void testAddProfileBadLanguage() throws Exception {
        profileUserIdsToDelete.add(guid);

        Profile payload = new Profile(birthDayInMonth, birthMonth, birthYear, sex, "abc", firstName, lastName);

        Response response = RouteTestUtil.buildAuthorizedPostRequest(token, url, gson.toJson(payload)).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(400, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        Error error = gson.fromJson(bodyToString, Error.class);
        Assert.assertEquals(ErrorCodes.INVALID_LANGUAGE_PREFERENCE, error.getErrorCode());
    }

    /**
     * tests to make sure if invalid sex string, throws 400 error.
     */
    @Test
    public void testAddProfileBadGender() throws Exception {
        profileUserIdsToDelete.add(guid);
        JsonObject profile = new JsonObject();
        profile.addProperty(Profile.SEX, "AAA");
        profile.addProperty(Profile.BIRTH_MONTH, birthMonth);
        profile.addProperty(Profile.BIRTH_YEAR, birthYear);

        Response response = RouteTestUtil.buildAuthorizedPostRequest(token, url, profile.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(400, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        Error error = gson.fromJson(bodyToString, Error.class);
        Assert.assertEquals(ErrorCodes.INVALID_SEX, error.getErrorCode());
    }

    //tests that if there is an existing user with a complete profile, you can retrieve it.
    @Test
    public void testGetFullProfile() throws Exception {
        postDummyProfile();

        Response response = RouteTestUtil.buildAuthorizedGetRequest(token, url).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(200, res.getStatusLine().getStatusCode());

        String bodyToString = EntityUtils.toString(res.getEntity());
        Profile queriedProfile = gson.fromJson(bodyToString, Profile.class);
        Assert.assertEquals(sex, queriedProfile.getSex());
        Assert.assertEquals(birthDayInMonth, queriedProfile.getBirthDayInMonth());
        Assert.assertEquals(birthMonth, queriedProfile.getBirthMonth());
        Assert.assertEquals(birthYear, queriedProfile.getBirthYear());
        Assert.assertEquals(preferredLanguage, queriedProfile.getPreferredLanguage());
    }

    //tests that if there is an existing user with a partially complete profile, you can retrieve it.
    @Test
    public void testGetPartialProfile() throws Exception {
        profileUserIdsToDelete.add(guid);
        JsonObject payload = createProfileJsonObject(null, null, null, null, null, null, null);
        Response response = RouteTestUtil.buildAuthorizedPostRequest(token, url, payload.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(201, res.getStatusLine().getStatusCode());

        response = RouteTestUtil.buildAuthorizedGetRequest(token, url).execute();
        res = response.returnResponse();
        Assert.assertEquals(200, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        Profile queriedProfile = gson.fromJson(bodyToString, Profile.class);
        Assert.assertNull(queriedProfile.getSex());
        Assert.assertNull(queriedProfile.getBirthDayInMonth());
        Assert.assertNull(queriedProfile.getBirthMonth());
        Assert.assertNull(queriedProfile.getBirthYear());
        Assert.assertNull(queriedProfile.getPreferredLanguage());
    }

    /**
     * tests that if there is an existing user with a profile not in database, there is a 422 error message
     * for a missing profile.
     */
    @Test
    public void testGetProfileNotInDatabase() throws Exception {
        Response response = RouteTestUtil.buildAuthorizedGetRequest(token, url).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(400, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        Error error = gson.fromJson(bodyToString, Error.class);
        Assert.assertEquals(error.getErrorCode(), ErrorCodes.MISSING_PROFILE);
    }

    // For existing profile and user, update all information.
    @Test
    public void testPatchFullProfile() throws Exception {
        postDummyProfile();
        Integer dummyBirthMonth = 2;
        Integer dummyBirthDayInMonth = 16;
        Integer dummyBirthYear = 2004;
        JsonObject updatedProfile = createProfileJsonObject(Profile.Sex.MALE.name(),
                dummyBirthDayInMonth, dummyBirthMonth, dummyBirthYear, "ru", "foo", "bar");

        Response response = RouteTestUtil.buildAuthorizedPatchRequest(token, url, updatedProfile.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(200, res.getStatusLine().getStatusCode());

        String bodyToString = EntityUtils.toString(res.getEntity());
        Profile queriedProfile = gson.fromJson(bodyToString, Profile.class);
        Assert.assertEquals(Profile.Sex.MALE, queriedProfile.getSex());
        Assert.assertEquals(dummyBirthDayInMonth, queriedProfile.getBirthDayInMonth());
        Assert.assertEquals(dummyBirthMonth, queriedProfile.getBirthMonth());
        Assert.assertEquals(dummyBirthYear, queriedProfile.getBirthYear());
        Assert.assertEquals("ru", queriedProfile.getPreferredLanguage());
        Assert.assertEquals("foo", queriedProfile.getFirstName());
        Assert.assertEquals("bar", queriedProfile.getLastName());
    }

    // For existing profile and user, update some information (not all).
    @Test
    public void testPatchNullProfile() throws Exception {
        postDummyProfile();
        JsonObject updatedProfile = new JsonObject();
        updatedProfile.addProperty(Profile.SEX, (String) null);
        updatedProfile.addProperty(Profile.BIRTH_YEAR, (Long) null);
        updatedProfile.addProperty(Profile.PREFERRED_LANGUAGE, (String) null);

        Response response = RouteTestUtil.buildAuthorizedPatchRequest(token, url, updatedProfile.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(200, res.getStatusLine().getStatusCode());

        String bodyToString = EntityUtils.toString(res.getEntity());
        Profile queriedProfile = gson.fromJson(bodyToString, Profile.class);
        Assert.assertNull(queriedProfile.getSex());
        Assert.assertNull(queriedProfile.getBirthYear());
        Assert.assertNull(queriedProfile.getPreferredLanguage());
    }

    // test where no changes were made to an existing profile for existing user.
    @Test
    public void testNoPatchProfile() throws Exception {
        postDummyProfile();
        JsonObject updatedProfile = createProfileJsonObject(sex.name(),
                birthDayInMonth, birthMonth, birthYear, preferredLanguage, firstName, lastName);

        Response response = RouteTestUtil.buildAuthorizedPatchRequest(token, url, updatedProfile.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(200, res.getStatusLine().getStatusCode());

        String bodyToString = EntityUtils.toString(res.getEntity());
        Profile queriedProfile = gson.fromJson(bodyToString, Profile.class);
        Assert.assertEquals(sex, queriedProfile.getSex());
        Assert.assertEquals(birthDayInMonth, queriedProfile.getBirthDayInMonth());
        Assert.assertEquals(birthMonth, queriedProfile.getBirthMonth());
        Assert.assertEquals(birthYear, queriedProfile.getBirthYear());
        Assert.assertEquals(preferredLanguage, queriedProfile.getPreferredLanguage());
        Assert.assertEquals(firstName, queriedProfile.getFirstName());
        Assert.assertEquals(lastName, queriedProfile.getLastName());
    }

    // tests for trying to patch a user profile that isn't in the database.
    @Test
    public void testPatchProfileDoesntExist() throws Exception {
        profileUserIdsToDelete.add(guid);
        JsonObject updatedProfile = createProfileJsonObject(sex.name(),
                birthDayInMonth, birthMonth, birthYear, preferredLanguage, firstName, lastName);

        Response response = RouteTestUtil.buildAuthorizedPatchRequest(token, url, updatedProfile.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(400, res.getStatusLine().getStatusCode());

        String bodyToString = EntityUtils.toString(res.getEntity());
        Error error = gson.fromJson(bodyToString, Error.class);
        Assert.assertEquals(error.getErrorCode(), ErrorCodes.MISSING_PROFILE);
    }

    // test where pass in empty string instead of profile.
    @Test
    public void testPatchWithMissingBody() throws Exception {
        postDummyProfile();
        JSONObject updatedProfile = new JSONObject();

        Response response = RouteTestUtil.buildAuthorizedPatchRequest(token, url, updatedProfile.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(400, res.getStatusLine().getStatusCode());

        String bodyToString = EntityUtils.toString(res.getEntity());
        Error error = gson.fromJson(bodyToString, Error.class);
        Assert.assertEquals(error.getErrorCode(), ErrorCodes.MISSING_BODY);
    }

    /**
     * makes sure if trying to patch with invalid language throws 400 error.
     */
    @Test
    public void testPatchBadLanguage() throws Exception {
        postDummyProfile();
        JsonObject updatedProfile = createProfileJsonObject(Profile.Sex.MALE.name(),
                birthDayInMonth, birthMonth, birthYear, "not-a-language", firstName, lastName);

        Response response = RouteTestUtil.buildAuthorizedPatchRequest(token, url, updatedProfile.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(400, res.getStatusLine().getStatusCode());
    }

    /**
     * makes sure if trying to patch with invalid sex throws 400 error.
     */
    @Test
    public void testPatchBadGender() throws Exception {
        postDummyProfile();
        JsonObject updatedProfile = createProfileJsonObject("Male", birthDayInMonth, birthMonth, birthYear, null,
                firstName, lastName);

        Response response = RouteTestUtil.buildAuthorizedPatchRequest(token, url, updatedProfile.toString()).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(400, res.getStatusLine().getStatusCode());
    }
}
