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
import org.broadinstitute.ddp.db.dao.AnswerCachedDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.json.DynamicSelectAnswersResponse;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.DynamicSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GetDynamicSelectAnswersBasedOnQuestionsRouteStandaloneTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetDynamicSelectAnswersBasedOnQuestionsRouteStandaloneTest.class);

    private static ActivityVersionDto activityVersionDto;
    private static ActivityInstanceDto activityInstanceDto;
    private static ActivityInstanceDto instanceDto;
    private static String instanceGuid;
    private static Gson gson;

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;
    private static String token;
    private static String urlTemplate;

    private static FormActivityDef activity;

    private static String textSid1;
    private static String textSid2;
    private static String textSid3;
    private static String dynamicSid1;
    private static String dynamicSid2;
    private static String dynamicSid3;

    private static List<String> answerGuidsToDelete;

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

        String endpoint = RouteConstants.API.DYNAMIC_ANSWER_BASED_ON_QUESTION
                .replace(RouteConstants.PathParam.USER_GUID, userGuid)
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(RouteConstants.PathParam.STABLE_ID, "{stableId}");
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + endpoint;

        answerGuidsToDelete = new ArrayList<>();
    }

    @Before
    public void refresh() {
        TransactionWrapper.useTxn(handle -> {
            instanceDto = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activity.getActivityId(), userGuid, userGuid, activityInstanceDto.getId());
            instanceGuid = instanceDto.getGuid();
        });
    }

    @Test
    public void givenDynamicSelectQuestionWithEmptySourceList_whenRouteIsCalled_thenItReturnsEmptyList() throws Exception {
        HttpResponse response = getHttpResponse(dynamicSid2);
        DynamicSelectAnswersResponse dynamicSelectAnswersResponse = getResponseBasedSuggestionResponse(response);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(0, dynamicSelectAnswersResponse.getResults().size());
    }

    @Test
    public void givenDynamicSelectQuestionWithSourceListWithoutAnswers_whenRouteIsCalled_thenItReturnsEmptyList() throws Exception {
        HttpResponse response = getHttpResponse(dynamicSid1);
        DynamicSelectAnswersResponse dynamicSelectAnswersResponse = getResponseBasedSuggestionResponse(response);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(0, dynamicSelectAnswersResponse.getResults().size());
    }

    @Test
    public void givenDynamicSelectQuestionWithUnexistSourceQuestionsList_whenRouteIsCalled_thenItReturnsEmptyList() throws Exception {
        HttpResponse response = getHttpResponse(dynamicSid3);
        DynamicSelectAnswersResponse dynamicSelectAnswersResponse = getResponseBasedSuggestionResponse(response);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(0, dynamicSelectAnswersResponse.getResults().size());
    }

    @Test
    public void givenDynamicSelectQuestionWithEmptySourceAnswers_whenRouteIsCalled_thenItReturnsEmptyList() throws Exception {
        answerGuidsToDelete.add(TransactionWrapper.withTxn(handle ->
                createAnswer(handle, new TextAnswer(null, textSid1, null, ""))));

        HttpResponse response = getHttpResponse(dynamicSid1);
        DynamicSelectAnswersResponse dynamicSelectAnswersResponse = getResponseBasedSuggestionResponse(response);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(0, dynamicSelectAnswersResponse.getResults().size());
    }

    @Test
    public void givenDynamicSelectQuestionWithListThatHaveMatches_whenRouteIsCalled_thenItReturnsList() throws Exception {
        TransactionWrapper.withTxn(handle -> {
            TextAnswer answer1 = new TextAnswer(null, textSid1, null, "answer");
            answerGuidsToDelete.add(createAnswer(handle, answer1));

            TextAnswer answer2 = new TextAnswer(null, textSid2, null, "answer");
            return answerGuidsToDelete.add(createAnswer(handle, answer2));
        });

        HttpResponse response = getHttpResponse(dynamicSid1);
        DynamicSelectAnswersResponse dynamicSelectAnswersResponse = getResponseBasedSuggestionResponse(response);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals(2, dynamicSelectAnswersResponse.getResults().size());
        assertEquals("answer", dynamicSelectAnswersResponse.getResults().get(0).getValue());
        assertEquals("answer", dynamicSelectAnswersResponse.getResults().get(1).getValue());
    }

    @After
    public void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            var answerDao = new AnswerCachedDao(handle);
            for (String answerGuid : answerGuidsToDelete) {
                long id = answerDao.getAnswerSql().findDtoByGuid(answerGuid).get().getId();
                answerDao.deleteAnswer(id);
            }
            answerGuidsToDelete.clear();
            handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(instanceGuid);
        });
    }

    private static void setupActivity(Handle handle) {
        long timestamp = Instant.now().toEpochMilli();

        textSid1 = "TEXT_Q1_" + timestamp;
        textSid2 = "TEXT_Q2_" + timestamp;
        textSid3 = "UNEXIST_STABLE_ID" + timestamp;

        dynamicSid1 = "RESPONSE_BASED_TEXT_Q1_" + timestamp;
        dynamicSid2 = "RESPONSE_BASED_TEXT_Q2_" + timestamp;
        dynamicSid3 = "RESPONSE_BASED_TEXT_Q3_" + timestamp;

        var activitySid = "ACTIVITY_" + timestamp;

        TextQuestionDef textQuestion1 = TextQuestionDef.builder(TextInputType.TEXT, textSid1, Template.text("t1")).build();
        TextQuestionDef textQuestion2 = TextQuestionDef.builder(TextInputType.TEXT, textSid2, Template.text("t2")).build();

        DynamicSelectQuestionDef dynamicQuestion1 = DynamicSelectQuestionDef.builder(dynamicSid1, Template.text("d1"))
                .setSourceQuestions(List.of(textSid1, textSid2))
                .build();

        DynamicSelectQuestionDef dynamicQuestion2 = DynamicSelectQuestionDef.builder(dynamicSid2, Template.text("d2"))
                .setSourceQuestions(Collections.emptyList())
                .build();

        DynamicSelectQuestionDef dynamicQuestion3 = DynamicSelectQuestionDef.builder(dynamicSid3, Template.text("d3"))
                .setSourceQuestions(List.of(textSid3))
                .build();

        FormSectionDef formSectionDef = new FormSectionDef(null, TestUtil.wrapQuestions(
                textQuestion1, textQuestion2, dynamicQuestion1, dynamicQuestion2, dynamicQuestion3
        ));

        activity = FormActivityDef.generalFormBuilder(activitySid, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + activitySid))
                .addSections(List.of(formSectionDef))
                .build();

        activityVersionDto = handle.attach(ActivityDao.class)
                .insertActivity(activity, RevisionMetadata.now(testData.getUserId(), "add " + activitySid));

        activityInstanceDto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityVersionDto.getActivityId(), userGuid);
    }

    private String createAnswer(Handle handle, Answer answer) {
        String guid = new AnswerCachedDao(handle)
                .createAnswer(testData.getUserId(), instanceDto.getId(), answer)
                .getAnswerGuid();
        assertNotNull(guid);
        return guid;
    }

    private DynamicSelectAnswersResponse getResponseBasedSuggestionResponse(HttpResponse response) throws IOException {
        String json = EntityUtils.toString(response.getEntity());
        return gson.fromJson(json, DynamicSelectAnswersResponse.class);
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
