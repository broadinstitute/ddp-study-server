package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.util.TestFormActivity.DEFAULT_MAX_FILE_SIZE_FOR_TEST;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.TabularBlockDef;
import org.broadinstitute.ddp.model.activity.definition.SectionIcon;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.EquationQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.tabular.TabularHeaderDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.ComparisonRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.FormSectionState;
import org.broadinstitute.ddp.model.activity.types.ComparisonType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.NestedActivityRenderHint;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class GetActivityInstanceRouteStandaloneTest extends IntegrationTestSuite.TestCaseWithCacheEnabled {
    public static final String TEXT_QUESTION_STABLE_ID = "TEXT_Q";

    public static final String MIME_TYPE_1 = "image/gif";
    public static final String MIME_TYPE_2 = "image/jpeg";

    private static final Set<String> MIME_TYPES = new LinkedHashSet<>() {
        {
            add(MIME_TYPE_1);
            add(MIME_TYPE_2);
        }
    };

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static FormActivityDef parentActivity;
    private static FormActivityDef activity;
    private static ActivityVersionDto activityVersionDto;
    private static ActivityInstanceDto parentInstanceDto;
    private static ActivityInstanceDto instanceDto;
    private static ActivityInstanceDto instanceDto2;
    private static String userGuid;
    private static String token;
    private static String url;
    private static Gson gson;
    private static Template placeholderTemplate;
    private static String essayQuestionStableId;
    private static String activityCode;
    private static long activityId;
    private static TextQuestionDef txt1;
    private static CompositeQuestionDef comp1;
    private static CompositeQuestionDef compositeWithEquation;
    private static FileUpload upload;

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
        //------------- create SECTION[0] ---------
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
        TextQuestionDef t4 = TextQuestionDef.builder(TextInputType.TEXT, "TEXT_Q_DISABLED", newTemplate())
                .setDeprecated(true)
                .build();
        FormSectionDef dateSection = new FormSectionDef(null, TestUtil.wrapQuestions(d1, d2, d3, t4));

        //------------- create SECTION[1] ---------
        placeholderTemplate = newTemplate();
        txt1 = TextQuestionDef.builder(TextInputType.TEXT, TEXT_QUESTION_STABLE_ID, newTemplate())
                .setPlaceholderTemplate(placeholderTemplate)
                .addValidation(new LengthRuleDef(newTemplate(), 5, 300))
                .build();
        TextQuestionDef txt2 = TextQuestionDef.builder(TextInputType.TEXT, "TEXT_DRUG", newTemplate())
                .setSuggestionType(SuggestionType.DRUG)
                .build();
        Template txt3Tmpl = Template.html("$foo $ddp.participantFirstName()'s favorite color?");
        txt3Tmpl.addVariable(TemplateVariable.single("foo", "en", "What is"));
        TextQuestionDef txt3 = TextQuestionDef.builder(TextInputType.TEXT, "TEXT_WITH_SPECIAL_VARS", txt3Tmpl)
                .setTooltip(Template.text("some helper text"))
                .build();
        TextQuestionDef txt4 = TextQuestionDef.builder(TextInputType.TEXT, "TEXT_Q1_DISABLED", newTemplate())
                .setDeprecated(true)
                .build();
        FormSectionDef textSection = new FormSectionDef(null, TestUtil.wrapQuestions(txt1, txt2, txt3, txt4));

        //------------- create SECTION[2] ---------
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
                .addGroup(new PicklistGroupDef("G1", newTemplate(), List.of(
                        new PicklistOptionDef(null, "G1_OPT1", newTemplate(),
                                Template.text("option tooltip"), null, false, false))))
                .addGroup(new PicklistGroupDef("G2", newTemplate(), Arrays.asList(
                        new PicklistOptionDef("G2_OPT1", newTemplate()),
                        new PicklistOptionDef("G2_OPT2", newTemplate()))))
                .addOption(new PicklistOptionDef("OPT1", newTemplate()))
                .build();
        TextQuestionDef t5 = TextQuestionDef.builder(TextInputType.TEXT, "TEXT_Q3_DISABLED", newTemplate())
                .setDeprecated(true)
                .build();

        //add picklist nested options question
        PicklistOptionDef nestedOptionDef1 = new PicklistOptionDef("NESTED_OPT1", new Template(TemplateType.TEXT, null, "nested option 1"));
        PicklistOptionDef nestedOptionDef2 = new PicklistOptionDef("NESTED_OPT2", new Template(TemplateType.TEXT, null, "nested option 2"));
        List<PicklistOptionDef> nestedOpts = List.of(nestedOptionDef1, nestedOptionDef2);
        PicklistOptionDef nestedPLOptionDef = new PicklistOptionDef("PARENT_OPT", new Template(TemplateType.TEXT, null, "parent option1"),
                new Template(TemplateType.TEXT, null, "nested options Label"), nestedOpts);
        String stableIdNPL = "PQ_NESTED_OPTS";
        PicklistQuestionDef nestedPLOptionsQuestion = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.LIST, stableIdNPL,
                new Template(TemplateType.TEXT, null, "prompt for Nested PL Question"))
                .addOption(nestedPLOptionDef)
                .build();

        FormSectionDef plistSection = new FormSectionDef(null, TestUtil.wrapQuestions(p1, p2, p3, t5, nestedPLOptionsQuestion));

        //------------- create SECTION[3] ---------
        essayQuestionStableId = "PATCH_TEXT_Q2";
        TextQuestionDef t2 = TextQuestionDef.builder(
                TextInputType.ESSAY,
                essayQuestionStableId,
                newTemplate()
        ).build();
        FormSectionDef textSection2 = new FormSectionDef(null, TestUtil.wrapQuestions(t2));

        //------------- create SECTION[4] ---------
        AgreementQuestionDef a1 = new AgreementQuestionDef("AGREEMENT_Q",
                false,
                newTemplate("I agree with the preceding text"),
                null,
                newTemplate("info header"),
                newTemplate("info footer"),
                List.of(new RequiredRuleDef(newTemplate())),
                true,
                false);
        FormSectionDef agreementSection = new FormSectionDef(null, TestUtil.wrapQuestions(a1));

        //------------- create SECTION[5] ---------
        Template contentTitle = new Template(TemplateType.HTML, null, "<p>hello title</p>");
        Template contentBody = new Template(TemplateType.HTML, null, "<p>hello body</p>");
        ContentBlockDef contentDef = new ContentBlockDef(contentTitle, contentBody);
        ContentBlockDef content2 = new ContentBlockDef(null, Template.html(
                "<p>$ddp.participantFirstName()<br/>$ddp.participantLastName()<br/>$ddp.date(\"MM-dd-uuuu\")</p>"));

        /*
        ContentBlockDef content2 = new ContentBlockDef(null, Template.html(
                String.format(
                "<p>$ddp.participantFirstName()<br/>$ddp.participantLastName()<br/>%s</p>",
                        LocalDate.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("MM-dd-uuuu"))
                ))); */

        FormSectionDef contentSection = new FormSectionDef(null, List.of(contentDef, content2));

        //------------- create SECTION[6] ---------
        Template nameTmpl = Template.text("icon section");
        SectionIcon icon1 = new SectionIcon(FormSectionState.COMPLETE, 100, 100);
        icon1.putSource("1x", new URL("https://dev.ddp.org/icon1_1x.png"));
        SectionIcon icon2 = new SectionIcon(FormSectionState.INCOMPLETE, 200, 200);
        icon2.putSource("1x", new URL("https://dev.ddp.org/icon2_1x.png"));
        icon2.putSource("2x", new URL("https://dev.ddp.org/icon2_2x.png"));
        FormSectionDef iconSection = new FormSectionDef(null, nameTmpl, Arrays.asList(icon1, icon2), Collections.emptyList());

        //------------- create SECTION[7] ---------
        comp1 = CompositeQuestionDef.builder()
                .setStableId("comp" + System.currentTimeMillis())
                .setPrompt(Template.text("composite"))
                .addChildrenQuestions(TextQuestionDef
                        .builder(TextInputType.TEXT, "comp-child" + System.currentTimeMillis(), Template.text("comp child"))
                        .build())
                .build();

        compositeWithEquation = CompositeQuestionDef.builder()
                .setStableId("compositeWithEquation" + System.currentTimeMillis())
                .setPrompt(Template.text("composite"))
                .addChildrenQuestions(
                        DecimalQuestionDef
                                .builder("RECTANGLE_WIDTH", Template.text("This is value"))
                                .setScale(2)
                                .build(),
                        DecimalQuestionDef
                                .builder("RECTANGLE_HEIGHT", Template.text("This is value"))
                                .setScale(2)
                                .build(),
                        EquationQuestionDef.builder()
                                .stableId("RECTANGLE_AREA")
                                .questionType(QuestionType.EQUATION)
                                .promptTemplate(new Template(TemplateType.TEXT, null, "Equation"))
                                .validations(new ArrayList<>())
                                .expression("RECTANGLE_WIDTH * RECTANGLE_HEIGHT")
                                .build())
                .build();
        var compSection = new FormSectionDef(null, TestUtil.wrapQuestions(comp1, compositeWithEquation));

        //------------- create SECTION[8] ---------
        FileQuestionDef file1 = FileQuestionDef
                .builder("FILE" + System.currentTimeMillis(), Template.text("file"))
                .setMaxFileSize(DEFAULT_MAX_FILE_SIZE_FOR_TEST)
                .setMimeTypes(MIME_TYPES)
                .build();
        var fileSection = new FormSectionDef(null, List.of(new QuestionBlockDef(file1)));

        //------------- create SECTION[9] ---------
        MatrixQuestionDef mqf1 = MatrixQuestionDef.builder(MatrixSelectMode.SINGLE, "MA_SINGLE", newTemplate())
                .addRow(new MatrixRowDef("MA_SINGLE_ROW_1", newTemplate()))
                .addRow(new MatrixRowDef("MA_SINGLE_ROW_2", newTemplate()))
                .addOption(new MatrixOptionDef("MA_SINGLE_OPT_1", newTemplate(), "DEFAULT"))
                .addOption(new MatrixOptionDef("MA_SINGLE_OPT_2", newTemplate(), "MA_SINGLE_GROUP"))
                .addOption(new MatrixOptionDef("MA_SINGLE_OPT_3", newTemplate(), "MA_SINGLE_GROUP"))
                .addGroups(List.of(
                        new MatrixGroupDef("DEFAULT", null),
                        new MatrixGroupDef("MA_SINGLE_GROUP", newTemplate())))
                .build();

        MatrixQuestionDef mqf2 = MatrixQuestionDef.builder(MatrixSelectMode.MULTIPLE, "MA_MULTI", newTemplate())
                .addRow(new MatrixRowDef("MA_MULTI_ROW_1", newTemplate()))
                .addRow(new MatrixRowDef("MA_MULTI_ROW_2", newTemplate()))
                .addOption(new MatrixOptionDef("MA_MULTI_OPT_1", newTemplate(), "DEFAULT"))
                .addOption(new MatrixOptionDef("MA_MULTI_OPT_2", newTemplate(), "MA_MULTI_GROUP"))
                .addOption(new MatrixOptionDef("MA_MULTI_OPT_3", newTemplate(), "MA_MULTI_GROUP"))
                .addGroups(List.of(
                        new MatrixGroupDef("DEFAULT", null),
                        new MatrixGroupDef("MA_MULTI_GROUP", newTemplate())))
                .addValidation(new RequiredRuleDef(newTemplate()))
                .build();

        FormSectionDef matrixSection = new FormSectionDef(null, TestUtil.wrapQuestions(mqf1, mqf2));

        //------------- create SECTION[10] ---------
        final DecimalQuestionDef decimalDef = DecimalQuestionDef
                .builder("DECIMAL_QUESTION", Template.text("This is value"))
                .setScale(2)
                .build();

        final DecimalQuestionDef decimalDefWithValidation = DecimalQuestionDef
                .builder("DECIMAL_QUESTION_WITH_VALIDATION", Template.text("This is value"))
                .addValidation(new ComparisonRuleDef(newTemplate(), decimalDef.getStableId(), ComparisonType.GREATER_OR_EQUAL))
                .setScale(2)
                .build();

        final EquationQuestionDef equationDef = EquationQuestionDef.builder()
                .stableId("EQUATION_QUESTION")
                .questionType(QuestionType.EQUATION)
                .promptTemplate(new Template(TemplateType.TEXT, null, "Equation"))
                .validations(new ArrayList<>())
                .expression("5 * " + decimalDef.getStableId())
                .build();

        FormSectionDef numericSection = new FormSectionDef(null,
                TestUtil.wrapQuestions(decimalDef, decimalDefWithValidation, equationDef));

        //------------- create SECTION[11] ---------
        final DecimalQuestionDef questionLU = DecimalQuestionDef
                .builder("QUESTION_LU", Template.text("This is value"))
                .setScale(2)
                .build();
        final QuestionBlockDef questionBlockDefLU = new QuestionBlockDef(questionLU);

        final DecimalQuestionDef questionRU = DecimalQuestionDef
                .builder("QUESTION_RU", Template.text("This is value"))
                .setScale(2)
                .build();
        final QuestionBlockDef questionBlockDefRU = new QuestionBlockDef(questionRU);

        final DecimalQuestionDef questionLB = DecimalQuestionDef
                .builder("QUESTION_LB", Template.text("This is value"))
                .setScale(2)
                .build();
        final QuestionBlockDef questionBlockDefLB = new QuestionBlockDef(questionLB);

        final DecimalQuestionDef questionRB = DecimalQuestionDef
                .builder("QUESTION_RB", Template.text("This is value"))
                .setScale(2)
                .build();
        final QuestionBlockDef questionBlockDefRB = new QuestionBlockDef(questionRB);

        var tabularBlock = new TabularBlockDef(2);
        tabularBlock.getBlocks().addAll(Arrays.asList(questionBlockDefLU, questionBlockDefRU));
        tabularBlock.getBlocks().addAll(Arrays.asList(questionBlockDefLB, questionBlockDefRB));

        tabularBlock.getHeaders().add(new TabularHeaderDef(1, Template.text("Left column")));
        tabularBlock.getHeaders().add(new TabularHeaderDef(1, Template.text("Right column")));
        FormSectionDef tabularSection = new FormSectionDef(null, Collections.singletonList(tabularBlock));

        //------------- create STUDY ACTIVITY ---------
        String parentActCode = "ACT_ROUTE_PARENT" + Instant.now().toEpochMilli();
        activityCode = "ACT_ROUTE_ACT" + Instant.now().toEpochMilli();
        var nestedActBlockDef = new NestedActivityBlockDef(
                activityCode, NestedActivityRenderHint.EMBEDDED, true, Template.text("add button"));

        var parentQuestion = TextQuestionDef.builder(TextInputType.TEXT, parentActCode + "_Q1", Template.text("q1")).build();
        parentActivity = FormActivityDef.generalFormBuilder(parentActCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "parent activity " + parentActCode))
                .addSubtitle(new Translation("en", "$ddp.answer(\"" + parentQuestion.getStableId() + "\",\"fallback\")"))
                .addSection(new FormSectionDef(null, List.of(nestedActBlockDef)))
                .addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(parentQuestion))))
                .build();
        activity = FormActivityDef.generalFormBuilder(activityCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + activityCode))
                .setParentActivityCode(parentActCode)
                .setCanDeleteInstances(true)
                .setCanDeleteFirstInstance(false)
                .addSections(Arrays.asList(dateSection, textSection, plistSection, textSection2, agreementSection, contentSection))
                .addSection(iconSection)
                .addSection(compSection)
                .addSection(fileSection)
                .addSection(matrixSection)
                .addSection(numericSection)
                .addSection(tabularSection)
                .build();
        activityVersionDto = handle.attach(ActivityDao.class).insertActivity(
                parentActivity, List.of(activity), RevisionMetadata.now(testData.getUserId(), "add " + activityCode)
        );
        assertNotNull(activity.getActivityId());
        activityId = activity.getActivityId();


        //------------ create ACTIVITY INSTANCE ----------
        ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
        parentInstanceDto = instanceDao.insertInstance(parentActivity.getActivityId(), userGuid);
        instanceDto = instanceDao.insertInstance(activity.getActivityId(), userGuid, userGuid, parentInstanceDto.getId());
        instanceDto2 = instanceDao.insertInstance(activity.getActivityId(), userGuid, userGuid, parentInstanceDto.getId());


        //------------- create ANSWERS ---------------
        AnswerDao answerDao = handle.attach(AnswerDao.class);
        answerDao.createAnswer(testData.getUserId(), parentInstanceDto.getId(),
                new TextAnswer(null, parentQuestion.getStableId(), null, "parent-subtitle-answer"));

        answerDao.createAnswer(testData.getUserId(), instanceDto.getId(),
                new TextAnswer(null, txt1.getStableId(), null, "valid answer"));

        answerDao.createAnswer(testData.getUserId(), instanceDto.getId(),
                new DecimalAnswer(null, decimalDef.getStableId(), null, new DecimalDef(2)));

        answerDao.createAnswer(testData.getUserId(), instanceDto.getId(),
                new DecimalAnswer(null, decimalDefWithValidation.getStableId(), null, new DecimalDef(3)));

        var compAnswer = new CompositeAnswer(null, comp1.getStableId(), null);
        compAnswer.addRowOfChildAnswers(new TextAnswer(null, comp1.getChildren().get(0).getStableId(), null, "comp child"));
        answerDao.createAnswer(testData.getUserId(), instanceDto.getId(), compAnswer);

        var compositeEquationAnswer = new CompositeAnswer(null, compositeWithEquation.getStableId(), null);
        compositeEquationAnswer.addRowOfChildAnswers(
                new DecimalAnswer(null, compositeWithEquation.getChildren().get(0).getStableId(), null, new DecimalDef(1)),
                new DecimalAnswer(null, compositeWithEquation.getChildren().get(1).getStableId(), null, new DecimalDef(1)));
        compositeEquationAnswer.addRowOfChildAnswers(
                new DecimalAnswer(null, compositeWithEquation.getChildren().get(0).getStableId(), null, new DecimalDef(2)),
                new DecimalAnswer(null, compositeWithEquation.getChildren().get(1).getStableId(), null, null));
        compositeEquationAnswer.addRowOfChildAnswers(
                new DecimalAnswer(null, compositeWithEquation.getChildren().get(0).getStableId(), null, new DecimalDef(3)),
                new DecimalAnswer(null, compositeWithEquation.getChildren().get(1).getStableId(), null, new DecimalDef(3)));

        answerDao.createAnswer(testData.getUserId(), instanceDto.getId(), compositeEquationAnswer);

        var fileDao = handle.attach(FileUploadDao.class);
        long userId = testData.getUserId();
        long studyId = testData.getStudyId();
        upload = fileDao.createAuthorized(GuidUtils.randomFileUploadGuid(),
                studyId, userId, userId, "blob", "application/pdf", "file.pdf", 123L);
        fileDao.markVerified(upload.getId());
        var fileAnswer = new FileAnswer(null, file1.getStableId(), null,
                Collections.singletonList(fileDao.findFileInfoByGuid(upload.getGuid()).get()));
        answerDao.createAnswer(testData.getUserId(), instanceDto.getId(), fileAnswer);
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
            handle.attach(ActivityInstanceDao.class).deleteAllByIds(Set.of(instanceDto2.getId()));
            handle.attach(ActivityInstanceDao.class).deleteAllByIds(Set.of(instanceDto.getId()));
            handle.attach(AnswerDao.class).deleteAllByInstanceIds(Set.of(instanceDto.getId()));
            handle.attach(FileUploadDao.class).deleteById(upload.getId());
        });
    }

    private static ValidatableResponse testFor200() {
        return given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON);
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
        ActivityInstance inst = gson.fromJson(json, ActivityInstance.class);
        assertEquals(ActivityType.FORMS, inst.getActivityType());
        assertEquals(instanceDto.getGuid(), inst.getGuid());
        assertEquals(activityCode, inst.getActivityCode());
        assertEquals(InstanceStatusType.CREATED, inst.getStatusType());
        assertEquals(parentInstanceDto.getGuid(), inst.getParentInstanceGuid());
        assertFalse("first child instance should have canDelete false", inst.canDelete());
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
            ActivityInstance inst = gson.fromJson(json, ActivityInstance.class);
            assertEquals(ActivityType.FORMS, inst.getActivityType());
            assertEquals(instanceDto.getGuid(), inst.getGuid());
            assertEquals(activityCode, inst.getActivityCode());
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
    public void testGet_parentActivity_nestedActivityBlock() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", parentInstanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("activityCode", equalTo(parentActivity.getActivityCode()))
                .body("guid", equalTo(parentInstanceDto.getGuid()))
                .body("parentInstanceGuid", nullValue())
                .body("canDelete", equalTo(false))
                .body("subtitle", equalTo("parent-subtitle-answer"))
                .root("sections[0].blocks[0]")
                .body("blockType", equalTo(BlockType.ACTIVITY.name()))
                .body("activityCode", equalTo(activityCode))
                .body("renderHint", equalTo(NestedActivityRenderHint.EMBEDDED.name()))
                .body("allowMultiple", equalTo(true))
                .body("addButtonText", equalTo("add button"))
                .body("instances.size()", equalTo(2))
                .body("instances[0].activityCode", equalTo(activityCode))
                .body("instances[0].instanceGuid", equalTo(instanceDto.getGuid()))
                .body("instances[0].canDelete", equalTo(false))
                .body("instances[1].instanceGuid", equalTo(instanceDto2.getGuid()))
                .body("instances[1].canDelete", equalTo(true));
    }

    @Test
    public void testGet_PicklistQuestionLabels() {
        Response resp = testFor200AndExtractResponse();
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
        testFor200()
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[2].blocks.size()", equalTo(4))
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
    public void testGet_tabularSection() {
        testFor200()
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[11].blocks.size()", equalTo(1))
                .body("sections[11].blocks[0].blockType", equalTo(BlockType.TABULAR.toString()))
                .body("sections[11].blocks[0].columnsCount", equalTo(2))
                .body("sections[11].blocks[0].headers.size()", equalTo(2))
                .body("sections[11].blocks[0].headers[0].columnSpan", equalTo(1))
                .body("sections[11].blocks[0].headers[0].label", equalTo("Left column"))
                .body("sections[11].blocks[0].headers[1].columnSpan", equalTo(1))
                .body("sections[11].blocks[0].headers[1].label", equalTo("Right column"))
                .body("sections[11].blocks[0].content.size()", equalTo(4))
                .body("sections[11].blocks[0].content[0].question.stableId", equalTo("QUESTION_LU"))
                .body("sections[11].blocks[0].content[1].question.stableId", equalTo("QUESTION_RU"))
                .body("sections[11].blocks[0].content[2].question.stableId", equalTo("QUESTION_LB"))
                .body("sections[11].blocks[0].content[3].question.stableId", equalTo("QUESTION_RB"));
    }

    @Test
    public void testGet_matrixQuestion() {
        Response resp = testFor200AndExtractResponse();

        resp.then().assertThat()
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[9].blocks.size()", equalTo(2));

        resp.then().assertThat()
                .root("sections[9].blocks[0].question")
                .body("questionType", equalTo("MATRIX"))
                .body("stableId", equalTo("MA_SINGLE"))
                .body("selectMode", equalTo("SINGLE"))
                .body("groups.size()", equalTo(2))
                .body("groups[0].identifier", equalTo("DEFAULT"))
                .body("groups[1].identifier", equalTo("MA_SINGLE_GROUP"))
                .body("options.size()", equalTo(3))
                .body("options[0].stableId", equalTo("MA_SINGLE_OPT_1"))
                .body("options[0].groupId", equalTo("DEFAULT"))
                .body("options[1].stableId", equalTo("MA_SINGLE_OPT_2"))
                .body("options[1].groupId", equalTo("MA_SINGLE_GROUP"))
                .body("options[2].stableId", equalTo("MA_SINGLE_OPT_3"))
                .body("options[2].groupId", equalTo("MA_SINGLE_GROUP"))
                .body("questions.size()", equalTo(2))
                .body("questions[0].stableId", equalTo("MA_SINGLE_ROW_1"))
                .body("questions[1].stableId", equalTo("MA_SINGLE_ROW_2"));

        resp.then().assertThat()
                .root("sections[9].blocks[1].question")
                .body("questionType", equalTo("MATRIX"))
                .body("stableId", equalTo("MA_MULTI"))
                .body("selectMode", equalTo("MULTIPLE"))
                .body("groups.size()", equalTo(2))
                .body("groups[0].identifier", equalTo("DEFAULT"))
                .body("groups[1].identifier", equalTo("MA_MULTI_GROUP"))
                .body("options.size()", equalTo(3))
                .body("options[0].stableId", equalTo("MA_MULTI_OPT_1"))
                .body("options[0].groupId", equalTo("DEFAULT"))
                .body("options[1].stableId", equalTo("MA_MULTI_OPT_2"))
                .body("options[1].groupId", equalTo("MA_MULTI_GROUP"))
                .body("options[2].stableId", equalTo("MA_MULTI_OPT_3"))
                .body("options[2].groupId", equalTo("MA_MULTI_GROUP"))
                .body("questions.size()", equalTo(2))
                .body("questions[0].stableId", equalTo("MA_MULTI_ROW_1"))
                .body("questions[1].stableId", equalTo("MA_MULTI_ROW_2"))
                .body("validations[0].rule", equalTo("REQUIRED"));
    }

    @Test
    public void testGet_decimalQuestion() {
        Response resp = testFor200AndExtractResponse();

        resp.then().assertThat()
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[10].blocks.size()", equalTo(3));

        resp.then().assertThat()
                .root("sections[10].blocks[0].question")
                .body("questionType", equalTo(QuestionType.DECIMAL.toString()))
                .body("stableId", equalTo("DECIMAL_QUESTION"))
                .body("answers.size()", equalTo(1))
                .body("answers[0].value.value", equalTo(2000000000000000L))
                .body("answers[0].value.scale", equalTo(15));
    }

    @Test
    public void testGet_equationQuestion() {
        Response resp = testFor200AndExtractResponse();

        resp.then().assertThat()
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[10].blocks.size()", equalTo(3));

        resp.then().assertThat()
                .root("sections[10].blocks[2].question")
                .body("questionType", equalTo(QuestionType.EQUATION.toString()))
                .body("stableId", equalTo("EQUATION_QUESTION"))
                .body("answers.size()", equalTo(1))
                .body("answers.value.size()", equalTo(1))
                .body("answers[0].value[0].value", equalTo(1000000000000000L))
                .body("answers[0].value[0].scale", equalTo(14));
    }

    @Test
    public void testQuestionNumbering() {
        Response resp = testFor200AndExtractResponse();
        resp.then().assertThat()
                .body("sections[0].blocks[0].displayNumber", isEmptyOrNullString())
                .body("sections[0].blocks[1].displayNumber", equalTo(1))
                .body("sections[1].blocks[0].displayNumber", equalTo(3));
    }

    @Test
    public void testGet_textAnswerWithType() {
        Response resp = testFor200AndExtractResponse();
        resp.then().assertThat()
                .body("sections[1].blocks[0].question.answers[0].value", equalTo("valid answer"));
        resp.then().assertThat()
                .body("sections[1].blocks[0].question.answers[0].type", equalTo("TEXT"));
    }

    @Test
    public void testGet_agreementQuestion() {
        Response resp = testFor200AndExtractResponse();
        resp.then().assertThat()
                .body("sections[4].blocks[0].question.stableId", equalTo("AGREEMENT_Q"));
    }

    @Test
    public void testTextQuestionPlaceholder() {
        Response resp = testFor200AndExtractResponse();
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
        Response resp = testFor200AndExtractResponse();
        resp.then().assertThat()
                .body("sections[3].blocks[0].question.stableId", equalTo(essayQuestionStableId));
        resp.then().assertThat()
                .body("sections[3].blocks[0].question", not(hasKey("placeholderText")));
    }

    @Test
    public void testGet_textQuestion_withSuggestionType() {
        testFor200()
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[1].blocks.size()", equalTo(3))
                .root("sections[1].blocks[1].question")
                .body("stableId", equalTo("TEXT_DRUG"))
                .body("inputType", equalTo(TextInputType.TEXT.name()))
                .body("suggestionType", equalTo(SuggestionType.DRUG.name()));
    }

    @Test
    public void testGet_datePicker_retrieved_withPlaceholder() {
        testFor200()
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
        Response resp = testFor200()
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
        Response resp = testFor200AndExtractResponse();
        // Basic size checks
        resp.then().assertThat()
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[0].blocks.size()", equalTo(3))
                .body("sections[1].blocks.size()", equalTo(3))
                .body("sections[2].blocks.size()", equalTo(4));

        // Check rules are rendered
        resp.then().assertThat()
                .body("sections[0].blocks[0].question.validations[0].rule", equalTo("REQUIRED"))
                .body("sections[1].blocks[0].question.validations[0].minLength", equalTo(5))
                .body("sections[1].blocks[0].question.validations[0].maxLength", equalTo(300));
    }

    @Test
    public void test_givenReadonlyFlagIsNotSet_whenEditTimeoutExpires_thenInstanceBecomesReadonly() throws InterruptedException {
        Response resp = testFor200AndExtractResponse();
        resp.then().assertThat().body("readonly", equalTo(false));

        Optional<Long> optStudyId = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(
                activity.getStudyGuid())
        );

        ActivityDefStore.getInstance().clearCachedActivityData();
        TransactionWrapper.withTxn(handle -> handle.attach(JdbiActivity.class).updateEditTimeoutSecByCode(
                1L, activity.getActivityCode(), optStudyId.get())
        );

        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiActivityInstance.class).updateIsReadonlyByGuid(null, instanceDto.getGuid())
        );

        TimeUnit.SECONDS.sleep(1L);

        resp = testFor200AndExtractResponse();
        resp.then().assertThat()
                .body("readonly", equalTo(true));

        TransactionWrapper.withTxn(handle -> handle.attach(JdbiActivity.class).updateEditTimeoutSecByCode(
                null, activity.getActivityCode(), optStudyId.get())
        );
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
        testFor200()
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[5].blocks[0].blockType", equalTo(BlockType.CONTENT.name()))
                .body("sections[5].blocks[0].title", equalTo("<p>hello title</p>"))
                .body("sections[5].blocks[0].body", equalTo("<p>hello body</p>"));
    }

    @Test
    public void testGet_iconSection_nameSerialized() {
        testFor200()
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[6].name", equalTo("icon section"));
    }

    @Test
    public void testGet_iconSection_iconsSerialized() {
        testFor200()
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[6].icons.size()", equalTo(2))
                .body("sections[6].icons[0].state", equalTo(FormSectionState.COMPLETE.name()))
                .body("sections[6].icons[0].height", equalTo(100))
                .body("sections[6].icons[0].width", equalTo(100))
                .body("sections[6].icons[0].1x", equalTo("https://dev.ddp.org/icon1_1x.png"));
    }

    @Test
    public void testGet_iconSection_iconAdditionalScaleFactorsSerializedAsProperties() {
        testFor200()
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
                handle -> handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                        testData.getUserGuid(),
                        testData.getStudyGuid(),
                        EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT
                )
        );
        Response resp = testFor200AndExtractResponse();

        resp.then().assertThat().body("readonly", equalTo(true));

        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                        testData.getUserGuid(),
                        testData.getStudyGuid(),
                        EnrollmentStatusType.ENROLLED
                )
        );
    }

    @Test
    public void given_oneTrueExpr_whenRouteIsCalled_thenItReturnsOkAndResponseDoesntContainFailedValidations() {
        try {
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiActivity.class).insertValidation(
                    RouteTestUtil.createActivityValidationDto(
                            activity,
                            "false", "Should never fail", List.of(txt1.getStableId())
                    ),
                    testData.getUserId(),
                    testData.getStudyId(),
                    activityVersionDto.getRevId()
            ));
            testFor200()
                    .body("sections[1].blocks[0].question.validationFailures", is(nullValue()));
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiActivity.class).deleteValidationsByCode(activityId));
        }
    }

    @Test
    public void given_oneTrueExpr_whenRouteIsCalled_thenItReturnsOkAndResponseContainsFailedValidations() {
        ActivityDefStore.getInstance().clear();
        try {
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiActivity.class).insertValidation(
                    RouteTestUtil.createActivityValidationDto(
                            activity,
                            "true", "Should always fail", List.of(txt1.getStableId())
                    ),
                    testData.getUserId(),
                    testData.getStudyId(),
                    activityVersionDto.getRevId()
            ));
            testFor200()
                    .body("sections[1].blocks[0].question.validationFailures", not(is(nullValue())))
                    .body("sections[1].blocks[0].question.validationFailures.size()", equalTo(1));
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiActivity.class).deleteValidationsByCode(activityId));
        }
    }

    @Test
    public void test_whenIsHidden_thenNotFound() {
        TransactionWrapper.useTxn(handle -> assertEquals(2, handle.attach(ActivityInstanceDao.class)
                .bulkUpdateIsHiddenByActivityIds(testData.getUserId(), true, Set.of(activity.getActivityId()))));
        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", instanceDto.getGuid())
                    .when().get(url).then().assertThat()
                    .statusCode(404).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                    .body("message", containsString("is hidden"));
        } finally {
            TransactionWrapper.useTxn(handle -> assertEquals(2, handle.attach(ActivityInstanceDao.class)
                    .bulkUpdateIsHiddenByActivityIds(testData.getUserId(), false, Set.of(activity.getActivityId()))));
        }
    }

    @Test
    public void testStudyAdmin_canRetrieveInstances() {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(2, handle.attach(ActivityInstanceDao.class)
                    .bulkUpdateIsHiddenByActivityIds(testData.getUserId(), true, Set.of(activity.getActivityId())));
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
        });
        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", instanceDto.getGuid())
                    .when().get(url).then().assertThat()
                    .log().all()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("guid", equalTo(instanceDto.getGuid()))
                    .body("isHidden", equalTo(true));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                assertEquals(2, handle.attach(ActivityInstanceDao.class)
                        .bulkUpdateIsHiddenByActivityIds(testData.getUserId(), false, Set.of(activity.getActivityId())));
                handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
            });
        }
    }

    @Test
    public void testSpecialVarsSubstitutions() {
        UserProfile profile = testData.getProfile();
        Response resp = testFor200AndExtractResponse();

        String expectedPrompt = "What is " + profile.getFirstName() + "'s favorite color?";
        resp.then().assertThat().body("sections[1].blocks[2].question.prompt", equalTo(expectedPrompt));

        /*
        String expectedBody = String.format("<p>%s<br/>%s<br/>%$ddp.date()</p>", profile.getFirstName(), profile.getLastName(),
                LocalDate.now(Clock.systemUTC()).format(DateTimeFormatter.ofPattern("MM-dd-uuuu")));
        // DateTimeFormatter.ofPattern("MM-dd-uuuu").format(LocalDate.now(ZoneId.of("America/New_York")))
        */
        String expectedBody = String.format("<p>%s<br/>%s<br/>$ddp.date(\"MM-dd-uuuu\")</p>", profile.getFirstName(), profile.getLastName());
        // DateTimeFormatter.ofPattern("MM-dd-uuuu").format(LocalDate.now(ZoneId.of("America/New_York")))
        resp.then().assertThat().body("sections[5].blocks[1].body", equalTo(expectedBody));
    }

    @Test
    public void testTooltip() {
        testFor200AndExtractResponse()
                .then().assertThat()
                .root("sections[1].blocks[2].question")
                .body("tooltip", equalTo("some helper text"))
                .root("sections[2].blocks[2].question")
                .body("picklistOptions[1].stableId", equalTo("G1_OPT1"))
                .body("picklistOptions[1].tooltip", equalTo("option tooltip"));
    }

    @Test
    public void test_compositeChildQuestionsShouldNotHaveAnswers() {
        testFor200()
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[7].blocks.size()", equalTo(2))
                .root("sections[7].blocks[0].question")
                .body("stableId", equalTo(comp1.getStableId()))
                .body("answers.size()", equalTo(1))
                .body("children.size()", equalTo(1))
                .body("children[0].answers.size()", equalTo(0));
    }

    @Test
    public void test_compositeEquationComputed() {
        testFor200()
                .body("guid", equalTo(instanceDto.getGuid()))
                .body("sections[7].blocks.size()", equalTo(2))
                .root("sections[7].blocks[1].question")
                .body("stableId", equalTo(compositeWithEquation.getStableId()))
                .body("answers.size()", equalTo(1))
                .body("children.size()", equalTo(3))
                .body("children[2].answers.size()", equalTo(1))
                .body("children[2].answers[0].value.size()", equalTo(3))
                .body("children[2].answers[0].value[0].value", equalTo(1000000000000000L))
                .body("children[2].answers[0].value[0].scale", equalTo(15))
                .body("children[2].answers[0].value[1]", equalTo(null))
                .body("children[2].answers[0].value[2].value", equalTo(9000000000000000L))
                .body("children[2].answers[0].value[2].scale", equalTo(15));
    }

    @Test
    public void testFileQuestionAndAnswer() {
        testFor200AndExtractResponse()
                .then().assertThat()
                .log().all()
                .root("sections[8].blocks[0].question")
                .body("questionType", equalTo(QuestionType.FILE.name()))
                .body("answers.size()", equalTo(1))
                .body("answers[0].value[0].fileName", equalTo("file.pdf"))
                .body("answers[0].value[0].fileSize", equalTo(123));
    }

    /**
     * To DB were added 3 deprecated questions: in each section - 1 deprecated question.
     * Verify that these questions (and blocks enclosing it) are not included into
     * rendered sections.
     */
    @Test
    public void testThatBlocksWithDeprecatedQuestionsNotAddedToSections() {
        testFor200()
                .body("sections.size()", equalTo(activity.getSections().size()))
                .body("sections[0].blocks.size()", equalTo(3))
                .body("sections[1].blocks.size()", equalTo(3))
                .body("sections[2].blocks.size()", equalTo(4));
    }

    @Test
    public void testFileQuestionProperties() {
        testFor200()
                .body("sections[8].blocks[0].question.questionType", equalTo(QuestionType.FILE.name()))
                .body("sections[8].blocks[0].question.maxFileSize", equalTo(Long.valueOf(DEFAULT_MAX_FILE_SIZE_FOR_TEST).intValue()))
                .body("sections[8].blocks[0].question.mimeTypes[0]", equalTo(MIME_TYPE_1))
                .body("sections[8].blocks[0].question.mimeTypes[1]", equalTo(MIME_TYPE_2));
    }

    private Response testFor200AndExtractResponse() {
        return testFor200().and().extract().response();
    }
}
