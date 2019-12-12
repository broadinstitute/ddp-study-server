package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.address.SetDefaultMailAddressPayload;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailAddressRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(MailAddressRouteTest.class);
    private static final String TEST_ZIP = "82001";
    private static final String testOLC = "849VCWF8+24";
    private static final OLCPrecision defaultOlcPrecision = OLCPrecision.MEDIUM;

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String activityCode;
    private static String userGuid;
    private static String token;
    private static Gson gson;

    @BeforeClass
    public static void beforeClass() {
        gson = new Gson();
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            userGuid = testData.getTestingUser().getUserGuid();
            setupConsentActivity(handle);
        });
    }

    private static void setupConsentActivity(Handle handle) {
        activityCode = "MAIL_ADDR_ROUTE_CONSENT_ACT_" + Instant.now().toEpochMilli();
        FormActivityDef activity = FormActivityDef.formBuilder(FormType.CONSENT, activityCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + activityCode))
                .build();
        handle.attach(ActivityDao.class).insertActivity(activity,
                RevisionMetadata.now(testData.getTestingUser().getUserId(), "add " + activityCode));
        assertNotNull(activity.getActivityId());
    }

    @Before
    public void deleteAllTestMailingAddress() throws SQLException {
        LOG.info("Delete test mailing address");
        TransactionWrapper.withTxn(handle -> {
            PreparedStatement stmt = handle.getConnection()
                    .prepareStatement("delete from mailing_address where participant_user_id in "
                            + "(SELECT user_id from user where guid=?)");
            stmt.setString(1, userGuid);
            int numRowsDeleted = stmt.executeUpdate();
            return null;
        });

    }

    @Test
    public void testAddGoodAddress() throws Exception {
        MailAddress address = buildValidAddress();
        MailAddress createdAddressFromServer = submitValidAddress(address);
        assertNotNull(createdAddressFromServer);
        checkAddress(address, createdAddressFromServer);
    }

    @Test
    public void testAddressWithMissingStreet() throws IOException {
        //Make sure it we get error in strict mode and gets accepted in non-strict
        MailAddress address = buildValidAddress();
        address.setStreet1("");
        Response responseWithStrict = submitAddressForCreation(address, true);
        HttpResponse strictResponseContents = responseWithStrict.returnResponse();
        ApiError strictError = gson.fromJson(EntityUtils.toString(strictResponseContents.getEntity()), ApiError.class);
        assertEquals(422, strictResponseContents.getStatusLine().getStatusCode());
        assertTrue(strictError.getMessage().contains("street"));

        Response responseWithNotStrict = submitAddressForCreation(address, false);
        HttpResponse notStrictResponseContents = responseWithNotStrict.returnResponse();
        ApiError notStrictError = gson.fromJson(EntityUtils.toString(notStrictResponseContents.getEntity()), ApiError
                .class);
        assertFalse(notStrictError.getMessage() != null && notStrictError.getMessage().contains("street"));
        assertEquals(201, notStrictResponseContents.getStatusLine().getStatusCode());
    }

    private String testSaveAddressForDifferentUSAliases(String countryName) throws IOException {
        MailAddress testAddress = new MailAddress();
        testAddress.setCountry(countryName);
        Response response = submitAddressForCreation(testAddress, false);
        HttpResponse responseContents = response.returnResponse();
        MailAddress fromCreation = gson.fromJson(EntityUtils.toString(responseContents.getEntity()),
                MailAddress
                        .class);
        assertEquals(HttpStatus.SC_CREATED, responseContents.getStatusLine().getStatusCode());
        MailAddress testAddressFromServer = getMailAddress(fromCreation.getGuid());
        assertNotNull(testAddressFromServer);

        /* FIXME - this line will fail since as the code currently stands for cases for countries not in db since
        we insert an address object and return the same one
        without confirming that those values were actually put into the database (we just updated its id and guid).
        Therefore, wherever we use the function `addAddress` (which is in 4 places),
        we’re just giving back the object we asked to put in with slightly more information added instead of what
        we actually put in, which isn’t what the documentation says it does. */
        /*
        FIXME - assertEquals(fromCreation.getCountry(), testAddressFromServer.getCountry());
        */

        assertNull(testAddressFromServer.getState());
        // should have a value since can always save pluscodes
        assertEquals(fromCreation.getPlusCode(), testAddressFromServer.getPlusCode());

        return testAddressFromServer.getCountry();
    }

    @Test
    public void testSaveAddressWithOnlyCountry_UnitedStatesOfAmerica() throws IOException {
        String countryName = "United States of America";
        String returnedCountry = testSaveAddressForDifferentUSAliases(countryName);
        assertNull(returnedCountry);         /* should be null since 'United States of America' is not country in db
         so it can't be saved. */
    }

    @Test
    public void testSaveAddressWithOnlyCountry_US() throws IOException {
        String countryName = "US";
        String returnedCountry = testSaveAddressForDifferentUSAliases(countryName);
        assertNotNull(returnedCountry);
    }

    @Test
    public void testSaveAddressWithOnlyCountry_UnitedStates() throws IOException {
        String countryName = "United States";
        String returnedCountry = testSaveAddressForDifferentUSAliases(countryName);
        assertNull(returnedCountry);         /* should be null since 'US' is not country in db
         so it can't be saved. */
    }

    @Test
    public void testAddressWithTooLongAStreetName() throws IOException {
        //Even if non-strict validation, if it is too long to store, we reject it
        MailAddress address = buildValidAddress();
        address.setStreet1("12345678901234567890123456789012345678901234567890"
                + "1234567890123456789012345678901234567890123456789012");
        Response response = submitAddressForCreation(address, false);
        HttpResponse responseContents = response.returnResponse();
        ApiError error = gson.fromJson(EntityUtils.toString(responseContents.getEntity()), ApiError.class);
        assertEquals(422, responseContents.getStatusLine().getStatusCode());
    }

    @Test
    public void testUpdateBadAddress() throws IOException {
        //if someone puts in a bad address, they should be able to update it to another bad address
        MailAddress initialBadAddress = new MailAddress();
        Response response = submitAddressForCreation(initialBadAddress, false);
        HttpResponse responseContents = response.returnResponse();
        assertEquals(HttpStatus.SC_CREATED, responseContents.getStatusLine().getStatusCode());
        MailAddress initialBadAddressFromServer = gson.fromJson(EntityUtils.toString(responseContents.getEntity()),
                MailAddress.class);
        assertNotNull(initialBadAddressFromServer);
        assertNotNull(initialBadAddressFromServer.getGuid());
        assertEquals(DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS.getCode(),
                initialBadAddress.getValidationStatus());
        //Try first to update strict. Should reject it!
        initialBadAddressFromServer.setPhone("12345");
        Response putResponseForStrict = putMailAddress(initialBadAddressFromServer, true);
        HttpResponse putResponseContentsForStrict = putResponseForStrict.returnResponse();
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, putResponseContentsForStrict.getStatusLine().getStatusCode());

        Response putResponse = putMailAddress(initialBadAddressFromServer, false);
        HttpResponse putResponseContents = putResponse.returnResponse();
        assertEquals(HttpStatus.SC_NO_CONTENT, putResponseContents.getStatusLine().getStatusCode());
        MailAddress updatedBadAddressFromServer = getMailAddress(initialBadAddressFromServer.getGuid());
        checkAddress(initialBadAddressFromServer, updatedBadAddressFromServer);

    }

    @Test
    public void testAddBadAddress() throws IOException {
        MailAddress address = buildValidAddress();
        address.setCountry("BOGUS");
        Response response = submitAddressForCreation(address, true);
        HttpResponse responseContents = response.returnResponse();
        ApiError error = gson.fromJson(EntityUtils.toString(responseContents.getEntity()), ApiError.class);
        assertEquals(422, responseContents.getStatusLine().getStatusCode());
    }

    @Test
    public void testRetrieveAddress() throws Exception {
        MailAddress addressToSave = buildValidAddress();
        MailAddress createdAddressFromServer = submitValidAddress(addressToSave);
        assertNotNull(createdAddressFromServer.getGuid());
        MailAddress retrievedAddress = getMailAddress(createdAddressFromServer.getGuid());
        checkAddress(createdAddressFromServer, retrievedAddress);
    }

    private MailAddress getMailAddress(String mailGuid) throws IOException {
        Response res = RouteTestUtil.buildAuthorizedGetRequest(token, buildAddressUrl(mailGuid)).execute();
        HttpResponse httpResponse = res.returnResponse();
        assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        LOG.info("Returned body : {}", EntityUtils.toString(httpResponse.getEntity()));
        MailAddress retrievedAddress = gson.fromJson(EntityUtils.toString(httpResponse.getEntity()), MailAddress.class);
        assertNotNull(retrievedAddress);
        return retrievedAddress;
    }

    private MailAddress getTempMailAddress(String instanceGuid, int expectedHttpStatus) throws IOException {
        String tempAddressCreationUrl = buildTempAddressUrl(instanceGuid);
        Response res = RouteTestUtil.buildAuthorizedGetRequest(token, tempAddressCreationUrl).execute();
        HttpResponse httpResponse = res.returnResponse();

        int statusCode = httpResponse.getStatusLine().getStatusCode();
        assertEquals(expectedHttpStatus, statusCode);
        LOG.info("Returned body : {}", EntityUtils.toString(httpResponse.getEntity()));
        String entity = EntityUtils.toString(httpResponse.getEntity());
        if (statusCode == HttpStatus.SC_OK) {
            MailAddress retrievedAddress = gson.fromJson(entity, MailAddress.class);
            assertNotNull(retrievedAddress);
            return retrievedAddress;
        } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
            ApiError error = gson.fromJson(entity, ApiError.class);
            Assert.assertEquals(error.getCode(), ErrorCodes.MAIL_ADDRESS_NOT_FOUND);
            Assert.assertEquals(error.getMessage(),
                    "Could not find temporary address for instance guid: " + instanceGuid);
            return null;
        } else {
            throw new DDPException("Status code did not match an expected value for route: " + statusCode);
        }
    }

    private String buildTempAddressUrl(String instanceGuid) {
        return RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.PARTICIPANT_TEMP_ADDRESS
                .replace(RouteConstants.PathParam.USER_GUID, userGuid).replace(RouteConstants.PathParam
                        .INSTANCE_GUID, instanceGuid);
    }

    private List<MailAddress> getAllAddresesForTestParticipant() throws IOException {
        Response res = RouteTestUtil.buildAuthorizedGetRequest(token, buildAddressUrlForTestUser(true)).execute();
        HttpResponse httpResponse = res.returnResponse();
        assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        LOG.info("Returned body : {}", EntityUtils.toString(httpResponse.getEntity()));
        Type mailAddressListType = new TypeToken<List<MailAddress>>() {
        }.getType();
        String body = EntityUtils.toString(httpResponse.getEntity());
        List<MailAddress> retrievedAddresses = gson.fromJson(body,
                mailAddressListType);
        return retrievedAddresses;
    }

    @Test
    public void testUpdateAddress() throws IOException {
        MailAddress addressToSave = buildValidAddress();
        MailAddress createdAddressFromServer = submitValidAddress(addressToSave);
        //change all fields and see if changes stuck on server
        createdAddressFromServer.setName("Another name");
        createdAddressFromServer.setStreet1("1313 Boulevard de Maisonneuve O");
        createdAddressFromServer.setStreet2("Eggspectation");
        createdAddressFromServer.setCity("Montreal");
        createdAddressFromServer.setState("QC");
        createdAddressFromServer.setCountry("CA");
        createdAddressFromServer.setZip("H3G 2R9");
        createdAddressFromServer.setPhone("777-222-3333");
        createdAddressFromServer.setPlusCode("87Q8FCXF+G5");
        createdAddressFromServer.setDefault(true);
        Response putResponse = putMailAddress(createdAddressFromServer, true);
        assertEquals(204, putResponse.returnResponse().getStatusLine().getStatusCode());

        MailAddress retrievedUpdatedAddress = getMailAddress(createdAddressFromServer.getGuid());
        checkAddress(createdAddressFromServer, retrievedUpdatedAddress);

    }

    @Test
    public void testUpdateNonExistentAdresss() throws IOException {
        MailAddress addressWithUpdates = buildValidAddress();
        addressWithUpdates.setGuid("NOWAYTHISEXISTS");
        Response putResponse = putMailAddress(addressWithUpdates, true);
        assertEquals(404, putResponse.returnResponse().getStatusLine().getStatusCode());
    }

    private Response putMailAddress(MailAddress address, boolean strict) throws IOException {
        String addressUrl = buildAddressUrl(address.getGuid());
        String strictQueryParam = strict ? "" : "?strict=false";

        return RouteTestUtil.buildAuthorizedPutRequest(token, addressUrl + strictQueryParam,
                gson.toJson(address)).execute();
    }

    @Test
    public void testGetAllAddresses() throws IOException, InterruptedException {
        MailAddress addressToSave = buildValidAddress();
        MailAddress createdAddressFromServer1 = submitValidAddress(addressToSave);
        MailAddress createdAddressFromServer2 = submitValidAddress(addressToSave);
        MailAddress createdAddressFromServer3 = submitValidAddress(addressToSave);

        List<MailAddress> allAddressesForUser = getAllAddresesForTestParticipant();
        assertTrue(allAddressesForUser.size() >= 3);
        checkAddress(createdAddressFromServer1, allAddressesForUser.get(allAddressesForUser.size() - 3));
        checkAddress(createdAddressFromServer2, allAddressesForUser.get(allAddressesForUser.size() - 2));
        checkAddress(createdAddressFromServer3, allAddressesForUser.get(allAddressesForUser.size() - 1));

    }

    @Test
    public void testAndSetDefaultAddress() throws IOException, InterruptedException {
        String defaultAddressUrl = buildDefaultAddressUrlForTestUser();
        Response resDefault = RouteTestUtil.buildAuthorizedGetRequest(token, defaultAddressUrl).execute();
        assertEquals(HttpStatus.SC_NOT_FOUND, resDefault.returnResponse().getStatusLine().getStatusCode());

        MailAddress addressToSave = buildValidAddress();

        MailAddress createdAddressFromServer1 = submitValidAddress(addressToSave);
        assertFalse(createdAddressFromServer1.isDefault());
        MailAddress createdAddressFromServer2 = submitValidAddress(addressToSave);
        assertFalse(createdAddressFromServer2.isDefault());

        SetDefaultMailAddressPayload requestBody =
                new SetDefaultMailAddressPayload(createdAddressFromServer1.getGuid());

        Response res = RouteTestUtil.buildAuthorizedPostRequest(token, defaultAddressUrl,
                gson.toJson(requestBody)).execute();
        assertEquals(HttpStatus.SC_NO_CONTENT, res.returnResponse().getStatusLine().getStatusCode());

        MailAddress address1FromServerAfterSettingAsDefault = getMailAddress(createdAddressFromServer1.getGuid());
        assertTrue(address1FromServerAfterSettingAsDefault.isDefault());

        Response res4 = RouteTestUtil.buildAuthorizedGetRequest(token, defaultAddressUrl).execute();
        HttpResponse getResponseFromDefaultUrl = res4.returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponseFromDefaultUrl.getStatusLine().getStatusCode());
        checkAddress(address1FromServerAfterSettingAsDefault, gson.fromJson(
                EntityUtils.toString(getResponseFromDefaultUrl.getEntity()), MailAddress.class));

        SetDefaultMailAddressPayload requestBody2 =
                new SetDefaultMailAddressPayload(createdAddressFromServer2.getGuid());
        Response res2 = RouteTestUtil.buildAuthorizedPostRequest(
                token, defaultAddressUrl, gson.toJson(requestBody2)).execute();
        assertEquals(HttpStatus.SC_NO_CONTENT, res2.returnResponse().getStatusLine().getStatusCode());

        MailAddress address2FromServerAfterSettingAsDefault = getMailAddress(createdAddressFromServer2.getGuid());
        assertTrue(address2FromServerAfterSettingAsDefault.isDefault());

        MailAddress address1AfterSettingOtherDefault = getMailAddress(createdAddressFromServer1.getGuid());
        assertFalse(address1AfterSettingOtherDefault.isDefault());

    }

    @Test
    public void testSetDefaultAddressOnNonExisting() throws IOException {
        SetDefaultMailAddressPayload requestBody = new SetDefaultMailAddressPayload("DOESNOTEXIST");
        String url = buildDefaultAddressUrlForTestUser();
        Response res = RouteTestUtil.buildAuthorizedPostRequest(token, url, gson.toJson(requestBody)).execute();
        assertEquals(HttpStatus.SC_NOT_FOUND, res.returnResponse().getStatusLine().getStatusCode());

    }

    @Test
    public void testDeleteAddress() throws IOException {
        MailAddress addressToSave = buildValidAddress();
        MailAddress createdAddressFromServer1 = submitValidAddress(addressToSave);
        MailAddress retrievedFromServer = getMailAddress(createdAddressFromServer1.getGuid());
        assertNotNull(retrievedFromServer);
        assertNotNull(retrievedFromServer.getGuid());
        deleteAddressFromServer(retrievedFromServer.getGuid());

        Response resAfterDeletion = RouteTestUtil.buildAuthorizedGetRequest(token,
                buildAddressUrl(retrievedFromServer.getGuid())).execute();
        assertEquals(HttpStatus.SC_NOT_FOUND, resAfterDeletion.returnResponse().getStatusLine().getStatusCode());

    }

    @Test
    public void testCreatePartialMailingAddress() throws IOException {
        //create test activity instance
        //use activity instance guid to create an empty temp address
        //see that we can retrieve it
        //add some data to the temp address and save it
        //see we can retrieve it
        MailAddress emptyAddress = new MailAddress();
        String activityInstanceGuid = createTestActivityInstance();
        saveRetrieveAndCheckTempAddress(emptyAddress, activityInstanceGuid, HttpStatus.SC_CREATED);


        MailAddress addressWithBlanks = new MailAddress();
        addressWithBlanks.setName("");
        addressWithBlanks.setStreet1("");
        addressWithBlanks.setStreet2("");
        addressWithBlanks.setZip("");
        addressWithBlanks.setPhone("");

        saveRetrieveAndCheckTempAddress(addressWithBlanks, activityInstanceGuid, HttpStatus.SC_NO_CONTENT);

        MailAddress addressWithSomeData = emptyAddress;
        addressWithSomeData.setStreet1("415 Main Street");
        saveRetrieveAndCheckTempAddress(addressWithSomeData, activityInstanceGuid, HttpStatus.SC_NO_CONTENT);

        addressWithSomeData.setStreet2("attn: Chucky");
        saveRetrieveAndCheckTempAddress(addressWithSomeData, activityInstanceGuid, HttpStatus.SC_NO_CONTENT);

        addressWithSomeData.setCity("Cambridge");
        saveRetrieveAndCheckTempAddress(addressWithSomeData, activityInstanceGuid, HttpStatus.SC_NO_CONTENT);

        //Can't save the state or province without the country. This is OK.
        addressWithSomeData.setCountry("US");
        saveRetrieveAndCheckTempAddress(addressWithSomeData, activityInstanceGuid, HttpStatus.SC_NO_CONTENT);

        addressWithSomeData.setState("MA");
        saveRetrieveAndCheckTempAddress(addressWithSomeData, activityInstanceGuid, HttpStatus.SC_NO_CONTENT);

        addressWithSomeData.setZip("02142");
        saveRetrieveAndCheckTempAddress(addressWithSomeData, activityInstanceGuid, HttpStatus.SC_NO_CONTENT);

        addressWithSomeData.setDefault(true);
        saveRetrieveAndCheckTempAddress(addressWithSomeData, activityInstanceGuid, HttpStatus.SC_NO_CONTENT);

        addressWithSomeData.setDefault(false);
        saveRetrieveAndCheckTempAddress(addressWithSomeData, activityInstanceGuid, HttpStatus.SC_NO_CONTENT);

        Response res = deleteTempAddress(activityInstanceGuid);
        assertEquals(HttpStatus.SC_NO_CONTENT, res.returnResponse().getStatusLine().getStatusCode());

        String tempAddressCreationUrl = buildTempAddressUrl(activityInstanceGuid);
        Response getResponseAfterDelete = RouteTestUtil.buildAuthorizedGetRequest(token, tempAddressCreationUrl)
                .execute();
        assertEquals(HttpStatus.SC_NOT_FOUND, getResponseAfterDelete.returnResponse().getStatusLine().getStatusCode());

        TransactionWrapper.useTxn(handle ->
                handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(activityInstanceGuid));
    }

    @Test
    public void testGetTempAddressFailsCorrectly() throws IOException {
        String activityInstanceGuid = createTestActivityInstance();
        getTempMailAddress(activityInstanceGuid, HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void testPutTempAddressFailsAsExpected_givenFakeInstanceGuid_returnsNotFound() throws IOException {
        String activityInstanceGuid = "thisIsSuperFake";
        Response res = putTempAddress(new MailAddress(), activityInstanceGuid);
        HttpResponse responseContents = res.returnResponse();
        assertEquals(HttpStatus.SC_NOT_FOUND, responseContents.getStatusLine().getStatusCode());
    }

    protected void saveRetrieveAndCheckTempAddress(MailAddress emptyAddress,
                                                   String activityInstanceGuid,
                                                   int expectedHttpStatus) throws
            IOException {
        Response res = putTempAddress(emptyAddress, activityInstanceGuid);
        HttpResponse responseContents = res.returnResponse();
        assertEquals(expectedHttpStatus, responseContents.getStatusLine().getStatusCode());

        MailAddress emptyAddressFromServer = getTempMailAddress(activityInstanceGuid, HttpStatus.SC_OK);
        checkTempAddress(emptyAddress, emptyAddressFromServer);
    }

    protected Response putTempAddress(MailAddress emptyAddress, String activityInstanceGuid) throws IOException {
        String tempAddressCreationUrl = buildTempAddressUrl(activityInstanceGuid);

        return RouteTestUtil.buildAuthorizedPutRequest(token,
                tempAddressCreationUrl, gson.toJson(emptyAddress)).execute();
    }

    private Response deleteTempAddress(String activityInstanceGuid) throws IOException {
        return RouteTestUtil.buildAuthorizedDeleteRequest(token, buildTempAddressUrl(activityInstanceGuid)).execute();
    }

    private String createTestActivityInstance() {
        return TransactionWrapper.withTxn(handle -> {
            long activityId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(testData.getStudyId(), activityCode).get();
            String instanceGuid = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activityId, userGuid, userGuid, InstanceStatusType.CREATED, false)
                    .getGuid();
            assertNotNull(instanceGuid);
            return instanceGuid;
        });
    }

    private void deleteAddressFromServer(String guid) throws IOException {
        Response res = RouteTestUtil.buildAuthorizedDeleteRequest(token, buildAddressUrl(guid)).execute();
    }

    private String buildAddressUrlForTestUser(boolean useStrictValidation) {
        return RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.PARTICIPANT_ADDRESS
                .replace(RouteConstants.PathParam.USER_GUID, userGuid) + (useStrictValidation ? ""
                : "?strict=false");
    }

    private String buildDefaultAddressUrlForTestUser() {
        return RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.PARTICIPANT_ADDRESS
                .replace(RouteConstants.PathParam.USER_GUID, userGuid) + "/default";
    }

    private String buildAddressUrl(String addressGuid) {
        return RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.ADDRESS
                .replace(RouteConstants.PathParam.ADDRESS_GUID, addressGuid)
                .replace(RouteConstants.PathParam.USER_GUID, userGuid);
    }

    protected MailAddress submitValidAddress(MailAddress address) throws IOException {
        Response res = submitAddressForCreation(address, true);
        HttpResponse responseContents = res.returnResponse();
        assertEquals(201, responseContents.getStatusLine().getStatusCode());

        MailAddress unmarshalledMailAddressed = gson.fromJson(
                EntityUtils.toString(responseContents.getEntity()), MailAddress.class);
        assertNotNull(unmarshalledMailAddressed);
        assertNotNull(unmarshalledMailAddressed.getGuid());
        Header[] locationHeaders = responseContents.getHeaders(HttpHeaders.LOCATION);
        assertEquals(1, locationHeaders.length);
        String locationHeaderValue = locationHeaders[0].getValue();
        URL locationUrl = new URL(locationHeaderValue);
        assertTrue(locationUrl.getPath().endsWith("/" + unmarshalledMailAddressed.getGuid()));
        assertEquals(buildAddressUrl(unmarshalledMailAddressed.getGuid()), locationUrl.toString());
        return unmarshalledMailAddressed;
    }

    private Response submitAddressForCreation(MailAddress address, boolean useStrictValidation) throws IOException {
        return RouteTestUtil.buildAuthorizedPostRequest(token,
                buildAddressUrlForTestUser(useStrictValidation), gson.toJson(address)).execute();
    }

    protected void checkAddress(MailAddress original, MailAddress fromServer) {
        assertEquals(original.getName(), fromServer.getName());
        assertEquals(original.getCity(), fromServer.getCity());
        assertEquals(original.getCountry(), fromServer.getCountry());
        assertEquals(original.getPhone(), fromServer.getPhone());
        assertEquals(original.getState(), fromServer.getState());
        assertEquals(original.getStreet1(), fromServer.getStreet1());
        assertEquals(original.getStreet2(), fromServer.getStreet2());
        assertEquals(original.getZip(), fromServer.getZip());
        assertEquals(original.getPlusCode(), fromServer.getPlusCode());
        assertNull(fromServer.getId());
        assertEquals(original.isDefault(), fromServer.isDefault());
        assertNotNull(fromServer.getGuid());
    }

    protected void checkTempAddress(MailAddress original, MailAddress fromServer) {
        assertEquals(original.getName(), fromServer.getName());
        assertEquals(original.getCity(), fromServer.getCity());
        assertEquals(original.getCountry(), fromServer.getCountry());
        assertEquals(original.getPhone(), fromServer.getPhone());
        assertEquals(original.getState(), fromServer.getState());
        assertEquals(original.getStreet1(), fromServer.getStreet1());
        assertEquals(original.getStreet2(), fromServer.getStreet2());
        assertEquals(original.getZip(), fromServer.getZip());
        assertEquals(original.getPlusCode(), fromServer.getPlusCode());
        assertNull(fromServer.getId());
        assertEquals(original.isDefault(), fromServer.isDefault());
        //Just checking guid is not relevant for temp adddresses
    }

    @Test
    public void testRetrieveMissingAddress() throws Exception {
        Response res = RouteTestUtil.buildAuthorizedGetRequest(
                token, buildAddressUrl("BOGUSGUID")).execute();
        HttpResponse responseObject = res.returnResponse();
        System.out.println("The response string is" + EntityUtils.toString(responseObject.getEntity()));
        assertEquals(404, responseObject.getStatusLine().getStatusCode());

    }

    private MailAddress buildValidAddress() {
        return new MailAddress("Gomez Adams", "3348 Ridge Road", "",
                "Cheyenne", "WY", "US", TEST_ZIP, "617-666-4444", "85HQ46WJ+WF", "fictional",
                DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS, false);
    }

}
