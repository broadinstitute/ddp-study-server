package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.util.TestFormActivity.DEFAULT_MAX_FILE_SIZE_FOR_TEST;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerCachedDao;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.json.AnswerResponse;
import org.broadinstitute.ddp.json.AnswerSubmission;
import org.broadinstitute.ddp.json.PatchAnswerPayload;
import org.broadinstitute.ddp.json.PatchAnswerResponse;
import org.broadinstitute.ddp.json.errors.AnswerValidationError;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.EquationQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.IntRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DecimalRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedMatrixCell;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.eclipse.jetty.http.HttpStatus;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PatchFormAnswersRouteStandaloneTest {

    private static Gson gson;

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;
    private static String token;
    private static String urlTemplate;
    private static String url;

    private static FormActivityDef parentActivity;
    private static FormActivityDef activity;
    private static ActivityVersionDto activityVersionDto;
    private static ActivityInstanceDto parentInstanceDto;
    private static ActivityInstanceDto instanceDto;
    private static String instanceGuid;
    private static String boolStableId;
    private static String textStableId;
    private static String textStableId2;
    private static String textStableId3;
    private static String plistSingleSelectSid;
    private static String plistMultiSelectSid;
    private static String dateTextSid;
    private static String dateSingleTextSid;
    private static String datePicklistSid;
    private static String essayTextStableId;
    private static String agreementSid;
    private static String childTextStableId;
    private static String childDateStableId;
    private static String compStabledId;

    private static NumericQuestionDef numericQuestionDef;
    private static String numericIntegerSid;
    private static String numericIntegerReqSid;
    private static String numericIntegerWithMultipleRulesSid;

    private static DecimalQuestionDef decimalQuestionDef;
    private static String decimalIntegerSid;
    private static String decimalIntegerReqSid;
    private static String decimalIntegerWithMultipleRulesSid;

    private static EquationQuestionDef equationQuestionDef;
    private static String equationSid;

    private static String plistSingle_option1_sid;
    private static String plistSingle_option2_sid;
    private static String plistMulti_option1_sid;
    private static String plistMulti_option2_sid;
    private static String plistMulti_opt3_exclusive_sid;

    private static String matrixSingleSelectSid;
    private static String matrixSingleRowStableId1;
    private static String matrixSingleRowStableId2;
    private static String matrixSingleOptionStableId1;
    private static String matrixSingleOptionStableId2;
    private static String matrixDefaultGroup;
    private static String matrixSingleGroup;

    private static String matrixMultiSelectSid;
    private static String matrixMultiRowStableId1;
    private static String matrixMultiRowStableId2;
    private static String matrixMultiOptionStableId1;
    private static String matrixMultiOptionStableId2;
    private static String matrixMultiOptionStableId3;
    private static String matrixMultiOptionStableId4;
    private static String matrixMultiGroup;

    private static FileQuestionDef fileQuestion;
    private static FileUpload upload1;
    private static FileUpload upload2;

    private static Map<QuestionType, List<String>> answerGuidsToDelete;

    public PatchFormAnswersRouteStandaloneTest(Integer runNumber) {

    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        Object[] first = {1};
        Object[] second = {2};
        return List.of(first, second);
    }

    @AfterClass
    public static void doSuiteTearDown() {
        TransactionWrapper.useTxn(handle -> {
            var fileDao = handle.attach(FileUploadDao.class);
            fileDao.deleteByIds(Set.of(upload1.getId(), upload2.getId()));
            handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(parentInstanceDto.getGuid());
        });
        IntegrationTestSuite.tearDown();
    }

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
        String endpoint = API.USER_ACTIVITY_ANSWERS
                .replace(PathParam.USER_GUID, userGuid)
                .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(PathParam.INSTANCE_GUID, "{instanceGuid}");
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + endpoint;

        answerGuidsToDelete = new HashMap<>();
        for (QuestionType type : QuestionType.values()) {
            answerGuidsToDelete.put(type, new ArrayList<>());
        }
    }

    private static void setupActivity(Handle handle) {
        long timestamp = Instant.now().toEpochMilli();

        boolStableId = "PATCH_BOOL_Q_" + timestamp;
        textStableId = "PATCH_TEXT_Q_" + timestamp;
        textStableId2 = "PATCH_TEXT_Q2_" + timestamp;
        textStableId3 = "PATCH_TEXT_Q3_" + timestamp;
        plistSingleSelectSid = "PATCH_PLIST_SINGLE_Q_" + timestamp;
        plistSingle_option1_sid = "PLIST_SINGLE_OPT_1_" + timestamp;
        plistSingle_option2_sid = "PLIST_SINGLE_OPT_2_" + timestamp;
        plistMultiSelectSid = "PATCH_PLIST_MULTI_Q_" + timestamp;
        plistMulti_option1_sid = "PLIST_MULTI_OPT_1_" + timestamp;
        plistMulti_option2_sid = "PLIST_MULTI_OPT_2_" + timestamp;
        plistMulti_opt3_exclusive_sid = "PLIST_MULTI_OPT3_EXCLUSIVE_" + timestamp;
        dateTextSid = "PATCH_DATE_TEXT_Q_" + timestamp;
        dateSingleTextSid = "PATCH_DATE_SINGLETEXT_Q_" + timestamp;
        datePicklistSid = "PATCH_DATE_PICKLIST_Q_" + timestamp;
        matrixSingleSelectSid = "MATRIX_SINGLE_Q_" + timestamp;
        matrixSingleRowStableId1 = "MATRIX_SINGLE_ROW1" + timestamp;
        matrixSingleRowStableId2 = "MATRIX_SINGLE_ROW2" + timestamp;
        matrixSingleOptionStableId1 = "MATRIX_SINGLE_OPT1" + timestamp;
        matrixSingleOptionStableId2 = "MATRIX_SINGLE_OPT2" + timestamp;
        matrixSingleGroup = "MATRIX_SINGLE_GROUP" + timestamp;
        matrixDefaultGroup = "DEFAULT_GROUP" + timestamp;
        matrixMultiSelectSid = "MATRIX_MULTI_Q_" + timestamp;
        matrixMultiRowStableId1 = "MATRIX_MULTI_ROW1" + timestamp;
        matrixMultiRowStableId2 = "MATRIX_MULTI_ROW2" + timestamp;
        matrixMultiOptionStableId1 = "MATRIX_MULTI_OPT1" + timestamp;
        matrixMultiOptionStableId2 = "MATRIX_MULTI_OPT2" + timestamp;
        matrixMultiOptionStableId3 = "MATRIX_MULTI_OPT3" + timestamp;
        matrixMultiOptionStableId4 = "MATRIX_MULTI_OPT4" + timestamp;
        matrixMultiGroup = "MATRIX_MULTI_GROUP" + timestamp;
        childTextStableId = "PATCH_TEXT_CHILD_" + timestamp;
        childDateStableId = "PATCH__DATE_CHILD" + timestamp;
        compStabledId = "ANS_COMPOSITE_" + timestamp;
        essayTextStableId = "PATCH_ESSAY_TEXT_Q_" + timestamp;
        agreementSid = "AGREEMENT_Q" + timestamp;
        numericIntegerSid = "PATCH_NUMERIC_INTEGER_Q" + timestamp;
        numericIntegerReqSid = "PATCH_NUMERIC_INTEGER_REQ" + timestamp;
        numericIntegerWithMultipleRulesSid = "PATCH_NUM_INT_W_MULT_RULES" + timestamp;

        decimalIntegerSid = "PATCH_DECIMAL_Q" + timestamp;
        decimalIntegerReqSid = "PATCH_DECIMAL_REQ" + timestamp;
        decimalIntegerWithMultipleRulesSid = "PATCH_DECIMAL_W_MULT_RULES" + timestamp;

        equationSid = "PATCH_EQUATION_Q" + timestamp;

        BoolQuestionDef b1 = BoolQuestionDef.builder(boolStableId, newTemplate(), newTemplate(), newTemplate()).build();
        FormSectionDef boolSection = new FormSectionDef(null, TestUtil.wrapQuestions(b1));

        TextQuestionDef t1 = TextQuestionDef.builder(TextInputType.TEXT, textStableId, newTemplate()).build();
        FormSectionDef textSection = new FormSectionDef(null, TestUtil.wrapQuestions(t1));

        TextQuestionDef t2 = TextQuestionDef.builder(TextInputType.ESSAY, textStableId2, newTemplate()).build();
        FormSectionDef textSection2 = new FormSectionDef(null, TestUtil.wrapQuestions(t2));

        TextQuestionDef t3 = TextQuestionDef.builder(TextInputType.EMAIL, textStableId3, newTemplate()).build();
        FormSectionDef textSection3 = new FormSectionDef(null, TestUtil.wrapQuestions(t3));

        PicklistQuestionDef p1 = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.LIST, plistSingleSelectSid, newTemplate())
                .addOption(new PicklistOptionDef(plistSingle_option1_sid, newTemplate()))
                .addOption(new PicklistOptionDef(plistSingle_option2_sid, newTemplate(), newTemplate()))
                .build();

        PicklistQuestionDef p2 = PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, plistMultiSelectSid, newTemplate())
                .addOption(new PicklistOptionDef(plistMulti_option1_sid, newTemplate()))
                .addOption(new PicklistOptionDef(plistMulti_option2_sid, newTemplate()))
                .addOption(new PicklistOptionDef(plistMulti_opt3_exclusive_sid, newTemplate())
                        .toBuilder().isExclusive(true).build())
                .addValidation(new RequiredRuleDef(newTemplate()))
                .build();
        FormSectionDef plistSection = new FormSectionDef(null, TestUtil.wrapQuestions(p1, p2));

        DateQuestionDef d1 = DateQuestionDef.builder(DateRenderMode.TEXT, dateTextSid, newTemplate())
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .build();

        DateQuestionDef d2 = DateQuestionDef.builder(DateRenderMode.SINGLE_TEXT, dateSingleTextSid, newTemplate())
                .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                .addValidation(new DateFieldRequiredRuleDef(RuleType.YEAR_REQUIRED, newTemplate()))
                .addValidation(new DateRangeRuleDef(newTemplate(), LocalDate.now(), null, false))
                .build();

        DateRangeRuleDef rangeRuleWithAllowSaveTrue = new DateRangeRuleDef(newTemplate(), LocalDate.of(2016, 3, 14), null, false);
        rangeRuleWithAllowSaveTrue.setAllowSave(true);
        DateQuestionDef d3 = DateQuestionDef.builder(DateRenderMode.PICKLIST, datePicklistSid, newTemplate())
                .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR)
                .addValidation(new DateRangeRuleDef(newTemplate(), null, LocalDate.of(2018, 3, 14), false))
                .addValidation(rangeRuleWithAllowSaveTrue)
                .build();
        FormSectionDef dateSection = new FormSectionDef(null, TestUtil.wrapQuestions(d1, d2, d3));

        MatrixQuestionDef mqf1 = MatrixQuestionDef.builder(MatrixSelectMode.SINGLE, matrixSingleSelectSid, newTemplate())
                .addRow(new MatrixRowDef(matrixSingleRowStableId1, newTemplate()))
                .addRow(new MatrixRowDef(matrixSingleRowStableId2, newTemplate()))
                .addOption(new MatrixOptionDef(matrixSingleOptionStableId1, newTemplate(), matrixDefaultGroup))
                .addOption(new MatrixOptionDef(matrixSingleOptionStableId2, newTemplate(), matrixSingleGroup))
                .addGroups(List.of(
                        new MatrixGroupDef(matrixDefaultGroup, null),
                        new MatrixGroupDef(matrixSingleGroup, newTemplate())))
                .build();

        MatrixQuestionDef mqf2 = MatrixQuestionDef.builder(MatrixSelectMode.MULTIPLE, matrixMultiSelectSid, newTemplate())
                .addRow(new MatrixRowDef(matrixMultiRowStableId1, newTemplate()))
                .addRow(new MatrixRowDef(matrixMultiRowStableId2, newTemplate()))
                .addOption(new MatrixOptionDef(matrixMultiOptionStableId1, newTemplate(), matrixDefaultGroup))
                .addOption(new MatrixOptionDef(matrixMultiOptionStableId2, newTemplate(), matrixMultiGroup))
                .addOption(new MatrixOptionDef(matrixMultiOptionStableId3, newTemplate(), matrixMultiGroup))
                .addOption(MatrixOptionDef.buildExclusive(matrixMultiOptionStableId4, newTemplate(), matrixMultiGroup))
                .addGroups(List.of(
                        new MatrixGroupDef(matrixDefaultGroup, null),
                        new MatrixGroupDef(matrixMultiGroup, newTemplate())))
                .addValidation(new RequiredRuleDef(newTemplate()))
                .build();

        FormSectionDef matrixSection = new FormSectionDef(null, TestUtil.wrapQuestions(mqf1, mqf2));

        TextQuestionDef childTextDef = buildTextQuestionDef(childTextStableId);

        DateQuestionDef childDateDef = buildDateQuestionDef(childDateStableId);

        CompositeQuestionDef compQ = CompositeQuestionDef.builder()
                .setStableId(compStabledId)
                .setPrompt(new Template(TemplateType.TEXT, null, "Comp1"))
                .addChildrenQuestions(childTextDef, childDateDef)
                .setAllowMultiple(true)
                .setAddButtonTemplate(new Template(TemplateType.TEXT, null, "Add Button Text"))
                .setAdditionalItemTemplate(new Template(TemplateType.TEXT, null, "Next Item below..."))
                .build();

        FormSectionDef compositeSection = new FormSectionDef(null, TestUtil.wrapQuestions(compQ));

        TextQuestionDef e2 = TextQuestionDef.builder(TextInputType.ESSAY, essayTextStableId, newTemplate())
                .build();
        FormSectionDef essayTextSection = new FormSectionDef(null, TestUtil.wrapQuestions(e2));

        AgreementQuestionDef a1 = new AgreementQuestionDef(agreementSid,
                false,
                newTemplate(),
                null,
                newTemplate(),
                newTemplate(),
                Arrays.asList(new RequiredRuleDef(newTemplate())),
                true,
                false);
        FormSectionDef agreementSection = new FormSectionDef(null, TestUtil.wrapQuestions(a1));

        numericQuestionDef = NumericQuestionDef
                .builder(numericIntegerSid, newTemplate())
                .addValidation(new IntRangeRuleDef(null, 5L, 100L))
                .build();
        NumericQuestionDef n2 = NumericQuestionDef
                .builder(numericIntegerReqSid, newTemplate())
                .addValidation(new RequiredRuleDef(null))
                .build();
        NumericQuestionDef n3 = NumericQuestionDef
                .builder(numericIntegerWithMultipleRulesSid, newTemplate())
                .addValidation(new IntRangeRuleDef(null, 5L, 100L))
                .addValidation(new IntRangeRuleDef(null, 200L, 500L))
                .build();
        FormSectionDef numericSection = new FormSectionDef(null, TestUtil.wrapQuestions(numericQuestionDef, n2, n3));

        decimalQuestionDef = DecimalQuestionDef
                .builder(decimalIntegerSid, newTemplate())
                .addValidation(new DecimalRangeRuleDef(null, new DecimalDef(5), new DecimalDef(100)))
                .build();
        DecimalQuestionDef dec2 = DecimalQuestionDef
                .builder(decimalIntegerReqSid, newTemplate())
                .addValidation(new RequiredRuleDef(null))
                .build();
        DecimalQuestionDef dec3 = DecimalQuestionDef
                .builder(decimalIntegerWithMultipleRulesSid, newTemplate())
                .addValidation(new DecimalRangeRuleDef(null, new DecimalDef(5), new DecimalDef(100)))
                .addValidation(new DecimalRangeRuleDef(null, new DecimalDef(200), new DecimalDef(500)))
                .build();
        FormSectionDef decimalSection = new FormSectionDef(null, TestUtil.wrapQuestions(decimalQuestionDef, dec2, dec3));

        equationQuestionDef = EquationQuestionDef
                .builder()
                .stableId(equationSid)
                .promptTemplate(newTemplate())
                .questionType(QuestionType.EQUATION)
                .expression(String.format("2 * %s", decimalIntegerSid))
                .build();

        FormSectionDef equationSection = new FormSectionDef(null, TestUtil.wrapQuestions(equationQuestionDef));

        fileQuestion = FileQuestionDef.builder("FILE" + timestamp, Template.text("file"))
                .setMaxFileSize(DEFAULT_MAX_FILE_SIZE_FOR_TEST)
                .build();
        var fileSection = new FormSectionDef(null, TestUtil.wrapQuestions(fileQuestion));

        String parentActCode = "PATCH_ANS_PARENT_" + timestamp;
        parentActivity = FormActivityDef.generalFormBuilder(parentActCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "parent activity " + parentActCode))
                .build();

        String code = "PATCH_ANS_ACT_" + timestamp;
        activity = FormActivityDef.generalFormBuilder(code, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity " + code))
                .setParentActivityCode(parentActCode)
                .addSections(
                        Arrays.asList(
                                boolSection, textSection, textSection2, textSection3, plistSection,
                                dateSection, compositeSection, essayTextSection, agreementSection,
                                numericSection, decimalSection, equationSection, fileSection, matrixSection
                        )
                )
                .build();
        activityVersionDto = handle.attach(ActivityDao.class)
                .insertActivity(parentActivity, List.of(activity),
                        RevisionMetadata.now(testData.getUserId(), "add " + code));
        parentInstanceDto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(parentActivity.getActivityId(), userGuid);

        long userId = testData.getUserId();
        long studyId = testData.getStudyId();
        var fileDao = handle.attach(FileUploadDao.class);
        upload1 = fileDao.createAuthorized(GuidUtils.randomFileUploadGuid(),
                studyId, userId, userId, "blob1", "text/plain", "file.txt", 123L);
        upload2 = fileDao.createAuthorized(GuidUtils.randomFileUploadGuid(),
                studyId, userId, userId, "blob2", "application/pdf", "file.pdf", 456L);
        fileDao.markVerified(upload1.getId());
        fileDao.markVerified(upload2.getId());
    }

    private static Template newTemplate() {
        return new Template(TemplateType.TEXT, null, "template " + Instant.now().toEpochMilli());
    }

    private static TextQuestionDef buildTextQuestionDef(String stableId) {
        return TextQuestionDef.builder().setStableId(stableId)
                .setInputType(TextInputType.TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "text prompt"))
                .addValidation(new LengthRuleDef(new Template(TemplateType.TEXT, null, "Get the length right!"), 1, 10))
                .build();
    }

    private static DateQuestionDef buildDateQuestionDef(String stableId) {
        return DateQuestionDef.builder().setStableId(stableId)
                .setRenderMode(DateRenderMode.TEXT)
                .setPrompt(new Template(TemplateType.TEXT, null, "date prompt"))
                .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                .build();
    }

    @Before
    public void refresh() {
        TransactionWrapper.useTxn(handle -> {
            instanceDto = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activity.getActivityId(), userGuid, userGuid, parentInstanceDto.getId());
            instanceGuid = instanceDto.getGuid();
            url = urlTemplate.replace("{instanceGuid}", instanceGuid);
        });
    }

    @After
    public void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            var answerDao = new AnswerCachedDao(handle);
            for (QuestionType type : QuestionType.values()) {
                for (String answerGuid : answerGuidsToDelete.get(type)) {
                    long id = answerDao.getAnswerSql().findDtoByGuid(answerGuid).get().getId();
                    answerDao.deleteAnswer(id);
                }
                answerGuidsToDelete.get(type).clear();
            }
            handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(instanceGuid);
        });
    }

    private boolean getBoolAnswerValue(String guid) {
        return TransactionWrapper.withTxn(handle ->
                ((BoolAnswer) new AnswerCachedDao(handle).findAnswerByGuid(guid).get()).getValue());
    }

    private String createAnswerAndDeferCleanup(Handle handle, Answer answer) {
        String guid = new AnswerCachedDao(handle)
                .createAnswer(testData.getUserId(), instanceDto.getId(), answer)
                .getAnswerGuid();
        assertNotNull(guid);
        answerGuidsToDelete.get(answer.getQuestionType()).add(guid);
        return guid;
    }

    @Test
    public void testParentActivity_parentReadOnlyPreventsChildPatch() {
        TransactionWrapper.useTxn(handle -> assertEquals(1, handle.attach(JdbiActivityInstance.class)
                .updateIsReadonlyByGuid(true, parentInstanceDto.getGuid())));
        try {
            AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(10));
            PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
            givenAnswerPatchRequest(instanceGuid, data)
                    .then().assertThat()
                    .statusCode(422).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY))
                    .body("message", containsString("Parent activity instance"))
                    .body("message", containsString("read-only"));
        } finally {
            TransactionWrapper.useTxn(handle -> assertEquals(1, handle.attach(JdbiActivityInstance.class)
                    .updateIsReadonlyByGuid(null, parentInstanceDto.getGuid())));
        }
    }

    @Test
    public void testParentActivity_childPatchUpdatesParentStatusToInProgress() {
        TransactionWrapper.useTxn(handle -> {
            var instanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
            instanceStatusDao.deleteAllByInstanceGuid(parentInstanceDto.getGuid());
            instanceStatusDao.insertStatus(parentInstanceDto.getGuid(), InstanceStatusType.CREATED,
                    Instant.now().toEpochMilli(), testData.getUserGuid());
        });

        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(10));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().path("answers[0].answerGuid");
        answerGuidsToDelete.get(QuestionType.PICKLIST).add(guid);

        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDto actualParentInstanceDto = handle.attach(JdbiActivityInstance.class)
                    .getByActivityInstanceGuid(parentInstanceDto.getGuid()).get();
            assertEquals(InstanceStatusType.IN_PROGRESS, actualParentInstanceDto.getStatusType());
        });
    }

    @Test
    public void testPatch_activityNotFound() throws Exception {
        String testUrl = urlTemplate.replace("{instanceGuid}", "not-an-instance-guid");
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, testUrl, null);
        HttpResponse response = request.execute().returnResponse();
        assertEquals(404, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        ApiError resp = gson.fromJson(json, ApiError.class);
        assertEquals(ErrorCodes.ACTIVITY_NOT_FOUND, resp.getCode());
    }

    @Test
    public void testPatch_activityInstanceIsHidden() {
        TransactionWrapper.useTxn(handle -> assertEquals(1, handle.attach(ActivityInstanceDao.class)
                .bulkUpdateIsHiddenByActivityIds(testData.getUserId(), true, Set.of(activity.getActivityId()))));

        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(10));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                .body("message", containsString("is hidden"));
    }

    @Test
    public void testPatch_activityIsWriteOnce() {
        var oldWriteOnce = new AtomicBoolean(activity.isWriteOnce());
        TransactionWrapper.useTxn(handle -> {
            // Make activity write-once and instance COMPLETE to activate read-only state.
            assertEquals(1, handle.attach(JdbiActivity.class).updateWriteOnceById(activity.getActivityId(), true));
            handle.attach(ActivityInstanceStatusDao.class).insertStatus(
                    instanceGuid, InstanceStatusType.COMPLETE, Instant.now().toEpochMilli(), testData.getUserGuid());
        });

        // Activity definition is cached! Clear cache since we made change to definition.
        ActivityDefStore.getInstance().clear();

        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(10));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        try {
            givenAnswerPatchRequest(instanceGuid, data)
                    .then().assertThat()
                    .statusCode(422).contentType(ContentType.JSON)
                    .log().all()
                    .body("code", equalTo(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY))
                    .body("message", containsString(instanceGuid))
                    .body("message", containsString("read-only"));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                assertEquals(1, handle.attach(JdbiActivity.class).updateWriteOnceById(activity.getActivityId(), oldWriteOnce.get()));
            });
            ActivityDefStore.getInstance().clear();
        }
    }

    private void assert400AndBadPayloadResponse(String uri, String payload) throws Exception {
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, uri, payload);
        HttpResponse response = request.execute().returnResponse();
        assertEquals(400, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        ApiError resp = gson.fromJson(json, ApiError.class);
        assertEquals(ErrorCodes.BAD_PAYLOAD, resp.getCode());
    }

    @Test
    public void testPatch_invalidJsonPayload() throws Exception {
        String[] payloads = new String[] {
                null,
                "",
                " \\  \t\n     ",
                "123",
                "123.56",
                "some string",
                "[ 1, 2, 3 ]",
                "{ \"answers\": 123 }",
                "{ \"answers\": null }",
                "{ \"answers\": [ 1, 2, 3 ] }",
                "{ \"stableId\": \"abc\", \"answer\": true }"
        };
        for (String payload : payloads) {
            assert400AndBadPayloadResponse(url, payload);
        }
    }

    @Test
    public void testPatch_missingStableId() throws Exception {
        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(null, null, new JsonPrimitive(true)));
        assert400AndBadPayloadResponse(url, gson.toJson(data));
    }

    @Test
    public void testPatch_invalidStableId() throws Exception {
        String stableId = "non-exist-stable-id";
        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(stableId, null, new JsonPrimitive(true)));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(404, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        ApiError resp = gson.fromJson(json, ApiError.class);
        assertEquals(ErrorCodes.QUESTION_NOT_FOUND, resp.getCode());
    }

    @Test
    public void testPatch_unexpectedAnswerType() throws Exception {
        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, null, null));
        assert400AndBadPayloadResponse(url, gson.toJson(data));

        data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, null, JsonNull.INSTANCE));
        assert400AndBadPayloadResponse(url, gson.toJson(data));

        data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, null, new JsonPrimitive(123)));
        assert400AndBadPayloadResponse(url, gson.toJson(data));
    }

    @Test
    public void testPatch_invalidAnswerGuid() throws Exception {
        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, "non-answer-guid", new JsonPrimitive(true)));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(404, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        ApiError resp = gson.fromJson(json, ApiError.class);
        assertEquals(ErrorCodes.ANSWER_NOT_FOUND, resp.getCode());
    }

    @Test
    public void testPatch_noGuid_singleExistingAnswer() throws Exception {
        String answerGuid = TransactionWrapper.withTxn(handle -> {
            BoolAnswer answer = new BoolAnswer(null, boolStableId, null, false);
            return createAnswerAndDeferCleanup(handle, answer);
        });

        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, null, new JsonPrimitive(true)));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        PatchAnswerResponse resp = gson.fromJson(json, PatchAnswerResponse.class);
        assertEquals(1, resp.getAnswers().size());

        AnswerResponse ans = resp.getAnswers().get(0);
        assertEquals(boolStableId, ans.getQuestionStableId());
        assertEquals(answerGuid, ans.getAnswerGuid());
        assertTrue(getBoolAnswerValue(answerGuid));
    }

    @Test
    public void test_givenQuestionHasMultipleAnswers_whenEndpointIsCalled_thenItReturns500() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            Answer answer = new BoolAnswer(null, boolStableId, null, true);
            createAnswerAndDeferCleanup(handle, answer);

            answer = new BoolAnswer(null, boolStableId, null, false);
            createAnswerAndDeferCleanup(handle, answer);
        });

        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, null, new JsonPrimitive(true)));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(500, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        ApiError resp = gson.fromJson(json, ApiError.class);
        assertEquals(ErrorCodes.SERVER_ERROR, resp.getCode());
        assertTrue(Pattern.compile("found 2 answers instead").matcher(resp.getMessage()).find());
    }

    private void assert200AndNoAnswersResponse(String uri, String payload) throws Exception {
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, uri, payload);
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        PatchAnswerResponse resp = gson.fromJson(json, PatchAnswerResponse.class);
        assertEquals(0, resp.getAnswers().size());
    }

    @Test
    public void testPatch_noAnswerSubmissions() throws Exception {
        PatchAnswerPayload data = new PatchAnswerPayload(null);
        assert400AndBadPayloadResponse(url, gson.toJson(data));

        data = new PatchAnswerPayload(new ArrayList<>());
        assert200AndNoAnswersResponse(url, gson.toJson(data));
    }

    @Test
    public void testPatch_createCompositeAnswer() throws IOException {
        String textValue = "Hi there";
        AnswerSubmission childTextAnswer1 = new AnswerSubmission(childTextStableId, null,
                new JsonPrimitive(textValue));
        DateValue dateValue = new DateValue(2018, 8, 1);
        AnswerSubmission childDateAnswer1 = new AnswerSubmission(childDateStableId, null,
                gson.toJsonTree(dateValue));
        List<List<AnswerSubmission>> compListOfAnswers = new ArrayList<>();
        compListOfAnswers.add(Arrays.asList(childTextAnswer1, childDateAnswer1));
        AnswerSubmission compAnswerSubmission = new AnswerSubmission(compStabledId, null,
                gson.toJsonTree(compListOfAnswers));
        PatchAnswerPayload data = new PatchAnswerPayload();

        data.addSubmission(compAnswerSubmission);

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        PatchAnswerResponse resp = gson.fromJson(json, PatchAnswerResponse.class);
        assertEquals(1, resp.getAnswers().size());
        AnswerResponse firstAnswer = resp.getAnswers().size() > 0 ? resp.getAnswers().get(0) : null;
        assertNotNull(firstAnswer);
        assertNotNull(firstAnswer.getAnswerGuid());
        assertNotNull(firstAnswer.getQuestionStableId());

        TransactionWrapper.useTxn(handle -> {
            Optional<Answer> savedCompositeAnswer = new AnswerCachedDao(handle)
                    .findAnswerByInstanceGuidAndQuestionStableId(instanceGuid, firstAnswer.getQuestionStableId());
            assertTrue(savedCompositeAnswer.isPresent());
            CompositeAnswer compositeAnswer = (CompositeAnswer) savedCompositeAnswer.get();
            List<AnswerRow> childAnswers = compositeAnswer.getValue();
            assertEquals(compListOfAnswers.size(), childAnswers.size());
            for (AnswerRow rowOfAnswers : childAnswers) {
                for (Answer childAnswer : rowOfAnswers.getValues()) {
                    assertNotNull(childAnswer.getQuestionStableId());
                    assertNotNull(childAnswer.getAnswerGuid());
                    assertNotNull(childAnswer.getAnswerId());
                }
            }
            assertEquals(textValue, childAnswers.get(0).getValues().get(0).getValue());
            assertEquals(dateValue, childAnswers.get(0).getValues().get(1).getValue());
        });


        //try updating just child answer. should return 404
        data = new PatchAnswerPayload();
        data.addSubmission(childTextAnswer1);

        request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response404 = request.execute().returnResponse();
        assertEquals(HttpStatus.NOT_FOUND_404, response404.getStatusLine().getStatusCode());
        String bodyToString = EntityUtils.toString(response404.getEntity());
        ApiError error = gson.fromJson(bodyToString, ApiError.class);
        assertEquals(ErrorCodes.QUESTION_NOT_FOUND, error.getCode());
        assertEquals("Only entire Composite question answer can be updated", error.getMessage());

        this.answerGuidsToDelete.put(QuestionType.COMPOSITE, new ArrayList<>(Arrays.asList(resp.getAnswers().get(0)
                .getAnswerGuid())));
    }

    @Test
    public void testPatch_createCompositeAnswer_noChildAnswers() throws IOException {
        AnswerSubmission compAnswerSubmission = new AnswerSubmission(compStabledId, null, null);
        PatchAnswerPayload data = new PatchAnswerPayload();

        data.addSubmission(compAnswerSubmission);
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(HttpStatus.BAD_REQUEST_400, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testPatch_createCompositeAnswer_emptyChildAnswerList() throws IOException {
        AnswerSubmission compAnswerSubmission = new AnswerSubmission(compStabledId, null, new JsonArray());
        PatchAnswerPayload data = new PatchAnswerPayload();

        data.addSubmission(compAnswerSubmission);
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(HttpStatus.BAD_REQUEST_400, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testPatch_createCompositeAnswer_wrongChildAnswer() throws IOException {
        String textValue = "Hi there";
        AnswerSubmission childTextAnswerWithWrongStableId = new AnswerSubmission(textStableId, null,
                new JsonPrimitive(textValue));
        List<List<AnswerSubmission>> compListOfAnswers = new ArrayList<>();
        compListOfAnswers.add(Arrays.asList(childTextAnswerWithWrongStableId));
        AnswerSubmission compAnswerSubmission = new AnswerSubmission(compStabledId, null,
                gson.toJsonTree(compListOfAnswers));
        PatchAnswerPayload data = new PatchAnswerPayload();

        data.addSubmission(compAnswerSubmission);
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(HttpStatus.BAD_REQUEST_400, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testPatch_createCompositeAnswer_invalidDate() throws IOException {
        String textValue = "Hi there";
        AnswerSubmission childTextAnswer1 = new AnswerSubmission(childTextStableId, null, new JsonPrimitive(textValue));
        //Set the month for the date to be 666 !!!
        DateValue dateValue = new DateValue(2018, 666, 1);
        AnswerSubmission childDateAnswer1 = new AnswerSubmission(childDateStableId, null, gson.toJsonTree(dateValue));
        List<List<AnswerSubmission>> compListOfAnswers = new ArrayList<>();
        compListOfAnswers.add(Arrays.asList(childTextAnswer1, childDateAnswer1));
        AnswerSubmission compAnswerSubmission = new AnswerSubmission(compStabledId, null,
                gson.toJsonTree(compListOfAnswers));
        PatchAnswerPayload data = new PatchAnswerPayload();

        data.addSubmission(compAnswerSubmission);

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(HttpStatus.BAD_REQUEST_400, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        AnswerValidationError validationError = gson.fromJson(json, AnswerValidationError.class);
        assertNotNull(validationError);
        assertNotNull(validationError.getCode());
    }

    @Test
    public void testPatch_createCompositeAnswer_textTooLong() throws IOException {
        String textValue = "This is an answer that is way too long!!!!";
        AnswerSubmission childTextAnswer1 = new AnswerSubmission(childTextStableId, null, new JsonPrimitive(textValue));
        DateValue dateValue = new DateValue(2018, 2, 1);
        AnswerSubmission childDateAnswer1 = new AnswerSubmission(childDateStableId, null, gson.toJsonTree(dateValue));
        List<List<AnswerSubmission>> compListOfAnswers = new ArrayList<>();
        compListOfAnswers.add(Arrays.asList(childTextAnswer1, childDateAnswer1));
        AnswerSubmission compAnswerSubmission = new AnswerSubmission(compStabledId, null,
                gson.toJsonTree(compListOfAnswers));
        PatchAnswerPayload data = new PatchAnswerPayload();

        data.addSubmission(compAnswerSubmission);

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY_422, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        AnswerValidationError validationError = gson.fromJson(json, AnswerValidationError.class);
        assertNotNull(validationError);
        assertNotNull(validationError.getCode());
        assertNotNull(validationError.getViolations().size() == 1);
        assertNotNull(validationError.getViolations().get(0).getRules().size() == 1);
        assertEquals(compStabledId, validationError.getViolations().get(0).getStableId());
    }

    @Test
    public void testPatch_createCompositeAnswer_missingValueInRow() throws IOException {
        //First going to try to include a child answer, but has null value
        AnswerSubmission childTextAnswer1 = new AnswerSubmission(childTextStableId, null, null);
        DateValue dateValue = new DateValue(2018, 2, 1);
        AnswerSubmission childDateAnswer1 = new AnswerSubmission(childDateStableId, null, gson.toJsonTree(dateValue));
        List<List<AnswerSubmission>> compListOfAnswers = new ArrayList<>();
        compListOfAnswers.add(Arrays.asList(childTextAnswer1, childDateAnswer1));
        //Let's add a second row to make things fun
        String secondRowStringValue = "hi there";
        AnswerSubmission childTextAnswer2 = new AnswerSubmission(childTextStableId, null, new JsonPrimitive(secondRowStringValue));

        AnswerSubmission childDateAnswer2 = new AnswerSubmission(childDateStableId, null, null);
        compListOfAnswers.add(Arrays.asList(childTextAnswer2, childDateAnswer2));
        AnswerSubmission compAnswerSubmission = new AnswerSubmission(compStabledId, null,
                gson.toJsonTree(compListOfAnswers));
        PatchAnswerPayload data = new PatchAnswerPayload();

        data.addSubmission(compAnswerSubmission);

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(HttpStatus.OK_200, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        PatchAnswerResponse resp = gson.fromJson(json, PatchAnswerResponse.class);
        assertEquals(1, resp.getAnswers().size());

        AnswerResponse compositeAnswerResponse = resp.getAnswers().get(0);
        assertEquals(compStabledId, compositeAnswerResponse.getQuestionStableId());
        assertNotNull(compositeAnswerResponse.getAnswerGuid());

        this.answerGuidsToDelete.put(QuestionType.COMPOSITE, new ArrayList<>(Arrays.asList(compositeAnswerResponse.getAnswerGuid())));

        TransactionWrapper.useTxn(handle -> {
            var jdbiQuestion = handle.attach(JdbiQuestion.class);
            Optional<QuestionDto> optCompQuestion = jdbiQuestion
                    .findDtoByStableIdAndInstanceGuid(compStabledId, instanceGuid);
            assertTrue(optCompQuestion.isPresent());
            Question compositeQuestionWithAnswers = handle.attach(QuestionDao.class)
                    .getQuestionByIdAndActivityInstanceGuid(optCompQuestion.get().getId(), instanceGuid,
                            instanceDto.getCreatedAtMillis(),
                            LanguageStore.getDefault().getId());
            assertTrue(compositeQuestionWithAnswers instanceof CompositeQuestion);
            List<CompositeAnswer> savedCompositeAnswers = ((CompositeQuestion) compositeQuestionWithAnswers).getAnswers();
            assertEquals(1, savedCompositeAnswers.size());
            assertTrue(savedCompositeAnswers.get(0) instanceof CompositeAnswer);
            CompositeAnswer compositeAnswer = (CompositeAnswer) savedCompositeAnswers.get(0);
            List<AnswerRow> childAnswers = compositeAnswer.getValue();
            assertEquals(compListOfAnswers.size(), childAnswers.size());
            AnswerRow firstRow = childAnswers.get(0);
            //this is the first element in the first row. Should be null
            assertNull(firstRow.getValues().get(0));
            //the second element should be the date
            assertEquals(childDateStableId, firstRow.getValues().get(1).getQuestionStableId());
            assertNotNull(firstRow.getValues().get(1).getAnswerGuid());
            assertEquals(dateValue, childAnswers.get(0).getValues().get(1).getValue());
            AnswerRow secondRow = childAnswers.get(1);
            assertEquals(QuestionType.TEXT, secondRow.getValues().get(0).getQuestionType());
            assertEquals(secondRowStringValue, secondRow.getValues().get(0).getValue());
        });
    }

    @Test
    public void testPatch_updateBoolAnswer() throws Exception {
        String answerGuid = TransactionWrapper.withTxn(handle -> {
            BoolAnswer answer = new BoolAnswer(null, boolStableId, null, false);
            return createAnswerAndDeferCleanup(handle, answer);
        });

        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, answerGuid, new JsonPrimitive(true)));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        PatchAnswerResponse resp = gson.fromJson(json, PatchAnswerResponse.class);
        assertEquals(1, resp.getAnswers().size());

        AnswerResponse ans = resp.getAnswers().get(0);
        assertEquals(boolStableId, ans.getQuestionStableId());
        assertEquals(answerGuid, ans.getAnswerGuid());
        assertTrue(getBoolAnswerValue(answerGuid));
    }

    @Test
    public void testPatch_updateTextAnswer() throws Exception {
        String answerGuid = TransactionWrapper.withTxn(handle -> {
            TextAnswer answer = new TextAnswer(null, textStableId, null, "old value");
            return createAnswerAndDeferCleanup(handle, answer);
        });

        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(textStableId, answerGuid, new JsonPrimitive("hi there")));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        PatchAnswerResponse resp = gson.fromJson(json, PatchAnswerResponse.class);
        assertEquals(1, resp.getAnswers().size());

        AnswerResponse ans = resp.getAnswers().get(0);
        assertEquals(textStableId, ans.getQuestionStableId());
        assertEquals(answerGuid, ans.getAnswerGuid());
    }

    @Test
    public void testPatch_updateTextAnswerEmailInput() throws Exception {
        String answerGuid = TransactionWrapper.withTxn(handle -> {
            TextAnswer answer = new TextAnswer(null, textStableId3, null, "abc123abc@gmail.com");
            return createAnswerAndDeferCleanup(handle, answer);
        });

        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(textStableId3, answerGuid, new JsonPrimitive("new_abc123abc@gmail.com")));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        PatchAnswerResponse resp = gson.fromJson(json, PatchAnswerResponse.class);
        assertEquals(1, resp.getAnswers().size());

        AnswerResponse ans = resp.getAnswers().get(0);
        assertEquals(textStableId3, ans.getQuestionStableId());
        assertEquals(answerGuid, ans.getAnswerGuid());

        //invalid email format
        data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(textStableId3, answerGuid, new JsonPrimitive("newabc123abc_invalid@gmail")));

        request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        response = request.execute().returnResponse();
        assertEquals(422, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testPatch_updateTextAnswer_tempuser_unauthorized() throws Exception {
        String answerGuid = TransactionWrapper.withTxn(handle -> {
            TextAnswer answer = new TextAnswer(null, textStableId, null, "old value");

            //set as temp user
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(userGuid), Instant.now().toEpochMilli() + 10000);

            return createAnswerAndDeferCleanup(handle, answer);
        });

        try {
            PatchAnswerPayload data = new PatchAnswerPayload();
            data.addSubmission(new AnswerSubmission(textStableId, answerGuid, new JsonPrimitive("hi there")));

            Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
            HttpResponse response = request.execute().returnResponse();
            assertEquals(401, response.getStatusLine().getStatusCode());
        } finally {
            //revert back temp user update
            TransactionWrapper.useTxn(handle -> {
                JdbiUser jdbiUser = handle.attach(JdbiUser.class);
                jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(userGuid), null);
            });
        }
    }

    @Test
    public void testPatch_updateTextAnswer_tempuser_authorized() throws Exception {
        String answerGuid = TransactionWrapper.withTxn(handle -> {
            TextAnswer answer = new TextAnswer(null, textStableId, null, "old value");
            //set as temp user and allow_unauthorized true
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(userGuid), Instant.now().toEpochMilli() + 10000);
            handle.attach(JdbiActivity.class).updateAllowUnauthenticatedById(activity.getActivityId(), true);
            return createAnswerAndDeferCleanup(handle, answer);
        });

        try {
            PatchAnswerPayload data = new PatchAnswerPayload();
            data.addSubmission(new AnswerSubmission(textStableId, answerGuid, new JsonPrimitive("hi there")));

            Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
            HttpResponse response = request.execute().returnResponse();
            assertEquals(200, response.getStatusLine().getStatusCode());

            String json = EntityUtils.toString(response.getEntity());
            PatchAnswerResponse resp = gson.fromJson(json, PatchAnswerResponse.class);
            assertEquals(1, resp.getAnswers().size());

            AnswerResponse ans = resp.getAnswers().get(0);
            assertEquals(textStableId, ans.getQuestionStableId());
            assertEquals(answerGuid, ans.getAnswerGuid());
        } finally {
            //revert back temp user update
            TransactionWrapper.useTxn(handle -> {
                JdbiUser jdbiUser = handle.attach(JdbiUser.class);
                jdbiUser.updateExpiresAtById(jdbiUser.getUserIdByGuid(userGuid), null);
                handle.attach(JdbiActivity.class).updateAllowUnauthenticatedById(activity.getActivityId(), false);
            });
        }
    }

    @Test
    public void testPatch_picklistAnswer_missingOptionList() {
        PatchAnswerPayload payload = new PatchAnswerPayload();
        payload.addSubmission(new AnswerSubmission(plistSingleSelectSid, null, JsonNull.INSTANCE));

        givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void testPatch_picklistAnswer_newAnswer() {
        PatchAnswerPayload payload = createPicklistPayload(plistSingleSelectSid, null,
                new SelectedPicklistOption(plistSingle_option1_sid));

        String guid = givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(plistSingleSelectSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.PICKLIST).add(guid);
        PicklistAnswer answer = (PicklistAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(guid, answer.getAnswerGuid());
        assertEquals(plistSingleSelectSid, answer.getQuestionStableId());
        assertEquals(1, answer.getValue().size());
        assertEquals(plistSingle_option1_sid, answer.getValue().get(0).getStableId());
    }

    @Test
    public void testPatch_picklistAnswer_updateAnswer() {
        String answerGuid = TransactionWrapper.withTxn(handle -> {
            List<SelectedPicklistOption> selected = new ArrayList<>();
            selected.add(new SelectedPicklistOption(plistSingle_option1_sid));
            PicklistAnswer answer = new PicklistAnswer(null, plistSingleSelectSid, null, selected);
            return createAnswerAndDeferCleanup(handle, answer);
        });

        PatchAnswerPayload payload = createPicklistPayload(plistSingleSelectSid, answerGuid,
                new SelectedPicklistOption(plistSingle_option2_sid));
        givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(plistSingleSelectSid))
                .body("answers[0].answerGuid", equalTo(answerGuid));

        PicklistAnswer answer = (PicklistAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(answerGuid).get());

        assertNotNull(answer);
        assertEquals(1, answer.getValue().size());
        assertEquals(plistSingle_option2_sid, answer.getValue().get(0).getStableId());
        assertNull(answer.getValue().get(0).getDetailText());
    }

    @Test
    public void testPatch_picklistAnswer_optionDetails() {
        PatchAnswerPayload payload = createPicklistPayload(plistSingleSelectSid, null,
                new SelectedPicklistOption(plistSingle_option2_sid, "some details"));

        String guid = givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(plistSingleSelectSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.PICKLIST).add(guid);
        PicklistAnswer answer = (PicklistAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(1, answer.getValue().size());
        assertEquals(plistSingle_option2_sid, answer.getValue().get(0).getStableId());
        assertEquals("some details", answer.getValue().get(0).getDetailText());
    }

    @Test
    public void testPatch_picklistAnswer_optionDetails_notAllowed() {
        PatchAnswerPayload payload = createPicklistPayload(plistSingleSelectSid, null,
                new SelectedPicklistOption(plistSingle_option1_sid, "details"));

        givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString(plistSingle_option1_sid))
                .body("message", containsString("does not allow details"));
    }

    @Test
    public void testPatch_picklistAnswer_optionDetails_overLengthLimit() {
        String details = StringUtils.repeat("0123456789", 50) + "123456";
        assertEquals(506, details.length());

        PatchAnswerPayload payload = createPicklistPayload(plistSingleSelectSid, null,
                new SelectedPicklistOption(plistSingle_option2_sid, details));

        givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", containsString("length must be between 0 and 500"));
    }

    @Test
    public void testPatch_picklistAnswer_singleSelect_onlyAlloweOne() {
        PatchAnswerPayload payload = createPicklistPayload(plistSingleSelectSid, null,
                new SelectedPicklistOption(plistSingle_option1_sid),
                new SelectedPicklistOption(plistSingle_option2_sid));

        givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("does not allow more than one"));
    }

    @Test
    public void testPatch_picklistAnswer_multiSelect_allowMultiple() {
        PatchAnswerPayload payload = createPicklistPayload(plistMultiSelectSid, null,
                new SelectedPicklistOption(plistMulti_option1_sid),
                new SelectedPicklistOption(plistMulti_option2_sid));

        String guid = givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(plistMultiSelectSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.PICKLIST).add(guid);
        PicklistAnswer answer = (PicklistAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(2, answer.getValue().size());
    }

    @Test
    public void testPatch_picklistAnswer_emptyListAllowed_whenNotRequired() {
        PatchAnswerPayload payload = createPicklistPayload(plistSingleSelectSid, null);

        String guid = givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(plistSingleSelectSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.PICKLIST).add(guid);
        PicklistAnswer answer = (PicklistAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(0, answer.getValue().size());
    }

    @Test
    public void testPatch_picklistAnswer_needOne_whenRequired() {
        PatchAnswerPayload payload = createPicklistPayload(plistMultiSelectSid, null);

        givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(plistMultiSelectSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.REQUIRED.name()));
    }

    @Test
    public void testPatch_picklistAnswer_exclusive_acceptedWhenOnlyOneSelected() {
        PatchAnswerPayload payload = createPicklistPayload(plistMultiSelectSid, null,
                new SelectedPicklistOption(plistMulti_opt3_exclusive_sid));

        String guid = givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(plistMultiSelectSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.PICKLIST).add(guid);
        PicklistAnswer answer = (PicklistAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(1, answer.getValue().size());
        assertEquals(plistMulti_opt3_exclusive_sid, answer.getValue().get(0).getStableId());
    }

    @Test
    public void testPatch_picklistAnswer_exclusive_rejectedWhenMultipleSelected() {
        PatchAnswerPayload payload = createPicklistPayload(plistMultiSelectSid, null,
                new SelectedPicklistOption(plistMulti_option1_sid),
                new SelectedPicklistOption(plistMulti_opt3_exclusive_sid));

        givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("contains an exclusive option"));
    }

    @Test
    public void testPatch_picklistAnswer_unknownOption() {
        PatchAnswerPayload payload = createPicklistPayload(plistMultiSelectSid, null,
                new SelectedPicklistOption("UNKNOWN"));

        givenPicklistRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NO_SUCH_ELEMENT));
    }

    private PatchAnswerPayload createPicklistPayload(String questionStableId, String answerGuid, SelectedPicklistOption... selected) {
        PatchAnswerPayload payload = new PatchAnswerPayload();
        payload.addSubmission(new AnswerSubmission(questionStableId, answerGuid, gson.toJsonTree(selected)));
        return payload;
    }

    private Response givenPicklistRequest(String instanceGuid, PatchAnswerPayload data) {
        return given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceGuid)
                .body(data, ObjectMapperType.GSON)
                .when().patch(urlTemplate);
    }

    @Test
    public void testPatch_matrixAnswer_missingOptionList() {
        PatchAnswerPayload payload = new PatchAnswerPayload();
        payload.addSubmission(new AnswerSubmission(matrixSingleSelectSid, null, JsonNull.INSTANCE));

        givenMatrixRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void testPatch_matrixAnswer_newAnswer() {
        PatchAnswerPayload payload = createMatrixPayload(matrixSingleSelectSid, null,
                new SelectedMatrixCell(matrixSingleRowStableId1, matrixSingleOptionStableId1, matrixDefaultGroup),
                new SelectedMatrixCell(matrixSingleRowStableId2, matrixSingleOptionStableId2, matrixSingleGroup));

        String guid = givenMatrixRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(matrixSingleSelectSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.MATRIX).add(guid);
        MatrixAnswer answer = (MatrixAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(guid, answer.getAnswerGuid());
        assertEquals(matrixSingleSelectSid, answer.getQuestionStableId());
        assertEquals(2, answer.getValue().size());

        assertEquals(matrixSingleOptionStableId1, answer.getValue().get(0).getOptionStableId());
        assertEquals(matrixSingleRowStableId1, answer.getValue().get(0).getRowStableId());
        assertEquals(matrixDefaultGroup, answer.getValue().get(0).getGroupStableId());

        assertEquals(matrixSingleOptionStableId2, answer.getValue().get(1).getOptionStableId());
        assertEquals(matrixSingleRowStableId2, answer.getValue().get(1).getRowStableId());
        assertEquals(matrixSingleGroup, answer.getValue().get(1).getGroupStableId());
    }

    @Test
    public void testPatch_matrixAnswer_updateAnswer() {
        String answerGuid = TransactionWrapper.withTxn(handle ->
                createAnswerAndDeferCleanup(handle, new MatrixAnswer(null, matrixSingleSelectSid, null, List.of(
                        new SelectedMatrixCell(matrixSingleRowStableId1, matrixSingleOptionStableId1, matrixDefaultGroup)
                ))));

        PatchAnswerPayload payload = createMatrixPayload(matrixSingleSelectSid, answerGuid,
                new SelectedMatrixCell(matrixSingleRowStableId2, matrixSingleOptionStableId2, matrixSingleGroup));
        givenMatrixRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(matrixSingleSelectSid))
                .body("answers[0].answerGuid", equalTo(answerGuid));

        MatrixAnswer answer = (MatrixAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(answerGuid).get());

        assertNotNull(answer);
        assertEquals(answerGuid, answer.getAnswerGuid());
        assertEquals(matrixSingleSelectSid, answer.getQuestionStableId());
        assertEquals(1, answer.getValue().size());

        assertEquals(matrixSingleOptionStableId2, answer.getValue().get(0).getOptionStableId());
        assertEquals(matrixSingleRowStableId2, answer.getValue().get(0).getRowStableId());
        assertEquals(matrixSingleGroup, answer.getValue().get(0).getGroupStableId());
    }

    @Test
    public void testPatch_matrixAnswer_singleSelect_onlyAllowOne() {
        PatchAnswerPayload payload = createMatrixPayload(matrixSingleSelectSid, null,
                new SelectedMatrixCell(matrixSingleRowStableId1, matrixSingleOptionStableId1, matrixDefaultGroup),
                new SelectedMatrixCell(matrixSingleRowStableId1, matrixSingleOptionStableId2, matrixSingleGroup));

        givenMatrixRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("does not allow more than one"));
    }

    @Test
    public void testPatch_matrixAnswer_multiSelect_andExclusive_allowMultiple() {
        PatchAnswerPayload payload = createMatrixPayload(matrixMultiSelectSid, null,
                new SelectedMatrixCell(matrixMultiRowStableId1, matrixMultiOptionStableId1, matrixDefaultGroup),
                new SelectedMatrixCell(matrixMultiRowStableId2, matrixMultiOptionStableId1, matrixDefaultGroup),
                new SelectedMatrixCell(matrixMultiRowStableId1, matrixMultiOptionStableId2, matrixMultiGroup),
                new SelectedMatrixCell(matrixMultiRowStableId2, matrixMultiOptionStableId2, matrixMultiGroup));

        String guid = givenMatrixRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(matrixMultiSelectSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.MATRIX).add(guid);
        MatrixAnswer answer = (MatrixAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(guid, answer.getAnswerGuid());
        assertEquals(matrixMultiSelectSid, answer.getQuestionStableId());
        assertEquals(4, answer.getValue().size());

        assertEquals(matrixMultiOptionStableId1, answer.getValue().get(0).getOptionStableId());
        assertEquals(matrixMultiRowStableId1, answer.getValue().get(0).getRowStableId());
        assertEquals(matrixDefaultGroup, answer.getValue().get(0).getGroupStableId());
    }

    @Test
    public void testPatch_matrixAnswer_emptyListAllowed_whenNotRequired() {
        PatchAnswerPayload payload = createMatrixPayload(matrixSingleSelectSid, null);

        String guid = givenMatrixRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(matrixSingleSelectSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.MATRIX).add(guid);
        MatrixAnswer answer = (MatrixAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(0, answer.getValue().size());
    }

    @Test
    public void testPatch_matrixAnswer_needOne_whenRequired() {
        PatchAnswerPayload payload = createMatrixPayload(matrixMultiSelectSid, null);

        givenMatrixRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(matrixMultiSelectSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.REQUIRED.name()));
    }

    @Test
    public void testPatch_matrixAnswer_exclusive_rejectedWhenMultipleSelected() {
        PatchAnswerPayload payload = createMatrixPayload(matrixMultiSelectSid, null,
                new SelectedMatrixCell(matrixMultiRowStableId1, matrixMultiOptionStableId1, matrixDefaultGroup),
                new SelectedMatrixCell(matrixMultiRowStableId1, matrixMultiOptionStableId3, matrixMultiGroup),
                new SelectedMatrixCell(matrixMultiRowStableId2, matrixMultiOptionStableId1, matrixDefaultGroup),
                new SelectedMatrixCell(matrixMultiRowStableId2, matrixMultiOptionStableId3, matrixMultiGroup),
                new SelectedMatrixCell(matrixMultiRowStableId2, matrixMultiOptionStableId4, matrixMultiGroup));

        givenMatrixRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("contains an exclusive option"));
    }

    @Test
    public void testPatch_matrixAnswer_unknownOption() {
        PatchAnswerPayload payload = createMatrixPayload(matrixSingleSelectSid, null,
                new SelectedMatrixCell("UNKNOWN", "UNKNOWN", "UNKNOWN"));

        givenMatrixRequest(instanceGuid, payload)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NO_SUCH_ELEMENT));
    }

    private PatchAnswerPayload createMatrixPayload(String questionStableId, String answerGuid, SelectedMatrixCell... selected) {
        PatchAnswerPayload payload = new PatchAnswerPayload();
        payload.addSubmission(new AnswerSubmission(questionStableId, answerGuid, gson.toJsonTree(selected)));
        return payload;
    }

    private Response givenMatrixRequest(String instanceGuid, PatchAnswerPayload data) {
        return given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceGuid)
                .body(data, ObjectMapperType.GSON)
                .when().patch(urlTemplate);
    }

    private Response givenAnswerPatchRequest(String instanceGuid, PatchAnswerPayload data) {
        return given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceGuid)
                .body(data, ObjectMapperType.GSON)
                .when().patch(urlTemplate);
    }

    @Test
    public void testPatch_dateAnswer_noAnswerValue() {
        AnswerSubmission submission = new AnswerSubmission(dateTextSid, null, null);
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void testPatch_dateAnswer_invalidValueFormat() {
        AnswerSubmission submission = new AnswerSubmission(dateTextSid,
                null, new JsonPrimitive("testing"));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void testPatch_dateAnswer_handleNumbersAsStrings() {
        JsonParser parser = new JsonParser();
        String valueWithStr = "{ \"year\": \"1988\", \"month\": 1 }";
        AnswerSubmission submission = new AnswerSubmission(dateTextSid, null, parser.parse(valueWithStr));
        PatchAnswerPayload data = new PatchAnswerPayload(Collections.singletonList(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(dateTextSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.DATE).add(guid);
        DateAnswer answer = (DateAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(guid, answer.getAnswerGuid());
        assertEquals(dateTextSid, answer.getQuestionStableId());
        assertEquals((Integer) 1988, answer.getValue().getYear());
        assertEquals((Integer) 1, answer.getValue().getMonth());
    }

    @Test
    public void testPatch_dateAnswer_invalidNumberString() {
        JsonParser parser = new JsonParser();
        String badValue = "{ \"year\": \"not a number\", \"month\": 1 }";
        AnswerSubmission submission = new AnswerSubmission(dateTextSid, null, parser.parse(badValue));
        PatchAnswerPayload data = new PatchAnswerPayload(Collections.singletonList(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void testPatch_dateAnswer_emptyNumberString() {
        JsonParser parser = new JsonParser();
        String badValue = "{ \"year\": \"\", \"month\": 1 }";
        AnswerSubmission submission = new AnswerSubmission(dateTextSid, null, parser.parse(badValue));
        PatchAnswerPayload data = new PatchAnswerPayload(Collections.singletonList(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void testPatch_dateAnswer_failAllowSaveTrueRule() {
        // date is ok with first range rule, but violates second rule with allowSave=true
        DateValue value = new DateValue(2015, 11, 15);
        AnswerSubmission submission = new AnswerSubmission(dateTextSid, null, gson.toJsonTree(value));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));
        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.DATE).add(guid);
    }

    @Test
    public void testPatch_dateAnswer_checksLeapYear() {
        DateValue value = new DateValue(2018, 2, 29);
        AnswerSubmission submission = new AnswerSubmission(dateTextSid, null, gson.toJsonTree(value));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", allOf(containsString("day 29"), containsString("invalid")));
    }

    @Test
    public void testPatch_dateAnswer_checksMonthDayLimit() {
        DateValue value = new DateValue(null, 11, 31);
        AnswerSubmission submission = new AnswerSubmission(dateTextSid, null, gson.toJsonTree(value));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", allOf(containsString("day 31"), containsString("invalid")));
    }

    @Test
    public void testPatch_dateAnswer_newAnswer() {
        DateValue expected = new DateValue(2018, 3, 15);
        AnswerSubmission submission = new AnswerSubmission(dateTextSid, null, gson.toJsonTree(expected));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(dateTextSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.DATE).add(guid);
        DateAnswer answer = (DateAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(guid, answer.getAnswerGuid());
        assertEquals(dateTextSid, answer.getQuestionStableId());
        assertEquals(expected, answer.getValue());
    }

    @Test
    public void testPatch_dateAnswer_updateAnswer() {
        DateValue old = new DateValue(2018, 3, 15);
        AnswerSubmission submission = new AnswerSubmission(dateTextSid, null, gson.toJsonTree(old));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().path("answers[0].answerGuid");
        answerGuidsToDelete.get(QuestionType.DATE).add(guid);

        DateValue updated = new DateValue(null, 4, 16);
        submission = new AnswerSubmission(dateTextSid, guid, gson.toJsonTree(updated));
        data = new PatchAnswerPayload(Arrays.asList(submission));
        String nextGuid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(dateTextSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        assertEquals(guid, nextGuid);
        DateAnswer answer = (DateAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(updated, answer.getValue());
    }

    @Test
    public void testPatch_dateAnswer_requiredFieldNotProvided() {
        DateValue value = new DateValue(null, 3, 15);
        AnswerSubmission submission = new AnswerSubmission(dateSingleTextSid, null, gson.toJsonTree(value));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(dateSingleTextSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.YEAR_REQUIRED.name()));
    }

    @Test
    public void testPatch_dateAnswer_dateFailsDateRange() {
        DateValue value = new DateValue(2012, 3, 15);
        AnswerSubmission submission = new AnswerSubmission(dateSingleTextSid, null, gson.toJsonTree(value));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(dateSingleTextSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.DATE_RANGE.name()));
    }

    @Test
    public void testPatch_dateAnswer_datePassesDateRange() {
        DateValue value = new DateValue(2018, 3, 14);
        AnswerSubmission submission = new AnswerSubmission(datePicklistSid, null, gson.toJsonTree(value));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(datePicklistSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.DATE).add(guid);
        DateAnswer answer = (DateAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(guid, answer.getAnswerGuid());
        assertEquals(datePicklistSid, answer.getQuestionStableId());
        assertEquals(value, answer.getValue());
    }

    @Test
    public void testPatch_dateAnswer_allowSaveTrueRuleFails() {
        DateValue value = new DateValue(2018, 3, 14);
        AnswerSubmission submission = new AnswerSubmission(datePicklistSid, null, gson.toJsonTree(value));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(datePicklistSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.DATE).add(guid);
        DateAnswer answer = (DateAnswer) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid).get());

        assertNotNull(answer);
        assertEquals(guid, answer.getAnswerGuid());
        assertEquals(datePicklistSid, answer.getQuestionStableId());
        assertEquals(value, answer.getValue());
    }

    @Test
    public void testPatch_essayTextAnswer() {
        AnswerSubmission submission = new AnswerSubmission(essayTextStableId, null, gson.toJsonTree("Hi there"));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));

        String answerGuid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(essayTextStableId))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.TEXT).add(answerGuid);
    }

    @Test
    public void testPatch_agreementAnswer() {
        AnswerSubmission submission = new AnswerSubmission(agreementSid, null, gson.toJsonTree(true));
        PatchAnswerPayload data = new PatchAnswerPayload(Arrays.asList(submission));

        String answerGuid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(agreementSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.AGREEMENT).add(answerGuid);
    }

    @Test
    public void testPatch_fileAnswer_createAndUpdate() {
        var stableId = fileQuestion.getStableId();
        var submission = new AnswerSubmission(stableId, null, gson.toJsonTree(List.of(upload1.getGuid())));
        var data = new PatchAnswerPayload(List.of(submission));
        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(stableId))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");
        answerGuidsToDelete.get(QuestionType.FILE).add(guid);
        var answer = (Optional<Answer>) TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));

        assertTrue(answer.isPresent());
        assertNotNull(answer.get());
        assertEquals(guid, answer.get().getAnswerGuid());
        assertEquals(stableId, answer.get().getQuestionStableId());
        assertEquals(QuestionType.FILE, answer.get().getQuestionType());
        assertEquals(upload1.getFileName(), ((FileAnswer)answer.get()).getInfo(0).getFileName());
        assertEquals(upload1.getFileSize(), ((FileAnswer)answer.get()).getInfo(0).getFileSize());

        submission = new AnswerSubmission(stableId, guid, gson.toJsonTree(List.of(upload2.getGuid())));
        data = new PatchAnswerPayload(List.of(submission));
        String nextGuid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().path("answers[0].answerGuid");

        assertEquals(guid, nextGuid);
        answer =  TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));
        assertTrue(answer.isPresent());
        assertEquals(upload2.getFileName(), ((FileAnswer) answer.get()).getInfo(0).getFileName());
    }

    @Test
    public void testPatch_fileAnswer_canCreateNullValue_andClearValueBySendingNull() {
        var stableId = fileQuestion.getStableId();
        var data = new PatchAnswerPayload(List.of(new AnswerSubmission(stableId, null, null)));
        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");
        answerGuidsToDelete.get(QuestionType.FILE).add(guid);
        var answer = TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));
        assertTrue(answer.isPresent());
        assertTrue("created answer should have empty list for value", ((List<?>) answer.get().getValue()).isEmpty());

        // Set a value then clear it.
        data = new PatchAnswerPayload(List.of(new AnswerSubmission(stableId, guid, gson.toJsonTree(List.of(upload1.getGuid())))));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat().statusCode(200);
        answer = TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));
        assertTrue(answer.isPresent());
        assertNotNull(answer.get().getValue());
        assertEquals(upload1.getFileName(), ((FileAnswer) answer.get()).getInfo(0).getFileName());

        data = new PatchAnswerPayload(List.of(new AnswerSubmission(stableId, guid, null)));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat().statusCode(200);
        answer = TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));
        assertTrue(answer.isPresent());
        assertTrue("cleared answer should have empty list for value", ((List<?>) answer.get().getValue()).isEmpty());
    }

    @Test
    public void testPatch_fileAnswer_fileNotFound() {
        var stableId = fileQuestion.getStableId();
        var data = new PatchAnswerPayload(List.of(new AnswerSubmission(stableId, null, gson.toJsonTree(List.of("foobar")))));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.FILE_ERROR))
                .body("message", containsString("Could not find file"));
    }

    @Test
    public void testPatch_numericAnswer_newAnswer() {
        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(25));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .log().all()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(numericIntegerSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.NUMERIC).add(guid);
        var answer = TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));

        assertNotNull(answer);
        assertTrue(answer.isPresent());
        assertEquals(guid, answer.get().getAnswerGuid());
        assertEquals(numericIntegerSid, answer.get().getQuestionStableId());
        assertEquals(25L, (long) answer.get().getValue());
    }

    @Test
    public void testPatch_numericAnswer_updateAnswer() {
        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(25));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .log().all()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().path("answers[0].answerGuid");
        answerGuidsToDelete.get(QuestionType.NUMERIC).add(guid);

        submission = new AnswerSubmission(numericIntegerSid, guid, gson.toJsonTree(75));
        data = new PatchAnswerPayload(List.of(submission));
        String nextGuid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(numericIntegerSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        assertEquals(guid, nextGuid);
        var answer = TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));

        assertNotNull(answer);
        assertTrue(answer.isPresent());
        assertEquals(75L, (long) answer.get().getValue());
    }

    @Test
    public void testPatch_numericAnswer_integerType_acceptsNull() {
        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, null);
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .log().all()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(numericIntegerSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.NUMERIC).add(guid);
        var answer = TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));

        assertNotNull(answer);
        assertTrue(answer.isPresent());
        assertEquals(guid, answer.get().getAnswerGuid());
        assertEquals(numericIntegerSid, answer.get().getQuestionStableId());
        assertNull(answer.get().getValue());
    }

    @Test
    public void testPatch_numericAnswer_integerType_rangeRule_lessThanMin() {
        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(1));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(numericIntegerSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.INT_RANGE.name()));
    }

    @Test
    public void testPatch_numericAnswer_integerType_rangeRule_greaterThanMax() {
        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(1024));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(numericIntegerSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.INT_RANGE.name()));
    }

    @Test
    public void testPatch_numericAnswer_integerType_requiredRule_failsValidation() {
        AnswerSubmission submission = new AnswerSubmission(numericIntegerReqSid, null, null);
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(numericIntegerReqSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.REQUIRED.name()));
    }

    @Test
    public void testPatch_decimalAnswer_newAnswer() {
        AnswerSubmission submission = new AnswerSubmission(decimalIntegerSid, null,
                gson.toJsonTree(new DecimalDef(25)));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .log().all()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(decimalIntegerSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .body("equations[0].stableId", equalTo(equationSid))
                .body("equations[0].values[0].value", equalTo(5000000000000000L))
                .body("equations[0].values[0].scale", equalTo(14))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.DECIMAL).add(guid);
        var answer = TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));

        assertNotNull(answer);
        assertTrue(answer.isPresent());
        assertEquals(guid, answer.get().getAnswerGuid());
        assertEquals(decimalIntegerSid, answer.get().getQuestionStableId());
        assertEquals(0, new DecimalDef(25).compareTo(((DecimalAnswer) answer.get()).getValue()));
    }

    @Test
    public void testPatch_decimalAnswer_updateAnswer() {
        AnswerSubmission submission = new AnswerSubmission(decimalIntegerSid, null,
                gson.toJsonTree(new DecimalDef(25)));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .log().all()
                .statusCode(200).contentType(ContentType.JSON)
                .and().extract().path("answers[0].answerGuid");
        answerGuidsToDelete.get(QuestionType.DECIMAL).add(guid);

        submission = new AnswerSubmission(decimalIntegerSid, guid,
                gson.toJsonTree(new DecimalDef(75)));
        data = new PatchAnswerPayload(List.of(submission));
        String nextGuid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(decimalIntegerSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        assertEquals(guid, nextGuid);
        var answer = TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));

        assertNotNull(answer);
        assertTrue(answer.isPresent());
        assertEquals(0, new DecimalDef(75).compareTo(((DecimalAnswer) answer.get()).getValue()));
    }

    @Test
    public void testPatch_decimalAnswer_acceptsNull() {
        AnswerSubmission submission = new AnswerSubmission(decimalIntegerSid, null, null);
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        String guid = givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .log().all()
                .statusCode(200).contentType(ContentType.JSON)
                .body("answers.size()", equalTo(1))
                .body("answers[0].stableId", equalTo(decimalIntegerSid))
                .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                .and().extract().path("answers[0].answerGuid");

        answerGuidsToDelete.get(QuestionType.DECIMAL).add(guid);
        var answer = TransactionWrapper.withTxn(handle ->
                new AnswerCachedDao(handle).findAnswerByGuid(guid));

        assertNotNull(answer);
        assertTrue(answer.isPresent());
        assertEquals(guid, answer.get().getAnswerGuid());
        assertEquals(decimalIntegerSid, answer.get().getQuestionStableId());
        assertNull(answer.get().getValue());
    }

    @Test
    public void testPatch_decimalAnswer_rangeRule_lessThanMin() {
        AnswerSubmission submission = new AnswerSubmission(decimalIntegerSid, null,
                gson.toJsonTree(new DecimalDef(1)));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(decimalIntegerSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.DECIMAL_RANGE.name()));
    }

    @Test
    public void testPatch_decimalAnswer_rangeRule_greaterThanMax() {
        AnswerSubmission submission = new AnswerSubmission(decimalIntegerSid, null,
                gson.toJsonTree(new DecimalDef(1024)));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(decimalIntegerSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.DECIMAL_RANGE.name()));
    }

    @Test
    public void testPatch_decimalAnswer_requiredRule_failsValidation() {
        AnswerSubmission submission = new AnswerSubmission(decimalIntegerReqSid, null, null);
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(decimalIntegerReqSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.REQUIRED.name()));
    }

    @Test
    public void testPath_whenMultipleRulesFail_allOfThemAreCommunicated() {
        AnswerSubmission submission = new AnswerSubmission(numericIntegerWithMultipleRulesSid, null, gson.toJsonTree(1024));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_VALIDATION))
                .body("violations[0].stableId", equalTo(numericIntegerWithMultipleRulesSid))
                .body("violations[0].rules[0].ruleType", equalTo(RuleType.INT_RANGE.name()))
                .body("violations[0].rules[0].message", notNullValue())
                .body("violations[0].rules[1].ruleType", equalTo(RuleType.INT_RANGE.name()));
    }

    /**
     * Given a form, query its status from the db.
     */
    private ActivityInstanceStatusDto queryStatus(String instanceGuid) {
        return TransactionWrapper.withTxn(handle -> {
            ActivityInstanceStatusDao statusDao = handle.attach(ActivityInstanceStatusDao.class);
            return statusDao.getCurrentStatus(instanceGuid).get();
        });
    }

    @Test
    public void testStatusStillInProgressAfterPatch() throws Exception {
        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, null, new JsonPrimitive(false)));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        String answerGuid = extractAnswerGuid(response);
        answerGuidsToDelete.get(QuestionType.BOOLEAN).add(answerGuid);

        ActivityInstanceStatusDto prevStatus = queryStatus(instanceGuid);
        assertEquals((InstanceStatusType.IN_PROGRESS), prevStatus.getType());

        data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, answerGuid, new JsonPrimitive(true)));

        request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());

        assertEquals(prevStatus.getType(), queryStatus(instanceGuid).getType());
    }

    @Test
    public void testSuccessfulStatusUpdateAfterPatch() throws Exception {
        ActivityInstanceStatusDto prevStatus = queryStatus(instanceGuid);
        assertEquals(prevStatus.getType(), InstanceStatusType.CREATED);

        PatchAnswerPayload data = new PatchAnswerPayload();
        data.addSubmission(new AnswerSubmission(boolStableId, null, new JsonPrimitive(true)));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
        answerGuidsToDelete.get(QuestionType.BOOLEAN).add(extractAnswerGuid(response));

        ActivityInstanceStatusDto newStatus = queryStatus(instanceGuid);

        assertNotEquals(prevStatus, newStatus);
        assertEquals(InstanceStatusType.IN_PROGRESS, newStatus.getType());
    }

    @Test
    public void test4xxStatusReturnedForReadonlyActivityInstance() throws Exception {
        PatchAnswerPayload data = createPicklistPayload(plistMultiSelectSid, null,
                new SelectedPicklistOption(plistMulti_option1_sid));

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(token, url, gson.toJson(data));
        HttpResponse response = request.execute().returnResponse();
        assertEquals(200, response.getStatusLine().getStatusCode());
        answerGuidsToDelete.get(QuestionType.PICKLIST).add(extractAnswerGuid(response));

        TransactionWrapper.useTxn(handle -> handle.attach(JdbiActivityInstance.class)
                .updateIsReadonlyByGuid(true, instanceGuid));

        response = request.execute().returnResponse();
        assertEquals(422, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        ApiError resp = gson.fromJson(json, ApiError.class);
        assertEquals(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, resp.getCode());
    }

    @Test
    public void given_oneTrueExpr_whenRouteIsCalled_thenItReturnsOk() {
        try {
            String answerGuid = TransactionWrapper.withTxn(handle -> {
                handle.attach(JdbiActivity.class).insertValidation(
                        RouteTestUtil.createActivityValidationDto(
                                activity, "false", "Should never fail", List.of(textStableId)
                        ),
                        testData.getUserId(),
                        testData.getStudyId(),
                        activityVersionDto.getRevId()
                );
                TextAnswer answer = new TextAnswer(null, textStableId, null, "old value");
                return createAnswerAndDeferCleanup(handle, answer);
            });
            PatchAnswerPayload data = new PatchAnswerPayload();
            data.addSubmission(new AnswerSubmission(textStableId, answerGuid, new JsonPrimitive("hi there")));
            givenAnswerPatchRequest(instanceGuid, data)
                    .then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON);
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiActivity.class)
                    .deleteValidationsByCode(activity.getActivityId()));
        }
    }

    @Test
    public void given_oneTrueExpr_whenRouteIsCalled_thenItReturnsOkAndRespContainsOneErrorPlusOneAnswer() {
        try {
            String answerGuid = TransactionWrapper.withTxn(handle -> {
                handle.attach(JdbiActivity.class).insertValidation(
                        RouteTestUtil.createActivityValidationDto(
                                activity, "true", "Should always fail", List.of(textStableId)
                        ),
                        testData.getUserId(),
                        testData.getStudyId(),
                        activityVersionDto.getRevId()
                );

                ActivityDefStore.getInstance().clearCachedActivityValidationDtos(activity.getActivityId());

                TextAnswer answer = new TextAnswer(null, textStableId, null, "old value");
                return createAnswerAndDeferCleanup(handle, answer);
            });

            Runnable executePatch = () -> {
                PatchAnswerPayload data = new PatchAnswerPayload();

                data.addSubmission(new AnswerSubmission(textStableId, answerGuid, new JsonPrimitive("hi there")));
                givenAnswerPatchRequest(instanceGuid, data)
                        .then().assertThat()
                        .statusCode(200).contentType(ContentType.JSON)
                        .body("validationFailures", is(notNullValue()))
                        .body("validationFailures.size()", equalTo(1))
                        .body("answers.size()", equalTo(1));
            };
            executePatch.run();
            //second time to pick up cached version
            executePatch.run();
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(JdbiActivity.class).deleteValidationsByCode(activity.getActivityId());
                ActivityDefStore.getInstance().clearCachedActivityValidationDtos(activity.getActivityId());
            });
        }
    }

    private String extractAnswerGuid(HttpResponse response) throws IOException {
        String json = EntityUtils.toString(response.getEntity());
        PatchAnswerResponse resp = gson.fromJson(json, PatchAnswerResponse.class);
        assertEquals(1, resp.getAnswers().size());

        AnswerResponse ans = resp.getAnswers().get(0);
        assertNotNull(ans.getAnswerGuid());

        return ans.getAnswerGuid();
    }

    @Test
    public void testStudyAdmin_patchHiddenInstance() {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.attach(ActivityInstanceDao.class)
                    .bulkUpdateIsHiddenByActivityIds(testData.getUserId(), true, Set.of(activity.getActivityId())));
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
        });

        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(25));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        try {
            String guid = givenAnswerPatchRequest(instanceGuid, data)
                    .then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("answers.size()", equalTo(1))
                    .body("answers[0].stableId", equalTo(numericIntegerSid))
                    .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                    .and().extract().path("answers[0].answerGuid");
            answerGuidsToDelete.get(QuestionType.NUMERIC).add(guid);
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
            });
        }
    }

    @Test
    public void testStudyAdmin_patchReadOnlyInstance() {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.attach(ActivityInstanceDao.class)
                    .bulkUpdateReadOnlyByActivityIds(testData.getUserId(), true, Set.of(activity.getActivityId())));
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
        });

        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(25));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        try {
            String guid = givenAnswerPatchRequest(instanceGuid, data)
                    .then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("answers.size()", equalTo(1))
                    .body("answers[0].stableId", equalTo(numericIntegerSid))
                    .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                    .and().extract().path("answers[0].answerGuid");
            answerGuidsToDelete.get(QuestionType.NUMERIC).add(guid);
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
            });
        }
    }

    @Test
    public void testStudyAdmin_patchReadOnlyQuestion() {
        AnswerSubmission submission = new AnswerSubmission(numericIntegerSid, null, gson.toJsonTree(25));
        PatchAnswerPayload data = new PatchAnswerPayload(List.of(submission));

        String updateSql = "update question set is_write_once = ? where question_id = ?";
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.execute(updateSql, true, numericQuestionDef.getQuestionId()));
            handle.attach(ActivityInstanceStatusDao.class).insertStatus(
                    instanceGuid, InstanceStatusType.COMPLETE, Instant.now().toEpochMilli(), testData.getUserGuid());
        });

        // Need to define question as write-once and instance need to be completed for the question to be read-only.
        // Let's make sure that's the case by doing a PATCH request.
        givenAnswerPatchRequest(instanceGuid, data)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.QUESTION_IS_READONLY))
                .body("message", containsString(numericIntegerSid));

        // Now mark user as study admin and try again.
        TransactionWrapper.useTxn(handle -> {
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
        });
        try {
            String guid = givenAnswerPatchRequest(instanceGuid, data)
                    .then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("answers.size()", equalTo(1))
                    .body("answers[0].stableId", equalTo(numericIntegerSid))
                    .body("answers[0].answerGuid", not(isEmptyOrNullString()))
                    .and().extract().path("answers[0].answerGuid");
            answerGuidsToDelete.get(QuestionType.NUMERIC).add(guid);
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
                assertEquals(1, handle.execute(updateSql, false, numericQuestionDef.getQuestionId()));
            });
        }
    }

}
