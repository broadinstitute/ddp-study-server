package org.broadinstitute.ddp.route;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GetShouldDisplayLanguageChangePopupRouteTest extends IntegrationTestSuite.TestCase {

    private static Gson gson;
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String url;

    @Before
    public void setup() {
        gson = new Gson();
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        String endpoint = RouteConstants.API.DISPLAY_LANGUAGE_POPUP
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private void addSetting(boolean settingValue) {
        TransactionWrapper.useTxn(handle -> {
            StudyDao dao = handle.attach(StudyDao.class);
            dao.addSettings(testData.getStudyId(), null, null, false, null, false, settingValue);
        });
    }

    private Boolean getSettingValue() throws IOException {
        HttpResponse httpResponse = Request.Get(url).execute().returnResponse();
        assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(httpResponse.getEntity());
        return gson.fromJson(json, Boolean.TYPE);
    }

    @Test
    public void defaultSettingTest() throws IOException {
        assertFalse(getSettingValue());
    }

    @Test
    public void trueSettingTest() throws IOException {
        addSetting(true);
        assertTrue(getSettingValue());
    }

    @Test
    public void falseSettingTest() throws IOException {
        addSetting(false);
        assertFalse(getSettingValue());
    }
}
