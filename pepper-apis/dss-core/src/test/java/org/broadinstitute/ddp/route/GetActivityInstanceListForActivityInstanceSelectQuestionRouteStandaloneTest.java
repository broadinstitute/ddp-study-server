package org.broadinstitute.ddp.route;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.json.ActivityInstanceSelectAnswersResponse;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.ActivityInstanceSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class GetActivityInstanceListForActivityInstanceSelectQuestionRouteStandaloneTest
        extends IntegrationTestSuite.TestCase {

    private static ActivityInstanceDto activityInstanceDto;
    private static Gson gson;

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;
    private static String token;
    private static String urlTemplate;

    private static FormActivityDef activity;

    private static String aiSid1;
    private static String aiSid2;
    private static String aiSid3;

    private static ActivityVersionDto activityOptionVersionDto;
    private static ActivityInstanceDto instanceOption1Dto;

    @BeforeClass
    public static void setup() {
        IntegrationTestSuite.setup(false);
        gson = new Gson();

        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            userGuid = testData.getUserGuid();
            setupActivity(handle);
        });

        String endpoint = RouteConstants.API.ACTIVITY_INSTANCE_SELECT_SUGGESTION
                .replace(RouteConstants.PathParam.USER_GUID, userGuid)
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(RouteConstants.PathParam.STABLE_ID, "{stableId}");
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @Before
    public void refresh() {
        TransactionWrapper.useTxn(handle -> handle.attach(ActivityInstanceDao.class)
                .insertInstance(activity.getActivityId(), userGuid, userGuid, activityInstanceDto.getId()));
    }

    @Test
    public void givenActivityInstanceSelectQuestionWithEmptyActivityCodes_whenRouteIsCalled_thenItReturnsEmptyList() throws Exception {
        HttpResponse response = getHttpResponse(aiSid2);
        ActivityInstanceSelectAnswersResponse activityInstanceSelectAnswersResponse = getResponseBasedSuggestionResponse(response);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(0, activityInstanceSelectAnswersResponse.getResults().size());
    }

    @Test
    public void givenActivityInstanceSelectQuestionWithActivityCodes_whenRouteIsCalled_thenItReturnsOneOption()
            throws Exception {
        HttpResponse response = getHttpResponse(aiSid1);
        ActivityInstanceSelectAnswersResponse activityInstanceSelectAnswersResponse = getResponseBasedSuggestionResponse(response);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(1, activityInstanceSelectAnswersResponse.getResults().size());
        assertEquals(instanceOption1Dto.getGuid(), activityInstanceSelectAnswersResponse.getResults().get(0).getGuid());
        assertEquals("activity to select", activityInstanceSelectAnswersResponse.getResults().get(0).getName());
    }

    @Test
    public void givenActivityInstanceSelectQuestionWithNotExistingActivityCode_whenRouteIsCalled_thenItReturnsEmptyList()
            throws Exception {
        HttpResponse response = getHttpResponse(aiSid3);
        ActivityInstanceSelectAnswersResponse activityInstanceSelectAnswersResponse = getResponseBasedSuggestionResponse(response);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(0, activityInstanceSelectAnswersResponse.getResults().size());
    }

    @Test
    public void givenActivityInstanceSelectQuestionWithActivityCodes_whenRouteIsCalled_thenItReturnsListOfTwo() throws Exception {
        ActivityInstanceDto instanceOption2Dto = TransactionWrapper.withTxn(handle -> handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityOptionVersionDto.getActivityId(), userGuid));
        HttpResponse response = getHttpResponse(aiSid1);
        ActivityInstanceSelectAnswersResponse activityInstanceSelectAnswersResponse = getResponseBasedSuggestionResponse(response);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(2, activityInstanceSelectAnswersResponse.getResults().size());
        assertEquals(instanceOption1Dto.getGuid(), activityInstanceSelectAnswersResponse.getResults().get(0).getGuid());
        assertEquals(instanceOption2Dto.getGuid(), activityInstanceSelectAnswersResponse.getResults().get(1).getGuid());
        assertEquals("activity to select", activityInstanceSelectAnswersResponse.getResults().get(0).getName());
        assertEquals("activity to select #2", activityInstanceSelectAnswersResponse.getResults().get(1).getName());
    }

    private static void setupActivity(Handle handle) {
        long timestamp = Instant.now().toEpochMilli();

        String textSid3 = "FAIL_STABLE_ID" + timestamp;

        aiSid1 = "AI_Q1_" + timestamp;
        aiSid2 = "AI_Q2_" + timestamp;
        aiSid3 = "AI_Q3_" + timestamp;

        var activitySid = "ACTIVITY_" + timestamp;
        var activityOptionSid = "ACTIVITY_OPT_" + timestamp;

        TextQuestionDef textQuestion = TextQuestionDef.builder(TextInputType.TEXT, "TEXT_Q1_" + timestamp, Template.text("t1")).build();
        var activityOption = FormActivityDef.generalFormBuilder(activityOptionSid, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity to select"))
                .addSections(List.of(new FormSectionDef(null, TestUtil.wrapQuestions(textQuestion))))
                .build();
        activityOptionVersionDto = handle.attach(ActivityDao.class)
                .insertActivity(activityOption, RevisionMetadata.now(testData.getUserId(), "add " + activityOptionSid));
        instanceOption1Dto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityOptionVersionDto.getActivityId(), userGuid);


        ActivityInstanceSelectQuestionDef aiQuestion1 = ActivityInstanceSelectQuestionDef.builder(aiSid1, Template.text("d1"))
                .setActivityCodes(List.of(activityOption.getActivityCode())).build();
        ActivityInstanceSelectQuestionDef aiQuestion2 = ActivityInstanceSelectQuestionDef.builder(aiSid2, Template.text("d2"))
                .setActivityCodes(Collections.emptyList()).build();
        ActivityInstanceSelectQuestionDef aiQuestion3 = ActivityInstanceSelectQuestionDef.builder(aiSid3, Template.text("d3"))
                .setActivityCodes(List.of(textSid3)).build();
        FormSectionDef formSectionDef = new FormSectionDef(null,
                TestUtil.wrapQuestions(aiQuestion1, aiQuestion2, aiQuestion3));

        activity = FormActivityDef.generalFormBuilder(activitySid, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + activitySid))
                .addSections(List.of(formSectionDef))
                .build();
        ActivityVersionDto activityVersionDto = handle.attach(ActivityDao.class)
                .insertActivity(activity, RevisionMetadata.now(testData.getUserId(), "add " + activitySid));
        activityInstanceDto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityVersionDto.getActivityId(), userGuid);
    }

    private ActivityInstanceSelectAnswersResponse getResponseBasedSuggestionResponse(HttpResponse response) throws IOException {
        String json = EntityUtils.toString(response.getEntity());
        return gson.fromJson(json, ActivityInstanceSelectAnswersResponse.class);
    }

    private HttpResponse getHttpResponse(String stableId) throws IOException {
        Request request = Request.Get(buildUrl(stableId))
                .addHeader(new BasicHeader("Authorization", "Bearer " + token));
        return request.execute().returnResponse();
    }

    private String buildUrl(String stableId) {
        return urlTemplate.replace("{stableId}", stableId);
    }

}
