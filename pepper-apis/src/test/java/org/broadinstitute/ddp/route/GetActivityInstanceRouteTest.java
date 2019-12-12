package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiAnswer;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiTextAnswer;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.SectionIcon;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.FormSectionState;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetActivityInstanceRouteTest extends IntegrationTestSuite.TestCase {

    public static final String TEXT_QUESTION_STABLE_ID = "TEXT_Q";
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static FormActivityDef activity;
    private static ActivityInstanceDto instanceDto;
    private static String userGuid;
    private static String token;
    private static String url;
    private static Gson gson;
    private static Template placeholderTemplate;
    private static String essayQuestionStableId;
    private static String activityCode;
    private static QuestionDto answeredQuestionDto;

    @BeforeClass
    public static void setup() throws Exception {
        gson = new Gson();
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            userGuid = testData.getUserGuid();
            setupActivityAndInstance(handle);
        });
        String endpoint = API.USER_ACTIVITIES_INSTANCE
                .replace(PathParam.USER_GUID, userGuid)
                .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(PathParam.INSTANCE_GUID, "{instanceGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static void setupActivityAndInstance(Handle handle) throws Exception {
        placeholderTemplate = newTemplate();
        DateQuestionDef d1 = DateQuestionDef.builder(DateRenderMode.TEXT, "DATE_TEXT_Q", newTemplate())
                .addFields(DateFieldType.MONTH)
                .addValidation(new RequiredRuleDef(newTemplate()))
                .setHideNumber(true)
                .setPlaceholderTemplate(Template.text("select a date"))
                .build();
        DateQuestionDef d2 = DateQuestionDef.builder(DateRenderMode.SINGLE_TEXT, "DATE_SINGLE_TEXT_Q", newTemplate())
                .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                .addValidation(new DateFieldRequiredRuleDef(RuleType.YEAR_REQUIRED, newTemplate()))
                .addValidation(new DateFieldRequiredRuleDef(RuleType.MONTH_REQUIRED, newTemplate()))
                .addValidation(new DateFieldRequiredRuleDef(RuleType.DAY_REQUIRED, newTemplate()))
                .build();
        DateQuestionDef d3 = DateQuestionDef.builder(DateRenderMode.PICKLIST, "DATE_PICKLIST_Q", newTemplate())
                .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                .addValidation(new DateRangeRuleDef(newTemplate("Pi Day to today"), LocalDate.of(2018, 3, 14), null, true))
                .build();
        FormSectionDef dateSection = new FormSectionDef(null, TestUtil.wrapQuestions(d1, d2, d3));

        TextQuestionDef txt1 = TextQuestionDef.builder(TextInputType.TEXT, TEXT_QUESTION_STABLE_ID, newTemplate())
                .setPlaceholderTemplate(placeholderTemplate)
                .addValidation(new LengthRuleDef(newTemplate(), 5, 300))
                .build();
        TextQuestionDef txt2 = TextQuestionDef.builder(TextInputType.TEXT, "TEXT_DRUG", newTemplate())
                .setSuggestionType(SuggestionType.DRUG)
                .build();
        FormSectionDef textSection = new FormSectionDef(null, TestUtil.wrapQuestions(txt1, txt2));

        PicklistQuestionDef p1 = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.LIST, "PL_NO_OTHER", newTemplate())
                .addOption(new PicklistOptionDef("PL_NO_OTHER_OPT_1", newTemplate()))
                .build();
        PicklistQuestionDef p2 = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.LIST, "PL_YES_OTHER", newTemplate())
                .addOption(new PicklistOptionDef("PL_YES_OTHER_OPT_OTHER", newTemplate("Other"), newTemplate("Details here")))
                .build();
        PicklistQuestionDef p3 = PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, "PL_GROUPS", newTemplate())
                .addGroup(new PicklistGroupDef("G1", newTemplate(), Arrays.asList(
                        new PicklistOptionDef("G1_OPT1", newTemplate()))))
                .addGroup(new PicklistGroupDef("G2", newTemplate(), Arrays.asList(
                        new PicklistOptionDef("G2_OPT1", newTemplate()),
                        new PicklistOptionDef("G2_OPT2", newTemplate()))))
                .addOption(new PicklistOptionDef("OPT1", newTemplate()))
                .build();
        FormSectionDef plistSection = new FormSectionDef(null, TestUtil.wrapQuestions(p1, p2, p3));

        essayQuestionStableId = "PATCH_TEXT_Q2";
        TextQuestionDef t2 = TextQuestionDef.builder(
                TextInputType.ESSAY,
                essayQuestionStableId,
                newTemplate()
        ).build();
        FormSectionDef textSection2 = new FormSectionDef(null, TestUtil.wrapQuestions(t2));

        AgreementQuestionDef a1 = new AgreementQuestionDef("AGREEMENT_Q",
                false,
                newTemplate("I agree with the preceding text"),
                newTemplate("info header"),
                newTemplate("info footer"),
                Arrays.asList(new RequiredRuleDef(newTemplate())),
                true);
        FormSectionDef agreementSection = new FormSectionDef(null, TestUtil.wrapQuestions(a1));

        Template contentTitle = new Template(TemplateType.HTML, null, "<p>hello title</p>");
        Template contentBody = new Template(TemplateType.HTML, null, "<p>hello body</p>");
        ContentBlockDef contentDef = new ContentBlockDef(contentTitle, contentBody);
        FormSectionDef contentSection = new FormSectionDef(null, Collections.singletonList(contentDef));

        Template nameTmpl = Template.text("icon section");
        SectionIcon icon1 = new SectionIcon(FormSectionState.COMPLETE, 100, 100);
        icon1.putSource("1x", new URL("https://dev.ddp.org/icon1_1x.png"));
        SectionIcon icon2 = new SectionIcon(FormSectionState.INCOMPLETE, 200, 200);
        icon2.putSource("1x", new URL("https://dev.ddp.org/icon2_1x.png"));
        icon2.putSource("2x", new URL("https://dev.ddp.org/icon2_2x.png"));
        FormSectionDef iconSection = new FormSectionDef(null, nameTmpl, Arrays.asList(icon1, icon2), Collections.emptyList());

        activityCode = "ACT_ROUTE_ACT" + Instant.now().toEpochMilli();
        activity = FormActivityDef.generalFormBuilder(activityCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + activityCode))
                .addSections(Arrays.asList(dateSection, textSection, plistSection, textSection2, agreementSection, contentSection))
                .addSection(iconSection)
                .build();
        handle.attach(ActivityDao.class).insertActivity(activity, RevisionMetadata.now(testData.getUserId(), "add " + activityCode));
        assertNotNull(activity.getActivityId());
        instanceDto = handle.attach(ActivityInstanceDao.class).insertInstance(activity.getActivityId(), userGuid);

        long answerId = handle.attach(JdbiAnswer.class).insertBaseAnswer(txt1.getQuestionId(), userGuid, instanceDto.getId());
        handle.attach(JdbiTextAnswer.class).insert(answerId, "valid answer");
        answeredQuestionDto = handle.attach(JdbiQuestion.class).getQuestionDtoById(txt1.getQuestionId()).get();
    }

    private static Template newTemplate() {
        return newTemplate("template " + Instant.now().toEpochMilli());
    }

    private static Template newTemplate(String message) {
        return new Template(TemplateType.TEXT, null, message);
    }

    @AfterClass
    public static void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiAnswer.class).deleteAllAnswersForQuestion(instanceDto, answeredQuestionDto);
            handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(instanceDto.getGuid());
        });
    }

    @Test
    public void testGet_activityNotFound() throws Exception {
        String testUrl = url.replace("{instanceGuid}", "not-an-activity-guid");
        Request request = RouteTestUtil.buildAuthorizedGetRequest(token, testUrl);
        HttpResponse response = request.execute().returnResponse();
        assertEquals(404, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        ApiError resp = gson.fromJson(json, ApiError.class);
        assertEquals(ErrorCodes.ACTIVITY_NOT_FOUND, resp.getCode());
    }

    @Test
    public void testGet_formActivity() throws Exception {
        String testUrl = url.replace("{instanceGuid}", instanceDto.getGuid());
        Request request = RouteTestUtil.buildAuthorizedGetRequest(token, testUrl);
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        ActivityInstance activity = gson.fromJson(json, ActivityInstance.class);
        assertEquals(ActivityType.FORMS, activity.getActivityType());
        assertEquals(instanceDto.getGuid(), activity.getGuid());
        assertEquals(activityCode, activity.getActivityCode());
    }

    @Test
    public void testGet_tempUser_unauthorized() throws Exception {
        //update test user as temp user
        TransactionWrapper.useTxn(handle -> {
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(userGuid), Instant.now().toEpochMilli() + 10000);
        });
        try {
            String testUrl = url.replace("{instanceGuid}", instanceDto.getGuid());
            Request request = RouteTestUtil.buildAuthorizedGetRequest(token, testUrl);
            HttpResponse response = request.execute().returnResponse();
            assertEquals(401, response.getStatusLine().getStatusCode());
            String json = EntityUtils.toString(response.getEntity());
            ApiError resp = gson.fromJson(json, ApiError.class);
            assertEquals(ErrorCodes.OPERATION_NOT_ALLOWED, resp.getCode());
        } finally {
            //revert back temp user update for other tests.
            TransactionWrapper.useTxn(handle -> {
                JdbiUser jdbiUser = handle.attach(JdbiUser.class);
                jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(userGuid), null);
            });
        }
    }

    @Test
    public void testGet_tempUser_authorized() throws Exception {
        //update test user as temp user and set allow_unauthenticated to yes
        TransactionWrapper.useTxn(handle -> {
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(userGuid), Instant.now().toEpochMilli() + 10000);
            handle.attach(JdbiActivity.class).updateAllowUnauthenticatedById(instanceDto.getActivityId(), true);
        });
        try {
            String testUrl = url.replace("{instanceGuid}", instanceDto.getGuid());
            Request request = RouteTestUtil.buildAuthorizedGetRequest(token, testUrl);
            HttpResponse response = request.execute().returnResponse();
            assertEquals(200, response.getStatusLine().getStatusCode());
            String json = EntityUtils.toString(response.getEntity());
            ActivityInstance activity = gson.fromJson(json, ActivityInstance.class);
            assertEquals(ActivityType.FORMS, activity.getActivityType());
            assertEquals(instanceDto.getGuid(), activity.getGuid());
            assertEquals(activityCode, activity.getActivityCode());
        } finally {
            //revert back temp user update for other tests.
            TransactionWrapper.useTxn(handle -> {
                JdbiUser jdbiUser = handle.attach(JdbiUser.class);
                jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(userGuid), null);
                handle.attach(JdbiActivity.class).updateAllowUnauthenticatedById(instanceDto.getActivityId(), false);
            });
        }
    }

    @Test
    public void testGet_PicklistQuestionLabels() {
        Response resp = given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();

        // When "other" is not allowed, properties should be null.
        resp.then().assertThat()
                .body("sections[2].blocks[0].question.stableId", equalTo("PL_NO_OTHER"))
                .body("sections[2].blocks[0].question.picklistOptions.size()", equalTo(1))
                .body("sections[2].blocks[0].question.picklistOptions[0].stableId", equalTo("PL_NO_OTHER_OPT_1"))
                .body("sections[2].blocks[0].question.picklistOptions[0].allowDetails", is(false));

        // When "other" is allowed, properties should be strings.
        resp.then().assertThat()
                .body("sections[2].blocks[1].question.stableId", equalTo("PL_YES_OTHER"))
                .body("sections[2].blocks[1].question.picklistOptions.size()", equalTo(1))
                .body("sections[2].blocks[1].question.picklistOptions[0].stableId", equalTo("PL_YES_OTHER_OPT_OTHER"))
                .body("sections[2].blocks[1].question.picklistOptions[0].allowDetails", is(true))
                .body("sections[2].blocks[1].question.picklistOptions[0].detailLabel", equalTo("Details here"));
    }

    @Test
    public void testGet_picklistQuestion_withGroups() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[2].blocks.size()", equalTo(3))
                .root("sections[2].blocks[2].question")
                .body("stableId", equalTo("PL_GROUPS"))
                .body("groups.size()", equalTo(2))
                .body("groups[0].identifier", equalTo("G1"))
                .body("groups[1].identifier", equalTo("G2"))
                .body("picklistOptions.size()", equalTo(4))
                .body("picklistOptions[0].stableId", equalTo("OPT1"))
                .body("picklistOptions[0].groupId", is(nullValue()))
                .body("picklistOptions[1].stableId", equalTo("G1_OPT1"))
                .body("picklistOptions[1].groupId", equalTo("G1"))
                .body("picklistOptions[2].stableId", equalTo("G2_OPT1"))
                .body("picklistOptions[2].groupId", equalTo("G2"))
                .body("picklistOptions[3].stableId", equalTo("G2_OPT2"))
                .body("picklistOptions[3].groupId", equalTo("G2"));
    }

    @Test
    public void testQuestionNumbering() {
        Response resp = given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();

        resp.then().assertThat()
                .body("sections[0].blocks[0].displayNumber", isEmptyOrNullString())
                .body("sections[0].blocks[1].displayNumber", equalTo(1))
                .body("sections[1].blocks[0].displayNumber", equalTo(3));
    }


    @Test
    public void testGet_textAnswerWithType() {

        Response resp = given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();

        resp.then().assertThat()
                .body("sections[1].blocks[0].question.answers[0].value", equalTo("valid answer"));
        resp.then().assertThat()
                .body("sections[1].blocks[0].question.answers[0].type", equalTo("TEXT"));
    }

    @Test
    public void testGet_agreementQuestion() {
        Response resp = given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();

        resp.then().assertThat()
                .body("sections[4].blocks[0].question.stableId", equalTo("AGREEMENT_Q"));

    }

    @Test
    public void testTextQuestionPlaceholder() {
        Response resp = given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();

        resp.then().assertThat()
                .body("sections[1].blocks[0].question.stableId", equalTo(TEXT_QUESTION_STABLE_ID));
        resp.then().assertThat()
                .body("sections[1].blocks[0].question", hasKey("placeholderText"));
        resp.then().assertThat()
                .body("sections[1].blocks[0].question.placeholderText",
                        equalTo(placeholderTemplate.getTemplateText()));
    }

    @Test
    public void testEssayQuestionHasEmptyPlaceholderField() {
        Response resp = given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();

        resp.then().assertThat()
                .body("sections[3].blocks[0].question.stableId", equalTo(essayQuestionStableId));

        resp.then().assertThat()
                .body("sections[3].blocks[0].question", not(hasKey("placeholderText")));
    }

    @Test
    public void testGet_textQuestion_withSuggestionType() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[1].blocks.size()", equalTo(2))
                .root("sections[1].blocks[1].question")
                .body("stableId", equalTo("TEXT_DRUG"))
                .body("inputType", equalTo(TextInputType.TEXT.name()))
                .body("suggestionType", equalTo(SuggestionType.DRUG.name()));
    }

    @Test
    public void testGet_datePicker_retrieved_withPlaceholder() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("activityType", equalTo(ActivityType.FORMS.name()))
                .body("formType", equalTo(FormType.GENERAL.name()))
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[0].blocks.size()", equalTo(3))
                .body("sections[0].blocks[0].question.questionType", equalTo(QuestionType.DATE.name()))
                .body("sections[0].blocks[0].question.placeholderText", equalTo("select a date"));
    }

    @Test
    public void testGet_datePicker_validationRulesRetrieved() {
        Response resp = given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .and().extract().response();

        // First date text question
        resp.then().assertThat()
                .body("sections[0].blocks[0].question.stableId", equalTo("DATE_TEXT_Q"))
                .body("sections[0].blocks[0].question.validations[0].rule", equalTo("REQUIRED"));

        // Second date single text question
        resp.then().assertThat()
                .body("sections[0].blocks[1].question.stableId", equalTo("DATE_SINGLE_TEXT_Q"))
                .body("sections[0].blocks[1].question.validations.rule",
                        hasItems("YEAR_REQUIRED", "MONTH_REQUIRED", "DAY_REQUIRED"));

        // Third date picklist question
        resp.then().assertThat()
                .root("sections[0].blocks[2].question")
                .body("stableId", equalTo("DATE_PICKLIST_Q"))
                .body("validations.size()", equalTo(1))
                .body("validations[0].rule", equalTo("DATE_RANGE"))
                .body("validations[0].startDate", equalTo("2018-03-14"))
                .body("validations[0].endDate", equalTo(LocalDate.now(ZoneOffset.UTC).toString()))
                .body("validations[0].message", containsString("Pi Day to today"));
    }

    @Test
    public void testGet_renderedCorrectly() {
        // todo: write some better tests when test data are cleaned up
        Response resp = given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();

        // Basic size checks
        resp.then().assertThat()
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[0].blocks.size()", equalTo(3))
                .body("sections[1].blocks.size()", equalTo(2))
                .body("sections[2].blocks.size()", equalTo(3));

        // Check rules are rendered
        resp.then().assertThat()
                .body("sections[0].blocks[0].question.validations[0].rule", equalTo("REQUIRED"))
                .body("sections[1].blocks[0].question.validations[0].minLength", equalTo(5))
                .body("sections[1].blocks[0].question.validations[0].maxLength", equalTo(300));
    }

    @Test
    public void testGet_readonlyIsSetCorrectly() throws InterruptedException {
        Response resp = testFor200AndExtractResponse();
        resp.then().assertThat()
                .body("readonly", equalTo(false));


        Optional<Long> optStudyId = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(
                activity.getStudyGuid())
        );

        TransactionWrapper.withTxn(handle -> handle.attach(JdbiActivity.class).updateEditTimeoutSecByCode(
                1L, activity.getActivityCode(), optStudyId.get())
        );

        TimeUnit.SECONDS.sleep(1L);

        resp = testFor200AndExtractResponse();
        resp.then().assertThat()
                .body("readonly", equalTo(true));

        TransactionWrapper.withTxn(handle -> handle.attach(JdbiActivity.class).updateEditTimeoutSecByCode(
                null, activity.getActivityCode(), optStudyId.get())
        );
    }

    private Response testFor200AndExtractResponse() {
        return given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();
    }

    @Test
    public void testGet_contentStyle_headerKeyCaseInsensitive() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .header(new Header(RouteConstants.Header.DDP_CONTENT_STYLE.toLowerCase(), ContentStyle.STANDARD.name()))
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[5].blocks[0].blockType", equalTo(BlockType.CONTENT.name()))
                .body("sections[5].blocks[0].title", equalTo("<p>hello title</p>"))
                .body("sections[5].blocks[0].body", equalTo("<p>hello body</p>"));
    }

    @Test
    public void testGet_contentStyle_headerValueCaseInsensitive() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .header(new Header(RouteConstants.Header.DDP_CONTENT_STYLE, "   standard   "))
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[5].blocks[0].blockType", equalTo(BlockType.CONTENT.name()))
                .body("sections[5].blocks[0].title", equalTo("<p>hello title</p>"))
                .body("sections[5].blocks[0].body", equalTo("<p>hello body</p>"));
    }

    @Test
    public void testGet_contentStyle_headerValueInvalid() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .header(new Header(RouteConstants.Header.DDP_CONTENT_STYLE, "NOT_A_STYLE_NAME"))
                .when().get(url).then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.MALFORMED_HEADER))
                .body("message", containsString("content style"));
    }

    @Test
    public void testGet_contentStyle_standardGivesHtml() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .header(new Header(RouteConstants.Header.DDP_CONTENT_STYLE, ContentStyle.STANDARD.name()))
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[5].blocks[0].blockType", equalTo(BlockType.CONTENT.name()))
                .body("sections[5].blocks[0].title", equalTo("<p>hello title</p>"))
                .body("sections[5].blocks[0].body", equalTo("<p>hello body</p>"));
    }

    @Test
    public void testGet_contentStyle_basicGivesPlainText() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .header(new Header(RouteConstants.Header.DDP_CONTENT_STYLE, ContentStyle.BASIC.name()))
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[5].blocks[0].blockType", equalTo(BlockType.CONTENT.name()))
                .body("sections[5].blocks[0].title", equalTo("hello title"))
                .body("sections[5].blocks[0].body", equalTo("<p>hello body</p>"));
    }

    @Test
    public void testGet_contentStyle_defaultsToStandard() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[5].blocks[0].blockType", equalTo(BlockType.CONTENT.name()))
                .body("sections[5].blocks[0].title", equalTo("<p>hello title</p>"))
                .body("sections[5].blocks[0].body", equalTo("<p>hello body</p>"));
    }

    @Test
    public void testGet_iconSection_nameSerialized() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[6].name", equalTo("icon section"));
    }

    @Test
    public void testGet_iconSection_iconsSerialized() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[6].icons.size()", equalTo(2))
                .body("sections[6].icons[0].state", equalTo(FormSectionState.COMPLETE.name()))
                .body("sections[6].icons[0].height", equalTo(100))
                .body("sections[6].icons[0].width", equalTo(100))
                .body("sections[6].icons[0].1x", equalTo("https://dev.ddp.org/icon1_1x.png"));
    }

    @Test
    public void testGet_iconSection_iconAdditionalScaleFactorsSerializedAsProperties() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[6].icons.size()", equalTo(2))
                .body("sections[6].icons[1].state", equalTo(FormSectionState.INCOMPLETE.name()))
                .body("sections[6].icons[1].height", equalTo(200))
                .body("sections[6].icons[1].width", equalTo(200))
                .body("sections[6].icons[1].1x", equalTo("https://dev.ddp.org/icon2_1x.png"))
                .body("sections[6].icons[1].2x", equalTo("https://dev.ddp.org/icon2_2x.png"));
    }

    @Test
    public void testGet_activityInstanceReadonlyForExitedUser() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                            testData.getUserGuid(),
                            testData.getStudyGuid(),
                            EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT
                    );
                }
        );
        Response resp = given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().response();

        resp.then().assertThat()
                .body("readonly", equalTo(true));

        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                            testData.getUserGuid(),
                            testData.getStudyGuid(),
                            EnrollmentStatusType.ENROLLED
                    );
                }
        );
    }
}
