package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.DaoBuilder;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.exception.OperationNotAllowedException;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.instance.answer.ActivityInstanceSelectAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedMatrixCell;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AnswerDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private final DaoBuilder<AnswerDao> daoBuilder;
    private boolean isCachedDao;

    public AnswerDaoTest(DaoBuilder daoBuilder, boolean isCachedDao) {
        this.daoBuilder = daoBuilder;
        this.isCachedDao = isCachedDao;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        Object[] uncached = {(DaoBuilder<AnswerDao>) (handle) -> handle.attach(AnswerDao.class), false};
        Object[] cached = {(DaoBuilder<AnswerDao>) (handle) -> new AnswerCachedDao(handle), true};
        return List.of(uncached, cached);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCreateUpdateDelete_agreement() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withAgreementQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answerDao = daoBuilder.buildDao(handle);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId,
                    new AgreementAnswer(null, act.getAgreementQuestion().getStableId(), null, true));
            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.AGREEMENT, created.getQuestionType());
            assertTrue(((AgreementAnswer) created).getValue());
            AgreementAnswer agreementAnswer = new AgreementAnswer(null, act.getAgreementQuestion().getStableId(), null, false);
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), agreementAnswer);

            assertEquals(created.getAnswerId(), agreementAnswer.getAnswerId());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(agreementAnswer.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            Answer updated = updatedOpt.get();

            assertEquals(created.getAnswerId(), updated.getAnswerId());
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            assertFalse(((AgreementAnswer) updated).getValue());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_bool() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answerDao = daoBuilder.buildDao(handle);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId,
                    new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true));

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.BOOLEAN, created.getQuestionType());
            assertTrue(((BoolAnswer) created).getValue());
            BoolAnswer boolAnswer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, false);
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), boolAnswer);

            assertEquals(created.getAnswerId(), boolAnswer.getAnswerId());
            Optional<Answer> updatedOpt = answerDao.findAnswerById(created.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerId(), updated.getAnswerId());
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            assertFalse(((BoolAnswer) updated).getValue());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_text() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            TextAnswer textAnswer1 = new TextAnswer(null, act.getTextQuestion().getStableId(), null, "old");
            answerDao.createAnswer(testData.getUserId(), instanceId, textAnswer1);

            assertTrue(textAnswer1.getAnswerId() > 0);
            assertEquals(QuestionType.TEXT, textAnswer1.getQuestionType());
            assertEquals("old", textAnswer1.getValue());
            TextAnswer textAnswer2 = new TextAnswer(null, act.getTextQuestion().getStableId(), null, "new");
            answerDao.updateAnswer(testData.getUserId(), textAnswer1.getAnswerId(), textAnswer2);

            assertEquals(textAnswer1.getAnswerId(), textAnswer2.getAnswerId());
            Optional<Answer> updatedOpt = answerDao.findAnswerById(textAnswer1.getAnswerId());
            assertTrue(updatedOpt.isPresent());

            Answer updated = updatedOpt.get();

            assertEquals(textAnswer1.getAnswerId(), updated.getAnswerId());
            assertEquals(textAnswer1.getAnswerGuid(), updated.getAnswerGuid());
            assertEquals("new", updated.getValue());

            answerDao.deleteAnswer(textAnswer1.getAnswerId());
            assertFalse(answerDao.findAnswerById(textAnswer1.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_ActivityInstanceSelect() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity activity = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .withActivityInstanceSelectQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            ActivityInstanceDto instanceDto = createInstance(handle, activity.getDef().getActivityId());
            long instanceId = instanceDto.getId();

            TestFormActivity subActivity1 = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            ActivityInstanceDto instanceDto1 = createInstance(handle, subActivity1.getDef().getActivityId());

            TestFormActivity subActivity2 = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            ActivityInstanceDto instanceDto2 = createInstance(handle, subActivity2.getDef().getActivityId());


            AnswerDao answerDao = daoBuilder.buildDao(handle);

            ActivityInstanceSelectAnswer activityInstanceSelectAnswer1 =
                    new ActivityInstanceSelectAnswer(null, activity.getActivityInstanceSelectQuestion().getStableId(),
                            null, instanceDto1.getGuid());
            answerDao.createAnswer(testData.getUserId(), instanceId, activityInstanceSelectAnswer1);

            assertTrue(activityInstanceSelectAnswer1.getAnswerId() > 0);
            assertEquals(QuestionType.ACTIVITY_INSTANCE_SELECT, activityInstanceSelectAnswer1.getQuestionType());
            assertEquals(instanceDto1.getGuid(), activityInstanceSelectAnswer1.getValue());

            ActivityInstanceSelectAnswer activityInstanceSelectAnswer2 =
                    new ActivityInstanceSelectAnswer(null, activity.getActivityInstanceSelectQuestion().getStableId(),
                            null, instanceDto2.getGuid());
            answerDao.updateAnswer(testData.getUserId(), activityInstanceSelectAnswer1.getAnswerId(), activityInstanceSelectAnswer2);

            assertEquals(activityInstanceSelectAnswer1.getAnswerId(), activityInstanceSelectAnswer2.getAnswerId());
            assertEquals(instanceDto2.getGuid(), activityInstanceSelectAnswer2.getValue());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(activityInstanceSelectAnswer1.getAnswerId());

            assertTrue(updatedOpt.isPresent());

            Answer updatedAnswer = updatedOpt.get();

            assertEquals(activityInstanceSelectAnswer1.getAnswerId(), updatedAnswer.getAnswerId());
            assertEquals(activityInstanceSelectAnswer1.getAnswerGuid(), updatedAnswer.getAnswerGuid());

            assertEquals(instanceDto2.getGuid(), updatedAnswer.getValue());

            answerDao.deleteAnswer(activityInstanceSelectAnswer1.getAnswerId());
            assertFalse(answerDao.findAnswerById(activityInstanceSelectAnswer1.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_date() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withDateFullQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            DateAnswer createdAnswer = new DateAnswer(null, act.getDateFullQuestion().getStableId(), null, 2018, 10, 24);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId, createdAnswer);

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.DATE, created.getQuestionType());
            assertEquals(new DateValue(2018, 10, 24), created.getValue());

            DateAnswer updatedAnswer = new DateAnswer(null, act.getDateFullQuestion().getStableId(), null, 2020, 4, null);
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), updatedAnswer);

            assertEquals(created.getAnswerId(), updatedAnswer.getAnswerId());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(created.getAnswerId());

            assertTrue(updatedOpt.isPresent());
            Answer updated = updatedOpt.get();

            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            assertEquals(new DateValue(2020, 4, null), updated.getValue());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_file() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withFileQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid",
                    testData.getStudyId(), testData.getUserId(), testData.getUserId(),
                    "blob", "mime", "file", 123L);
            fileDao.markVerified(upload.getId());
            var info = fileDao.findFileInfoByGuid(upload.getGuid()).get();

            var answerDao = daoBuilder.buildDao(handle);
            var created = new FileAnswer(null, act.getFileQuestion().getStableId(), null, Collections.singletonList(info));
            answerDao.createAnswer(testData.getUserId(), instanceId, created);

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.FILE, created.getQuestionType());
            assertNotNull(created.getValue());

            var updated = new FileAnswer(null, act.getFileQuestion().getStableId(), null, Collections.singletonList(info));
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), updated);

            assertEquals(created.getAnswerId(), updated.getAnswerId());
            var queried = answerDao.findAnswerById(updated.getAnswerId()).orElse(null);
            assertNotNull(queried);
            assertEquals(created.getAnswerGuid(), queried.getAnswerGuid());
            assertEquals(QuestionType.FILE, queried.getQuestionType());
            assertNotNull(queried.getValue());

            var queriedInfo = ((FileAnswer) queried).getValue().get(0);
            assertEquals(info.getUploadId(), queriedInfo.getUploadId());
            assertEquals(info.getFileName(), queriedInfo.getFileName());
            assertEquals(info.getFileSize(), queriedInfo.getFileSize());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_numeric() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withNumericIntQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);

            NumericAnswer created = new NumericAnswer(null, act.getNumericIntQuestion().getStableId(), null, 25L);
            answerDao.createAnswer(testData.getUserId(), instanceId, created);

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.NUMERIC, created.getQuestionType());
            assertEquals(25L, (long) created.getValue());

            NumericAnswer updatedNumber = new NumericAnswer(null, act.getNumericIntQuestion().getStableId(), null, 100L);
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), updatedNumber);

            assertEquals(created.getAnswerId(), updatedNumber.getAnswerId());
            Optional<Answer> updatedOpt = answerDao.findAnswerById(updatedNumber.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            assertEquals(100L, updated.getValue());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_decimal() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withDecimalQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);

            DecimalAnswer created = new DecimalAnswer(null, act.getDecimalQuestion().getStableId(), null, new DecimalDef(25));
            answerDao.createAnswer(testData.getUserId(), instanceId, created);

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.DECIMAL, created.getQuestionType());
            assertEquals(0, new DecimalDef(25).compareTo(created.getValue()));

            DecimalAnswer updatedNumber = new DecimalAnswer(null, act.getDecimalQuestion().getStableId(),
                    null, new DecimalDef(100));
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), updatedNumber);

            assertEquals(created.getAnswerId(), updatedNumber.getAnswerId());
            Optional<Answer> updatedOpt = answerDao.findAnswerById(updatedNumber.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            assertEquals(0, new DecimalDef(100).compareTo((DecimalDef) updated.getValue()));

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_picklist() {
        TransactionWrapper.useTxn(handle -> {
            PicklistOptionDef nestedOptionDef1 = new PicklistOptionDef("NESTED_OPT1", Template.text("nested option 1"));
            PicklistOptionDef nestedOptionDef2 = new PicklistOptionDef("NESTED_OPT2", Template.text("nested option 2"));
            List<PicklistOptionDef> nestedOpts = List.of(nestedOptionDef1, nestedOptionDef2);
            PicklistOptionDef optionDef = new PicklistOptionDef("PARENT_OPT", Template.text("parent option1"),
                    Template.text("nested options Label"), nestedOpts);

            TestFormActivity act = TestFormActivity.builder()
                    .withPicklistMultiList(true,
                            new PicklistOptionDef("PO1", Template.text("po1")),
                            new PicklistOptionDef("PO2", Template.text("po2"), Template.text("details")), optionDef)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId,
                    new PicklistAnswer(null, act.getPicklistMultiListQuestion().getStableId(), null, List.of(
                            new SelectedPicklistOption("PO1"))));

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.PICKLIST, created.getQuestionType());

            var selected = ((PicklistAnswer) created).getValue();
            assertEquals(1, selected.size());
            assertEquals("PO1", selected.get(0).getStableId());

            PicklistAnswer picklistAnswer = new PicklistAnswer(null, act.getPicklistMultiListQuestion().getStableId(), null, List.of(
                    new SelectedPicklistOption("PO2", "details2")));
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), picklistAnswer);

            assertEquals(created.getAnswerId(), picklistAnswer.getAnswerId());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(created.getAnswerId());

            assertTrue(updatedOpt.isPresent());

            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());

            selected = ((PicklistAnswer) updated).getValue();
            assertEquals(1, selected.size());
            assertEquals("PO2", selected.get(0).getStableId());
            assertEquals("details2", selected.get(0).getDetailText());

            picklistAnswer = new PicklistAnswer(null, act.getPicklistMultiListQuestion().getStableId(), null, List.of(
                    new SelectedPicklistOption("PARENT_OPT"),
                    new SelectedPicklistOption("NESTED_OPT2")));
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), picklistAnswer);
            assertEquals(created.getAnswerId(), picklistAnswer.getAnswerId());
            updatedOpt = answerDao.findAnswerById(created.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            selected = ((PicklistAnswer) updated).getValue();
            assertEquals(2, selected.size());
            assertEquals("PARENT_OPT", selected.get(0).getStableId());
            assertEquals("NESTED_OPT2", selected.get(1).getStableId());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_matrix() {
        TransactionWrapper.useTxn(handle -> {
            PicklistOptionDef nestedOptionDef1 = new PicklistOptionDef("NESTED_OPT1", Template.text("nested option 1"));
            PicklistOptionDef nestedOptionDef2 = new PicklistOptionDef("NESTED_OPT2", Template.text("nested option 2"));
            List<PicklistOptionDef> nestedOpts = List.of(nestedOptionDef1, nestedOptionDef2);
            PicklistOptionDef optionDef = new PicklistOptionDef("PARENT_OPT", Template.text("parent option1"),
                    Template.text("nested options Label"), nestedOpts);

            List<MatrixOptionDef> options = List.of(
                    new MatrixOptionDef("OPT_1", Template.text(""), "DEFAULT"),
                    new MatrixOptionDef("OPT_2", Template.text(""), "GROUP"),
                    new MatrixOptionDef("OPT_3", Template.text(""), "GROUP"));
            List<MatrixRowDef> rows = List.of(
                    new MatrixRowDef("ROW_1", Template.text("")),
                    new MatrixRowDef("ROW_2", Template.text("")));
            List<MatrixGroupDef> groups = List.of(new MatrixGroupDef("GROUP", Template.text("")),
                    new MatrixGroupDef("DEFAULT", null));

            TestFormActivity act = TestFormActivity.builder()
                    .withMatrixOptionsRowsGroupsList(true, MatrixSelectMode.SINGLE, options, rows, groups)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId,
                    new MatrixAnswer(null, act.getMatrixListQuestion().getStableId(), null, List.of(
                            new SelectedMatrixCell("ROW_1", "OPT_1", "DEFAULT"))));

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.MATRIX, created.getQuestionType());

            var selected = ((MatrixAnswer) created).getValue();
            assertEquals(1, selected.size());
            assertEquals("OPT_1", selected.get(0).getOptionStableId());
            assertEquals("ROW_1", selected.get(0).getRowStableId());
            assertEquals("DEFAULT", selected.get(0).getGroupStableId());

            MatrixAnswer matrixAnswer = new MatrixAnswer(null, act.getMatrixListQuestion().getStableId(), null, List.of(
                    new SelectedMatrixCell("ROW_2", "OPT_2", "GROUP")));
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), matrixAnswer);

            assertEquals(created.getAnswerId(), matrixAnswer.getAnswerId());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(created.getAnswerId());

            assertTrue(updatedOpt.isPresent());

            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());

            selected = ((MatrixAnswer) updated).getValue();
            assertEquals(1, selected.size());
            assertEquals("OPT_2", selected.get(0).getOptionStableId());
            assertEquals("ROW_2", selected.get(0).getRowStableId());
            assertEquals("GROUP", selected.get(0).getGroupStableId());

            matrixAnswer = new MatrixAnswer(null, act.getMatrixListQuestion().getStableId(), null, List.of(
                    new SelectedMatrixCell("ROW_1", "OPT_1", "DEFAULT"),
                    new SelectedMatrixCell("ROW_2", "OPT_2", "GROUP")));
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), matrixAnswer);
            assertEquals(created.getAnswerId(), matrixAnswer.getAnswerId());
            updatedOpt = answerDao.findAnswerById(created.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            selected = ((MatrixAnswer) updated).getValue();
            assertEquals(2, selected.size());
            assertEquals("OPT_1", selected.get(0).getOptionStableId());
            assertEquals("ROW_1", selected.get(0).getRowStableId());
            assertEquals("DEFAULT", selected.get(0).getGroupStableId());
            assertEquals("OPT_2", selected.get(1).getOptionStableId());
            assertEquals("ROW_2", selected.get(1).getRowStableId());
            assertEquals("GROUP", selected.get(1).getGroupStableId());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_composite() {
        TransactionWrapper.useTxn(handle -> {
            DateQuestionDef childDate = DateQuestionDef
                    .builder(DateRenderMode.TEXT, "CDATE" + Instant.now().toEpochMilli(), Template.text("child date"))
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .build();
            TextQuestionDef childText = TextQuestionDef
                    .builder(TextInputType.TEXT, "CTEXT" + Instant.now().toEpochMilli(), Template.text("child text"))
                    .build();

            TestFormActivity act = TestFormActivity.builder()
                    .withCompositeQuestion(true, childDate, childText)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            // create

            CompositeAnswer answer = new CompositeAnswer(null, act.getCompositeQuestion().getStableId(), null);
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2018, 10, 24)));
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2020, 3, 14)),
                    new TextAnswer(null, childText.getStableId(), null, "row 2 col 2"));

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId, answer);

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.COMPOSITE, created.getQuestionType());

            var rows = ((CompositeAnswer) created).getValue();
            assertEquals(2, rows.size());
            assertEquals(1, rows.get(0).getValues().size());
            assertEquals(2, rows.get(1).getValues().size());

            var row1 = rows.get(0).getValues();
            assertEquals(QuestionType.DATE, row1.get(0).getQuestionType());
            assertEquals(new DateValue(2018, 10, 24), row1.get(0).getValue());

            var row2 = rows.get(1).getValues();
            assertEquals(QuestionType.DATE, row2.get(0).getQuestionType());
            assertEquals(new DateValue(2020, 3, 14), row2.get(0).getValue());
            assertEquals(QuestionType.TEXT, row2.get(1).getQuestionType());
            assertEquals("row 2 col 2", row2.get(1).getValue());

            long row1Child1Id = row1.get(0).getAnswerId();
            long row2Child1Id = row2.get(0).getAnswerId();
            long row2Child2Id = row2.get(1).getAnswerId();

            // update

            answer = new CompositeAnswer(null, act.getCompositeQuestion().getStableId(), null);
            answer.addRowOfChildAnswers(
                    new TextAnswer(null, childText.getStableId(), null, "row 1 col 2"));
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2020, 9, null)),
                    new TextAnswer(null, childText.getStableId(), null, "row 2 col 2"));

            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), answer);

            assertEquals(created.getAnswerId(), answer.getAnswerId());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(answer.getAnswerId());
            assert (updatedOpt.isPresent());

            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());

            rows = ((CompositeAnswer) updated).getValue();
            assertEquals("updated composite should still have same rows", 2, rows.size());
            assertEquals(2, rows.get(0).getValues().size());
            assertEquals(2, rows.get(1).getValues().size());

            row1 = rows.get(0).getValues();
            assertNull("updated composite row 1 col 1 should not have a child answer", row1.get(0));
            assertNotEquals("updated composite row 1 child should be a different answer",
                    (Long) row1Child1Id, row1.get(1).getAnswerId());
            assertEquals(QuestionType.TEXT, row1.get(1).getQuestionType());
            assertEquals("row 1 col 2", row1.get(1).getValue());
            assertFalse("old row 1 child should be deleted", answerDao.findAnswerById(row1Child1Id).isPresent());
            row1Child1Id = row1.get(1).getAnswerId();

            row2 = rows.get(1).getValues();
            assertEquals((Long) row2Child1Id, row2.get(0).getAnswerId());
            assertEquals(QuestionType.DATE, row2.get(0).getQuestionType());
            assertEquals("updated composite row 2 child 1 should be updated",
                    new DateValue(2020, 9, null), row2.get(0).getValue());
            assertEquals((Long) row2Child2Id, row2.get(1).getAnswerId());
            assertEquals(QuestionType.TEXT, row2.get(1).getQuestionType());
            assertEquals("updated composite row 2 child 2 should be the same",
                    "row 2 col 2", row2.get(1).getValue());

            // delete

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());
            assertFalse("child answers should be deleted", answerDao.findAnswerById(row1Child1Id).isPresent());
            assertFalse(answerDao.findAnswerById(row2Child1Id).isPresent());
            assertFalse(answerDao.findAnswerById(row2Child2Id).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAnswer_usingQuestionId() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true);
            Answer actual = daoBuilder.buildDao(handle)
                    .createAnswer(testData.getUserId(), instanceId, act.getBoolQuestion().getQuestionId(), answer);

            assertTrue(actual.getAnswerId() > 0);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue(((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAnswer_picklist_detailsNotAllowed() {
        thrown.expect(OperationNotAllowedException.class);
        thrown.expectMessage("PO1 does not allow details");
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withPicklistSingleList(true,
                            new PicklistOptionDef("PO1", Template.text("po1")),
                            new PicklistOptionDef("PO2", Template.text("po2"), Template.text("details")))
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            PicklistAnswer answer = new PicklistAnswer(null, act.getPicklistSingleListQuestion().getStableId(), null,
                    List.of(new SelectedPicklistOption("PO1", "some details")));
            daoBuilder.buildDao(handle).createAnswer(testData.getUserId(), instanceId, answer);

            fail("expected exception not thrown");
        });
    }

    @Test
    public void testFindAnswerById_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<Answer> result = daoBuilder.buildDao(handle).findAnswerById(123456L);
            assertTrue(result.isEmpty());
            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByGuid_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<Answer> result = daoBuilder.buildDao(handle).findAnswerByGuid("abcxyz");
            assertTrue(result.isEmpty());
            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByGuid() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true);
            var answerDao = daoBuilder.buildDao(handle);
            String guid = answerDao.createAnswer(testData.getUserId(), instanceId, answer).getAnswerGuid();
            Answer actual = answerDao.findAnswerByGuid(guid).orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue(((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByInstanceIdAndQuestionStableId_notFound() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            Optional<Answer> result = answerDao.findAnswerByInstanceIdAndQuestionStableId(123456L, "abcxyz");
            assertTrue(result.isEmpty());

            result = answerDao.findAnswerByInstanceIdAndQuestionStableId(instanceId, "abcxyz");
            assertTrue(result.isEmpty());

            result = answerDao.findAnswerByInstanceIdAndQuestionStableId(instanceId, act.getBoolQuestion().getStableId());
            assertTrue(result.isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByInstanceIdAndQuestionStableId_bool() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true);
            var answerDao = daoBuilder.buildDao(handle);
            answerDao.createAnswer(testData.getUserId(), instanceId, answer);
            Answer actual = answerDao
                    .findAnswerByInstanceIdAndQuestionStableId(instanceId, act.getBoolQuestion().getStableId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue(((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByInstanceIdAndQuestionStableId_composite() {
        TransactionWrapper.useTxn(handle -> {
            DateQuestionDef childDate = DateQuestionDef
                    .builder(DateRenderMode.TEXT, "CDATE" + Instant.now().toEpochMilli(), Template.text("child date"))
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .build();
            TextQuestionDef childText = TextQuestionDef
                    .builder(TextInputType.TEXT, "CTEXT" + Instant.now().toEpochMilli(), Template.text("child text"))
                    .build();

            TestFormActivity act = TestFormActivity.builder()
                    .withCompositeQuestion(true, childDate, childText)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            CompositeAnswer answer = new CompositeAnswer(null, act.getCompositeQuestion().getStableId(), null);
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2018, 10, 24)));
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2020, 3, 14)),
                    new TextAnswer(null, childText.getStableId(), null, "row 2 col 2"));

            var answerDao = daoBuilder.buildDao(handle);
            answerDao.createAnswer(testData.getUserId(), instanceId, answer);
            Answer actual = answerDao
                    .findAnswerByInstanceIdAndQuestionStableId(instanceId, act.getCompositeQuestion().getStableId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.COMPOSITE, actual.getQuestionType());

            var rows = ((CompositeAnswer) actual).getValue();
            assertEquals(2, rows.size());
            assertEquals(1, rows.get(0).getValues().size());
            assertEquals(2, rows.get(1).getValues().size());

            var row1 = rows.get(0).getValues();
            assertEquals(QuestionType.DATE, row1.get(0).getQuestionType());
            assertEquals(new DateValue(2018, 10, 24), row1.get(0).getValue());

            var row2 = rows.get(1).getValues();
            assertEquals(QuestionType.DATE, row2.get(0).getQuestionType());
            assertEquals(new DateValue(2020, 3, 14), row2.get(0).getValue());
            assertEquals(QuestionType.TEXT, row2.get(1).getQuestionType());
            assertEquals("row 2 col 2", row2.get(1).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByLatestInstanceAndQuestionStableId_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<Answer> result = daoBuilder.buildDao(handle)
                    .findAnswerByLatestInstanceAndQuestionStableId(1L, 2L, "abcxyz");
            assertTrue(result.isEmpty());
        });
    }

    @Test
    public void testFindAnswerByLatestInstanceAndQuestionStableId() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            // Create old instance
            long instance1 = createInstance(handle, act.getDef().getActivityId()).getId();
            var answerDao = daoBuilder.buildDao(handle);
            answerDao.createAnswer(testData.getUserId(), instance1,
                    new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, false));

            // Create latest instance
            long instance2 = createInstance(handle, act.getDef().getActivityId()).getId();
            answerDao.createAnswer(testData.getUserId(), instance2,
                    new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true));

            Answer actual = answerDao.findAnswerByLatestInstanceAndQuestionStableId(
                    testData.getUserId(), testData.getStudyId(), act.getBoolQuestion().getStableId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue("should be answer from latest instance", ((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    private ActivityInstanceDto createInstance(Handle handle, long activityId) {
        return handle.attach(ActivityInstanceDao.class).insertInstance(activityId, testData.getUserGuid());
    }
}
