package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.EquationQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.equation.QuestionEvaluator;
import org.broadinstitute.ddp.json.EquationResponse;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.EquationQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class EvaluationDaoTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testSimpleEvaluation() {
        TransactionWrapper.useTxn(handle -> {
            final DecimalQuestionDef questionDef = DecimalQuestionDef
                    .builder("Q_VALUE", Template.text("This is value"))
                    .setScale(2)
                    .build();

            final FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), questionDef);
            final ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            final ActivityInstanceDto instanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceDto.getId(),
                    new DecimalAnswer(null, questionDef.getStableId(), null, new DecimalDef(BigDecimal.TEN)));

            final EquationQuestionDef equationQuestionDef = EquationQuestionDef.builder()
                    .stableId("EQ1")
                    .questionType(QuestionType.EQUATION)
                    .promptTemplate(new Template(TemplateType.TEXT, null, "Equation"))
                    .validations(new ArrayList<>())
                    .expression("5 * " + questionDef.getStableId())
                    .build();

            handle.attach(QuestionDao.class).insertQuestion(form.getActivityId(), equationQuestionDef, version1.getRevId());

            final Optional<QuestionDto> equationDto = handle.attach(QuestionDao.class).getJdbiQuestion()
                    .findDtoByStableIdAndInstanceGuid(equationQuestionDef.getStableId(), instanceDto.getGuid());
            if (equationDto.isEmpty()) {
                throw new RuntimeException("Can't fetch equation dto");
            }

            final var questionEvaluator = new QuestionEvaluator(handle, instanceDto.getGuid());
            final EquationResponse response = questionEvaluator.evaluate((EquationQuestionDto) equationDto.get());

            assertNotNull(response);
            assertEquals(equationQuestionDef.getStableId(), response.getQuestionStableId());
            assertEquals(1, response.getValues().size());
            assertEquals(0, BigDecimal.valueOf(50).compareTo(response.getValues().get(0).toBigDecimal()));

            handle.rollback();
        });
    }

    @Test
    public void testNoValue() {
        TransactionWrapper.useTxn(handle -> {
            final DecimalQuestionDef questionDef = DecimalQuestionDef
                    .builder("Q_VALUE", Template.text("This is value"))
                    .setScale(2)
                    .build();

            final FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), questionDef);
            final ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            final ActivityInstanceDto instanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            final EquationQuestionDef equationQuestionDef = EquationQuestionDef.builder()
                    .stableId("EQ1")
                    .questionType(QuestionType.EQUATION)
                    .promptTemplate(new Template(TemplateType.TEXT, null, "Equation"))
                    .validations(new ArrayList<>())
                    .expression("5 * NOT_EXISTING_QUESTION")
                    .build();

            handle.attach(QuestionDao.class).insertQuestion(form.getActivityId(), equationQuestionDef, version1.getRevId());

            final Optional<QuestionDto> equationDto = handle.attach(QuestionDao.class).getJdbiQuestion()
                    .findDtoByStableIdAndInstanceGuid(equationQuestionDef.getStableId(), instanceDto.getGuid());
            if (equationDto.isEmpty()) {
                throw new RuntimeException("Can't fetch equation dto");
            }

            final var questionEvaluator = new QuestionEvaluator(handle, instanceDto.getGuid());
            assertNull(questionEvaluator.evaluate((EquationQuestionDto) equationDto.get()));

            handle.rollback();
        });
    }

    @Test
    public void testAreaEvaluation() {
        TransactionWrapper.useTxn(handle -> {
            final DecimalQuestionDef questionWidthDef = DecimalQuestionDef
                    .builder("Q_WIDTH", Template.text("This is width"))
                    .setScale(2)
                    .build();

            final DecimalQuestionDef questionLengthDef = DecimalQuestionDef
                    .builder("Q_LENGTH", Template.text("This is length"))
                    .setScale(2)
                    .build();

            final FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(), questionWidthDef, questionLengthDef);
            final ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            final ActivityInstanceDto instanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceDto.getId(),
                    new DecimalAnswer(null, questionWidthDef.getStableId(), null, new DecimalDef(BigDecimal.TEN)));

            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceDto.getId(),
                    new DecimalAnswer(null, questionLengthDef.getStableId(), null, new DecimalDef(BigDecimal.valueOf(2.5))));

            final EquationQuestionDef equationQuestionDef = EquationQuestionDef.builder()
                    .stableId("EQ_AREA")
                    .questionType(QuestionType.EQUATION)
                    .promptTemplate(new Template(TemplateType.TEXT, null, "Equation"))
                    .validations(new ArrayList<>())
                    .expression(String.format("%s * %s", questionWidthDef.getStableId(), questionLengthDef.getStableId()))
                    .build();

            handle.attach(QuestionDao.class).insertQuestion(form.getActivityId(), equationQuestionDef, version1.getRevId());

            final Optional<QuestionDto> equationDto = handle.attach(QuestionDao.class).getJdbiQuestion()
                    .findDtoByStableIdAndInstanceGuid(equationQuestionDef.getStableId(), instanceDto.getGuid());
            if (equationDto.isEmpty()) {
                throw new RuntimeException("Can't fetch equation dto");
            }

            final var questionEvaluator = new QuestionEvaluator(handle, instanceDto.getGuid());
            final EquationResponse response = questionEvaluator.evaluate((EquationQuestionDto) equationDto.get());

            assertNotNull(response);
            assertEquals(equationQuestionDef.getStableId(), response.getQuestionStableId());
            assertEquals(1, response.getValues().size());
            assertEquals(0, BigDecimal.valueOf(25).compareTo(response.getValues().get(0).toBigDecimal()));

            handle.rollback();
        });
    }

    @Test
    public void testHierarchicalEvaluation() {
        TransactionWrapper.useTxn(handle -> {
            final DecimalQuestionDef questionWidthDef = DecimalQuestionDef
                    .builder("Q_WIDTH", Template.text("This is width"))
                    .setScale(2)
                    .build();

            final DecimalQuestionDef questionLengthDef = DecimalQuestionDef
                    .builder("Q_LENGTH", Template.text("This is length"))
                    .setScale(2)
                    .build();

            final DecimalQuestionDef questionHeightDef = DecimalQuestionDef
                    .builder("Q_HEIGHT", Template.text("This is height"))
                    .setScale(2)
                    .build();

            final FormActivityDef form = buildSingleSectionForm(testData.getStudyGuid(),
                    questionWidthDef, questionLengthDef, questionHeightDef);
            final ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            final ActivityInstanceDto instanceDto = TestDataSetupUtil
                    .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid());

            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceDto.getId(),
                    new DecimalAnswer(null, questionWidthDef.getStableId(), null, new DecimalDef(BigDecimal.TEN)));

            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceDto.getId(),
                    new DecimalAnswer(null, questionLengthDef.getStableId(), null, new DecimalDef(BigDecimal.valueOf(2.5))));

            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceDto.getId(),
                    new DecimalAnswer(null, questionHeightDef.getStableId(), null, new DecimalDef(BigDecimal.valueOf(2))));

            final EquationQuestionDef equationAreaDef = EquationQuestionDef.builder()
                    .stableId("EQ_AREA")
                    .questionType(QuestionType.EQUATION)
                    .promptTemplate(new Template(TemplateType.TEXT, null, "Area"))
                    .validations(new ArrayList<>())
                    .expression(String.format("%s * %s", questionWidthDef.getStableId(), questionLengthDef.getStableId()))
                    .build();

            handle.attach(QuestionDao.class).insertQuestion(form.getActivityId(), equationAreaDef, version1.getRevId());

            final EquationQuestionDef equationVolumeDef = EquationQuestionDef.builder()
                    .stableId("EQ_VOLUME")
                    .questionType(QuestionType.EQUATION)
                    .promptTemplate(new Template(TemplateType.TEXT, null, "Volume"))
                    .validations(new ArrayList<>())
                    .expression(String.format("%s * %s", equationAreaDef.getStableId(), questionHeightDef.getStableId()))
                    .build();

            handle.attach(QuestionDao.class).insertQuestion(form.getActivityId(), equationVolumeDef, version1.getRevId());

            final Optional<QuestionDto> equationDto = handle.attach(QuestionDao.class).getJdbiQuestion()
                    .findDtoByStableIdAndInstanceGuid(equationVolumeDef.getStableId(), instanceDto.getGuid());
            if (equationDto.isEmpty()) {
                throw new RuntimeException("Can't fetch equation dto");
            }

            final var questionEvaluator = new QuestionEvaluator(handle, instanceDto.getGuid());
            final EquationResponse response = questionEvaluator.evaluate((EquationQuestionDto) equationDto.get());

            assertNotNull(response);
            assertEquals(equationVolumeDef.getStableId(), response.getQuestionStableId());
            assertEquals(1, response.getValues().size());
            assertEquals(0, BigDecimal.valueOf(50).compareTo(response.getValues().get(0).toBigDecimal()));

            handle.rollback();
        });
    }

    private FormActivityDef buildSingleSectionForm(String studyGuid, QuestionDef... questions) {
        return FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(questions)))
                .build();
    }
}
