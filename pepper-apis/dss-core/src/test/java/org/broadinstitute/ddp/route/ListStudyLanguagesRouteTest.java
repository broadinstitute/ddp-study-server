package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.model.study.StudyLanguage;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ListStudyLanguagesRouteTest extends IntegrationTestSuite.TestCase {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String url;
    private static long[] idsToDelete = {-1, -1};
    private static Gson gson;
    private static Long englishLangCodeId;
    private static Long frenchLangCodeId;

    @BeforeClass
    public static void setup() {
        gson = new Gson();
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
        });

        String endpoint = RouteConstants.API.STUDY_LANGUAGES
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @AfterClass
    public static void teardown() {
        deleteTestData();
    }


    private static void insertTestData() {
        TransactionWrapper.useTxn(
                handle -> {
                        StudyLanguageDao dao = handle.attach(StudyLanguageDao.class);
                        englishLangCodeId = LanguageStore.getDefault().getId();
                        idsToDelete[0] = dao.insert(testData.getStudyId(), englishLangCodeId, "English");
                        frenchLangCodeId = LanguageStore.get("fr").getId();
                        idsToDelete[1] = dao.insert(testData.getStudyId(), frenchLangCodeId, "French");
                        dao.setAsDefaultLanguage(testData.getStudyId(), englishLangCodeId);
                }
        );
    }

    private static void deleteTestData() {
        TransactionWrapper.useTxn(
                handle -> {
                    StudyLanguageDao dao = handle.attach(StudyLanguageDao.class);
                    dao.deleteStudyLanguageById(idsToDelete[0]);
                    dao.deleteStudyLanguageById(idsToDelete[1]);
                }
        );
    }

    @Test
    public void testStudyLanguages() throws IOException {
        //Make sure we initially get an empty list
        HttpResponse httpResponse = Request.Get(url).execute().returnResponse();
        assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(httpResponse.getEntity());
        Type listType = new TypeToken<List<StudyLanguage>>() {
        }.getType();
        List<StudyLanguage> languageList = gson.fromJson(json, listType);
        assertEquals(0, languageList.size());

        insertTestData();
        httpResponse = Request.Get(url).execute().returnResponse();
        assertEquals(200, httpResponse.getStatusLine().getStatusCode());
        json = EntityUtils.toString(httpResponse.getEntity());
        languageList = gson.fromJson(json, listType);
        assertEquals(2, languageList.size());

        StudyLanguage lang = languageList.get(0);
        assertEquals("English", lang.getDisplayName());
        assertEquals(0, lang.getStudyId());
        assertEquals(0, lang.getLanguageId());
        assertTrue(lang.isDefault());
        assertEquals("en", lang.getLanguageCode());
        lang = languageList.get(1);
        assertEquals("French", lang.getDisplayName());
        assertFalse(lang.isDefault());
        assertEquals("fr", lang.getLanguageCode());

    }
}
