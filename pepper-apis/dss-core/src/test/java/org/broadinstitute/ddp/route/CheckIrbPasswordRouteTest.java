package org.broadinstitute.ddp.route;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class CheckIrbPasswordRouteTest extends IntegrationTestSuite.TestCase {

    private static String TEST_PASSWORD = "this-is-correct-password";
    private static String WRONG_TEST_PASSWORD = "this-is-wrong-password";
    private static TestDataSetupUtil.GeneratedTestData generatedTestData;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() {
        generatedTestData = TransactionWrapper.withTxn(handle -> TestDataSetupUtil.generateBasicUserTestData(handle));
        token = null;
        url = RouteTestUtil.getTestingBaseUrl() + API.CHECK_IRB_PASSWORD;
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiUmbrellaStudy.class).updateIrbPasswordByGuid(TEST_PASSWORD, generatedTestData.getStudyGuid());
                }
        );
    }

    @AfterClass
    public static void teardown() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiUmbrellaStudy.class).updateIrbPasswordByGuid(null, generatedTestData.getStudyGuid());
                }
        );
    }

    @Test
    public void test_CheckIrbPasswordRoute_PasswordOK() throws Exception {
        Assert.assertTrue(getIrbPasswordCheckResult(TEST_PASSWORD).get("result"));
    }

    @Test
    public void test_CheckIrbPasswordRoute_WrongPasswordRejected() throws Exception {
        Assert.assertFalse(getIrbPasswordCheckResult(WRONG_TEST_PASSWORD).get("result"));
    }

    @Test
    public void test_CheckIrbPasswordRoute_OKNoPasswordForStudy() throws Exception {
        TransactionWrapper.useTxn(h -> h.attach(JdbiUmbrellaStudy.class).updateIrbPasswordByGuid(null, generatedTestData.getStudyGuid()));
        Assert.assertTrue(getIrbPasswordCheckResult(TEST_PASSWORD).get("result"));
        Assert.assertTrue(getIrbPasswordCheckResult(WRONG_TEST_PASSWORD).get("result"));
        TransactionWrapper.useTxn(h -> h.attach(JdbiUmbrellaStudy.class)
                .updateIrbPasswordByGuid(TEST_PASSWORD, generatedTestData.getStudyGuid()));
    }

    @Test
    public void test_CheckIrbPasswordRoute_NullOrBlankPasswordRejected() throws Exception {
        Assert.assertFalse(getIrbPasswordCheckResult(null).get("result"));
        Assert.assertFalse(getIrbPasswordCheckResult("").get("result"));
    }

    @Test
    public void test_CheckIrbPasswordRoute_NoSuchStudy() throws Exception {
        String requestUrl = url.replace(RouteConstants.PathParam.STUDY_GUID, "NOSUCHSTUDY");
        HttpResponse res = RouteTestUtil.buildAuthorizedGetRequest(token, requestUrl).execute().returnResponse();
        Assert.assertEquals(404, res.getStatusLine().getStatusCode());
    }

    private Map<String, Boolean> getIrbPasswordCheckResult(String password) throws Exception {
        String requestUrl = url.replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid());
        JsonObject payload = new JsonObject();
        payload.addProperty("password", password);
        Response res = RouteTestUtil.buildAuthorizedPostRequest(token, requestUrl, payload.toString()).execute();
        Map<String, Boolean> irbCheckResult = new Gson().fromJson(
                EntityUtils.toString(res.returnResponse().getEntity()),
                new TypeToken<HashMap<String, Boolean>>() {
                }.getType()
        );
        return irbCheckResult;
    }

}
