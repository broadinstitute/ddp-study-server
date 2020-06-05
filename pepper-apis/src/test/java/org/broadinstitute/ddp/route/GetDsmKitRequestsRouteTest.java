package org.broadinstitute.ddp.route;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.dsm.DsmKitRequest;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GetDsmKitRequestsRouteTest extends DsmRouteTest {
    private static final int NUM_TEST_KIT_REQUESTS = 4;

    private Set<Long> requestKitsIdsToDelete = new HashSet<>();
    private KitType testKitType;

    @Before
    public void setup() {
        TransactionWrapper.withTxn(handle -> {
            DsmKitRequestDao kitDao = handle.attach(DsmKitRequestDao.class);
            //clear any existing requests
            kitDao.findAllKitRequestsForStudy(generatedTestData.getStudyGuid())
                    .forEach(kit -> kitDao.deleteKitRequest(kit.getId()));
            testKitType = handle.attach(KitTypeDao.class).getSalivaKitType();
            updateAltpidForTestUser(null);
            return null;
        });
    }

    @After
    public void breakDown() {
        TransactionWrapper.withTxn(handle -> {
            DsmKitRequestDao dao = handle.attach(DsmKitRequestDao.class);
            requestKitsIdsToDelete.forEach(id -> dao.deleteKitRequest(id));
            updateAltpidForTestUser(null);
            return null;
        });

    }

    @Test
    public void testGetEmptyListOfKits() throws IOException {
        //setup should keep the list empty
        String requestUrl = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.DSM_ALL_KIT_REQUESTS
                .replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid());
        Response res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute();
        Type listType = new TypeToken<List<DsmKitRequest>>() {
        }.getType();
        List<DsmKitRequest> results = new Gson().fromJson(EntityUtils
                .toString(res.returnResponse().getEntity()), listType);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testGetAllStudyKits() throws IOException {
        //create multiple kit requests
        List<DsmKitRequest> testKitRequests = createTestKitRequests();
        addCreatedTestKitRequestsToDeleteList(testKitRequests);
        String requestUrl = RouteTestUtil.getTestingBaseUrl()
                + RouteConstants.API.DSM_ALL_KIT_REQUESTS.replace(RouteConstants.PathParam.STUDY_GUID,
                generatedTestData.getStudyGuid());

        // get test user, alter altpid, run test both ways
        String altpid = "testGetAllStudyKits";
        updateAltpidForTestUser(altpid);
        List<DsmKitRequest> allKitRequests = getAndCheckDsmKitRequests(requestUrl, altpid);
        checkKitListSize(allKitRequests);

        updateAltpidForTestUser(null);
        allKitRequests = getAndCheckDsmKitRequests(requestUrl, generatedTestData.getUserGuid());
        checkKitListSize(allKitRequests);
    }

    private void checkKitListSize(List<DsmKitRequest> allKitRequests) {
        //all ids not null and unique
        assertEquals(NUM_TEST_KIT_REQUESTS, allKitRequests.stream()
                .map(i -> i.getKitRequestId()).filter(i -> i != null).collect(Collectors.toSet()).size());
        assertEquals(NUM_TEST_KIT_REQUESTS, allKitRequests.size());
    }

    protected List<DsmKitRequest> getAndCheckDsmKitRequests(String requestUrl, String expectedParticipantId) throws IOException {
        Response res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute();
        Type listType = new TypeToken<List<DsmKitRequest>>() {
        }.getType();
        String responseBodyString = EntityUtils.toString(res.returnResponse().getEntity());
        JsonElement responseJson = new JsonParser().parse(responseBodyString);
        assertTrue(responseJson.isJsonArray());
        JsonArray jsonArray = responseJson.getAsJsonArray();
        //make sure id is not in there!
        jsonArray.forEach(item -> {
            assertTrue(item.isJsonObject());
            JsonObject jsonItem = item.getAsJsonObject();
            assertFalse(jsonItem.has("id"));
            assertEquals(expectedParticipantId,
                    jsonItem.getAsJsonPrimitive("participantId").getAsString());
            Assert.assertTrue(jsonItem.has("kitType"));
            Assert.assertTrue(jsonItem.has("kitRequestId"));
            Assert.assertTrue(jsonItem.has("needsApproval"));
            boolean actualNeedsApproval = jsonItem.get("needsApproval").getAsBoolean();
            Assert.assertFalse("should be set to false", actualNeedsApproval);
        });
        List<DsmKitRequest> results = new Gson().fromJson(jsonArray, listType);
        assertTrue(results.stream().allMatch(item -> expectedParticipantId
                .equals(item.getParticipantId())));

        assertTrue(results.stream().allMatch(item -> testKitType.getName().equals(item.getKitType())));
        return results;
    }

    private void updateAltpidForTestUser(String altpid) {
        TransactionWrapper.useTxn(handle -> {
            Assert.assertEquals(1, handle.createUpdate("update user set legacy_altpid = :altpid where user_id = :userId")
                    .bind("altpid", altpid)
                    .bind("userId", generatedTestData.getUserId()).execute());
        });
    }

    @Test
    public void testGetKitsAfterGivenKitGuid() throws IOException {
        List<DsmKitRequest> testKitRequests = createTestKitRequests();
        addCreatedTestKitRequestsToDeleteList(testKitRequests);
        Set<String> testKitIds = testKitRequests.stream().map(each -> each.getKitRequestId()).collect(toSet());
        String urlTemplate = RouteTestUtil.getTestingBaseUrl()
                + RouteConstants.API.DSM_KIT_REQUESTS_STARTING_AFTER
                .replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid());


        for (int i = 0; i < testKitRequests.size(); i++) {
            DsmKitRequest currentkit = testKitRequests.get(i);
            String url = urlTemplate.replace(RouteConstants.PathParam.PREVIOUS_LAST_KIT_REQUEST_ID,
                    currentkit.getKitRequestId());

            updateAltpidForTestUser(null);
            checkKitsFromServer(testKitIds, testKitRequests.size() - i - 1, currentkit, url, generatedTestData.getUserGuid());

            String altpid = "testGetKitsAfterGivenKitGuid";
            updateAltpidForTestUser(altpid);
            checkKitsFromServer(testKitIds, testKitRequests.size() - i - 1, currentkit, url, altpid);
        }

    }

    private void checkKitsFromServer(Set<String> testKitIds, int expectedNumBack,
                                     DsmKitRequest currentkit, String url, String expectedParticipantId) throws IOException {
        List<DsmKitRequest> kitsFromServer = getAndCheckDsmKitRequests(url, expectedParticipantId);
        assertEquals(expectedNumBack, kitsFromServer.size());
        assertTrue(kitsFromServer.stream().noneMatch(k -> k.getKitRequestId()
                .equals(currentkit.getKitRequestId())));
        assertTrue(kitsFromServer.stream().map(e -> e.getKitRequestId()).allMatch(id -> testKitIds.contains(id)));
    }

    private List<DsmKitRequest> createTestKitRequests() {
        return TransactionWrapper.withTxn(handle -> {
            DsmKitRequestDao kitDao = handle.attach(DsmKitRequestDao.class);
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            //Creating 4 kit requests for the same user (would be nice for it to be easy to create additional users!
            return IntStream.range(0, NUM_TEST_KIT_REQUESTS)
                    .mapToLong(i -> kitDao.createKitRequest(generatedTestData.getStudyGuid(),
                            generatedTestData.getMailAddress(),
                            jdbiUser.getUserIdByGuid(generatedTestData.getUserGuid()), testKitType)).boxed()
                    .map(kitDao::findKitRequest)
                    .filter(o -> o.isPresent())
                    .map(o -> o.get())
                    .collect(toList());
        });
    }

    private void addCreatedTestKitRequestsToDeleteList(List<DsmKitRequest> dsmKitRequestList) {
        for (DsmKitRequest dsmKitRequest : dsmKitRequestList) {
            requestKitsIdsToDelete.add(dsmKitRequest.getId());
        }
    }
}
