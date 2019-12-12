package org.broadinstitute.ddp.route;

import java.time.Instant;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.json.mailinglist.GetMailingListResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GetDsmMailingListRouteTest extends DsmRouteTest {

    private static String TEST_MAIL_ADDRESS = "mailing-test+" + System.currentTimeMillis() + "@datadonationplatform"
            + ".org";
    private static long TEST_DATE_CREATED = 1537371572000L;
    private static long studyMailingListId;

    @Before
    public void addEntriesToMailingList() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiMailingList.class).insertByStudyGuidIfNotStoredAlready(
                    "foo",
                    "bar",
                    TEST_MAIL_ADDRESS,
                    generatedTestData.getStudyGuid(),
                    null,
                    Instant.now().toEpochMilli()
            );
            handle.attach(JdbiMailingList.class).updateDateCreatedByEmailAndStudy(
                    TEST_DATE_CREATED,
                    TEST_MAIL_ADDRESS,
                    generatedTestData.getTestingStudy().getId()
            );
        });
    }

    @After
    public void removeEntriesFromMailingList() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiMailingList.class).deleteById(studyMailingListId);
        });
    }

    @Test
    public void test_GetMailingListRoute_OK() throws Exception {
        String requestUrl = RouteTestUtil.getTestingBaseUrl()
                + RouteConstants.API.GET_STUDY_MAILING_LIST
                .replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid());

        Response res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute();
        GetMailingListResponse[] mailingList = new Gson().fromJson(
                EntityUtils.toString(res.returnResponse().getEntity()),
                GetMailingListResponse[].class
        );

        Assert.assertEquals(TEST_MAIL_ADDRESS, mailingList[0].getEmail());
        Assert.assertEquals(TEST_DATE_CREATED / 1000L, mailingList[0].getDateCreated());

    }

    @Test
    public void test_GetMailingListRoute_NoSuchStudy() throws Exception {
        String requestUrl = RouteTestUtil.getTestingBaseUrl()
                + RouteConstants.API.GET_STUDY_MAILING_LIST
                .replace(RouteConstants.PathParam.STUDY_GUID, "NOSUCHSTUDY");

        HttpResponse res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute().returnResponse();
        Assert.assertEquals(404, res.getStatusLine().getStatusCode());
    }

}
