package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.util.GuidUtils.UPPER_ALPHA_NUMERIC;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.copyanswer.CompositeAnswerCopyConfiguration;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityAnswerCopierSqlTest extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityAnswerCopierSqlTest.class);

    private static final String DATE_QUESTION_STABLE_ID = "COPY_ANSWER_DATE_Q";

    private static final String TEXT_QUESTION_STABLE_ID = "COPY_ANSWER_DATE_T";

    private static final String ACTIVITY_CODE = "COPY_ANS_TEST";

    private static final String OPTION_1_STABLE_ID = "COPY_ANS_OPT1";

    private static final String OPTION_2_STABLE_ID = "COPY_ANS_OPT2";

    private static final String PICKLIST_QUESTION_STABLE_ID = "COPY_ANS_LIST";

    private static final String OPTION_OTHER_STABLE_ID = "COPY_ANS_OPT3";

    private static final String BOOLEAN_ANSWER_STABLE_ID = "COPY_ANS_BOOL";

    private static final String AGREEMENT_QUESTION_STABLE_ID = "COPY_ANS_AGREE";

    private static final String NUMERIC_QUESTION_STABLE_ID = "COPY_ANS_NUM";

    private static ActivityDef activityDef;

    private static ActivityInstanceDto sourceActivityInstance;

    private static ActivityInstanceDto destinationActivityInstance;

    private static UserDto testUser;

    private static StudyDto testStudy;

    private static QuestionDef dateQuestionDef;

    private static QuestionDef textQuestionDef;

    private static QuestionDef picklistQuestionDef;

    private static QuestionDef booleanQuestionDef;

    private static QuestionDef agreementQuestionDef;

    private static QuestionDef numericQuestionDef;

    private static Answer dateAnswer;

    private static Answer textAnswer;

    private static Answer booleanAnswer;

    private static Answer agreementAnswer;

    private static Answer numericAnswer;

    private static Answer picklistAnswer;

    private static PicklistOptionDef option1;

    private static PicklistOptionDef option2;

    private static PicklistOptionDef option3;

    private static CompositeQuestionDef compositeQuestionDef;

    private static Answer compositeAnswer;

    private static CompositeQuestionDef buildCompositeQuestion() {
        TextQuestionDef childQuestion1 = TextQuestionDef.builder(TextInputType.TEXT, "COPY_COMP1", Template.text("child text")).build();

        TextQuestionDef childQuestion2 = TextQuestionDef.builder(TextInputType.TEXT, "COPY_COMP2", Template.text("child text")).build();

        return CompositeQuestionDef.builder().setStableId("COPY_ANS_COMP").setPrompt(Template.text("Parent"))
                .setChildOrientation(OrientationType.HORIZONTAL)
                .setAllowMultiple(true)
                .addChildrenQuestions(childQuestion1, childQuestion2).build();
    }

    private static ActivityDef createTestActivityDef(Handle handle, String userGuid, String studyGuid, String activityCode) {
        // todo arz refactor, copied from JdbiActivityTest

        long millis = Instant.now().toEpochMilli();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        long revId = handle.attach(JdbiRevision.class).insert(userId, millis, null, "test");

        List<Translation> names = Collections.singletonList(new Translation("en", "dummy activity"));
        List<Translation> subtitles = Collections.singletonList(new Translation("en", "dummy subtitle"));
        List<Translation> dashboardNames = Collections.singletonList(new Translation("en", "dummy dashboard name"));
        List<Translation> descriptions = Collections.singletonList(new Translation("en", "dummy description"));
        List<SummaryTranslation> dashboardSummaries = Collections.singletonList(
                new SummaryTranslation("en", "dummy dashboard summary", InstanceStatusType.CREATED)
        );
        List<FormSectionDef> sections = new ArrayList<>();

        dateQuestionDef = DateQuestionDef.builder().addFields(DateFieldType.DAY, DateFieldType.MONTH, DateFieldType.YEAR)
                .setStableId(DATE_QUESTION_STABLE_ID).setRenderMode(DateRenderMode.PICKLIST)
        .setPrompt(Template.text("Date question")).build();

        textQuestionDef = TextQuestionDef.builder().setStableId(TEXT_QUESTION_STABLE_ID).setInputType(TextInputType.TEXT)
                .setPrompt(Template.text("Text question")).build();

        // todo arz error checking
        option1 = new PicklistOptionDef(OPTION_1_STABLE_ID, Template.text("Option 1 is great"));
        option2 = new PicklistOptionDef(OPTION_2_STABLE_ID, Template.text("Option 2 is pretty awesome too"));
        option3 = new PicklistOptionDef(OPTION_OTHER_STABLE_ID, Template.text("Option 3 is whatever you want"),
                Template.text("Type some stuff"));

        picklistQuestionDef = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, PICKLIST_QUESTION_STABLE_ID,
                Template.text("Picklist Question")).addOption(option1).addOption(option2).addOption(option3).build();

        booleanQuestionDef = BoolQuestionDef.builder(BOOLEAN_ANSWER_STABLE_ID,
                Template.text("Boolean question"), Template.text("Yes"), Template.text("No")).build();

        agreementQuestionDef = new AgreementQuestionDef(AGREEMENT_QUESTION_STABLE_ID,
                false,
                Template.text("agreement question"),
                Template.text("header"),
                Template.text("footer"),
                Collections.singletonList(new RequiredRuleDef(Template.text("Required"))),
                false);

        numericQuestionDef = NumericQuestionDef.builder().setStableId(NUMERIC_QUESTION_STABLE_ID)
                .setNumericType(NumericType.INTEGER)
                .setPlaceholderTemplate(Template.text("Put in a number"))
                .setPrompt(Template.text("A numeric question")).build();

        compositeQuestionDef = buildCompositeQuestion();

        List<FormBlockDef> blocks = new ArrayList<>();
        blocks.add(new QuestionBlockDef(dateQuestionDef));
        blocks.add(new QuestionBlockDef(textQuestionDef));
        blocks.add(new QuestionBlockDef(picklistQuestionDef));
        blocks.add(new QuestionBlockDef(booleanQuestionDef));
        blocks.add(new QuestionBlockDef(agreementQuestionDef));
        blocks.add(new QuestionBlockDef(numericQuestionDef));
        blocks.add(new QuestionBlockDef(compositeQuestionDef));

        FormSectionDef formSectionDef = new FormSectionDef("COPY_ANSWER_TEST", blocks);

        sections.add(formSectionDef);

        Template readonlyHint = Template.html("Please contact your organization");
        FormActivityDef form = new FormActivityDef(
                FormType.GENERAL, activityCode, "v1", studyGuid, 1, 1, false,
                names, subtitles, dashboardNames, descriptions, dashboardSummaries,
                readonlyHint, null, sections, null, null, null, false
        );
        handle.attach(FormActivityDao.class).insertActivity(form, revId);

        assertNotNull(form.getActivityId());
        return form;
    }

    @BeforeClass
    public static void createTestingUser() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.GeneratedTestData generatedTestData = TestDataSetupUtil.generateBasicUserTestData(handle);
            testStudy = TestDataSetupUtil.generateTestStudy(handle, cfg);
            String userGuid = DBUtils.uniqueUserGuid(handle);
            String userHruid = DBUtils.uniqueUserHruid(handle);
            long testUserId = handle.attach(JdbiUser.class)
                    .insert(GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                            userGuid,
                            generatedTestData.getClientId(),
                            userHruid);
            testUser = handle.attach(JdbiUser.class).findByUserId(testUserId);

            activityDef = createTestActivityDef(handle, testUser.getUserGuid(), testStudy.getGuid(), ACTIVITY_CODE);

            ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);

            sourceActivityInstance = activityInstanceDao.insertInstance(activityDef.getActivityId(), testUser.getUserGuid());

            destinationActivityInstance = activityInstanceDao.insertInstance(activityDef.getActivityId(), testUser.getUserGuid());

            JdbiAnswer jdbiAnswer = handle.attach(JdbiAnswer.class);
            AnswerDao answerDao = handle.attach(AnswerDao.class);
            JdbiAgreementAnswer jdbiAgreementAnswer = handle.attach(JdbiAgreementAnswer.class);
            JdbiBooleanAnswer jdbiBooleanAnswer = handle.attach(JdbiBooleanAnswer.class);
            JdbiDateAnswer jdbiDateAnswer = handle.attach(JdbiDateAnswer.class);
            JdbiTextAnswer jdbiTextAnswer = handle.attach(JdbiTextAnswer.class);
            JdbiPicklistOption jdbiPicklistOption = handle.attach(JdbiPicklistOption.class);
            JdbiPicklistOptionAnswer jdbiPicklistOptionAnswer = handle.attach(JdbiPicklistOptionAnswer.class);
            JdbiCompositeAnswer jdbiCompositeAnswer = handle.attach(JdbiCompositeAnswer.class);

            long dateAnswerId = jdbiAnswer.insertBaseAnswer(dateQuestionDef.getQuestionId(),
                    testUser.getUserGuid(), sourceActivityInstance.getId());
            jdbiDateAnswer.insertAnswer(dateAnswerId, new DateValue(1, 1, 2020));

            dateAnswer = answerDao.getAnswerByIdAndType(dateAnswerId, QuestionType.DATE);

            textAnswer = newTextAnswer(textQuestionDef, sourceActivityInstance.getId(), jdbiAnswer, jdbiTextAnswer, "text");


            long booleanAnswerId = jdbiAnswer.insertBaseAnswer(booleanQuestionDef.getQuestionId(), testUser.getUserGuid(),
                    sourceActivityInstance.getId());

            jdbiBooleanAnswer.insert(booleanAnswerId, false);

            booleanAnswer = answerDao.getAnswerByIdAndType(booleanAnswerId, QuestionType.BOOLEAN);

            long agreementAnswerId = jdbiAnswer.insertBaseAnswer(agreementQuestionDef.getQuestionId(), testUser.getUserGuid(),
                    sourceActivityInstance.getId());

            jdbiAgreementAnswer.insert(agreementAnswerId, true);

            agreementAnswer = answerDao.getAnswerByIdAndType(agreementAnswerId, QuestionType.AGREEMENT);

            long numericAnswerId = jdbiAnswer.insertBaseAnswer(numericQuestionDef.getQuestionId(), testUser.getUserGuid(),
                    sourceActivityInstance.getId());

            handle.attach(JdbiNumericAnswer.class).insertNumericInteger(numericAnswerId, 47L);

            numericAnswer = answerDao.getAnswerByIdAndType(numericAnswerId, QuestionType.NUMERIC);

            long picklistAnswerId = jdbiAnswer.insertBaseAnswer(picklistQuestionDef.getQuestionId(),
                    testUser.getUserGuid(), sourceActivityInstance.getId());

            List<PicklistOptionDto> picklistOptions = jdbiPicklistOption.findAllActiveOrderedOptionsByQuestionId(
                    picklistQuestionDef.getQuestionId());

            List<Long> selectedIds = new ArrayList<>();
            List<String> otherOptions = new ArrayList<>();
            for (PicklistOptionDto picklistOption : picklistOptions) {
                if (OPTION_2_STABLE_ID.equals(picklistOption.getStableId())) {
                    selectedIds.add(picklistOption.getId());
                    otherOptions.add(null);
                } else if (OPTION_OTHER_STABLE_ID.equals(picklistOption.getStableId())) {
                    selectedIds.add(picklistOption.getId());
                    otherOptions.add("Additional info for " + picklistOption.getStableId());
                }
            }

            jdbiPicklistOptionAnswer.bulkInsert(picklistAnswerId, selectedIds, otherOptions);

            picklistAnswer = answerDao.getAnswerByIdAndType(picklistAnswerId, QuestionType.PICKLIST);

            long compositeAnswerId = jdbiAnswer.insertBaseAnswer(compositeQuestionDef.getQuestionId(), testUser.getUserGuid(),
                    sourceActivityInstance.getId());

            // insert two child rows
            TextAnswer firstChild = newTextAnswer(compositeQuestionDef.getChildren().get(0), sourceActivityInstance.getId(), jdbiAnswer,
                    jdbiTextAnswer, "first");

            TextAnswer secondChild = newTextAnswer(compositeQuestionDef.getChildren().get(1), sourceActivityInstance.getId(), jdbiAnswer,
                    jdbiTextAnswer, "second");

            jdbiCompositeAnswer.insertChildAnswerItems(compositeAnswerId, Arrays.asList(firstChild.getAnswerId(),
                    secondChild.getAnswerId()), Arrays.asList(1, 2));

            compositeAnswer = answerDao.getAnswerByIdAndType(compositeAnswerId, QuestionType.COMPOSITE);

        });

    }

    private static TextAnswer newTextAnswer(QuestionDef questionDef, long sourceActivityInstanceId, JdbiAnswer jdbiAnswer,
                                            JdbiTextAnswer jdbiTextAnswer, String textAnswerPrefix) {
        long textAnswerId = jdbiAnswer.insertBaseAnswer(questionDef.getQuestionId(),
                testUser.getUserGuid(), sourceActivityInstanceId);
        jdbiTextAnswer.insert(textAnswerId, textAnswerPrefix + System.currentTimeMillis());

        return jdbiTextAnswer.findByAnswerId(textAnswerId);
    }

    @Test
    public void testCopyForAllQuestionTypes() {
        CompositeAnswerCopyConfiguration compositeCopyConfig = new CompositeAnswerCopyConfiguration();

        // for our tests, since we're copying data between instances of the same activity definition,
        // we'll use the same child question stable ids.
        for (QuestionDef childQuestionDef : compositeQuestionDef.getChildren()) {
            compositeCopyConfig.addChildCopyConfiguration(childQuestionDef.getStableId(), childQuestionDef.getStableId());
        }

        TransactionWrapper.useTxn(handle -> {
            LOG.info("Created activity instance {} for user {} in study {}", sourceActivityInstance.getGuid(), testUser.getUserGuid(),
                    testStudy.getGuid());
            doAnswerVerifications(handle, new SourceQuestion(dateQuestionDef, dateAnswer),
                    new SourceQuestion(textQuestionDef, textAnswer),
                    new SourceQuestion(picklistQuestionDef, picklistAnswer),
                    new SourceQuestion(booleanQuestionDef, booleanAnswer),
                    new SourceQuestion(agreementQuestionDef, agreementAnswer),
                    new SourceQuestion(numericQuestionDef, numericAnswer),
                    new SourceQuestion(compositeQuestionDef, compositeAnswer, compositeCopyConfig));
        });
    }

    private void doAnswerVerifications(Handle handle, SourceQuestion... questionsToCopy) {
        int initialNumberOfAnswers = handle.createQuery("select count(1) from answer").mapTo(Integer.class).findOnly();

        JdbiAnswer jdbiAnswerDao = handle.attach(JdbiAnswer.class);
        long createdAt = System.currentTimeMillis();
        long lastUpdatedAt = createdAt + 2;

        ActivityAnswerCopierSql answerCopierSql = handle.attach(ActivityAnswerCopierSql.class);

        int numNewAnswersExpected = questionsToCopy.length;

        for (SourceQuestion srcQuestion : questionsToCopy) {
            QuestionDef sourceQuestionDef = srcQuestion.getSourceQuestionDef();
            Answer sourceAnswer = srcQuestion.getSourceAnswer();
            LOG.info("Testing copy of " + sourceQuestionDef.getQuestionType() + " question " + sourceQuestionDef.getStableId());
            long copiedAnswerId = answerCopierSql.copyAnswer(sourceQuestionDef.getStableId(),
                    sourceQuestionDef.getStableId(), sourceActivityInstance.getId(), destinationActivityInstance.getId(), createdAt,
                    lastUpdatedAt, srcQuestion.getCompositeCopyConfiguration()).getDestinationAnswerId();

            Optional<AnswerDto> copiedAnswerDto = jdbiAnswerDao.findDtoById(copiedAnswerId);

            Assert.assertTrue(copiedAnswerDto.isPresent());
            Assert.assertEquals(sourceQuestionDef.getStableId(), copiedAnswerDto.get().getQuestionStableId());
            Assert.assertEquals(sourceQuestionDef.getQuestionType(), copiedAnswerDto.get().getQuestionType());
            Assert.assertEquals(sourceQuestionDef.getQuestionId().longValue(), copiedAnswerDto.get().getQuestionId());
            Assert.assertEquals(createdAt, copiedAnswerDto.get().getCreatedAt());
            Assert.assertEquals(lastUpdatedAt, copiedAnswerDto.get().getLastUpdatedAt());

            Answer copiedAnswer = handle.attach(AnswerDao.class).getAnswerByIdAndType(copiedAnswerId, sourceQuestionDef.getQuestionType());

            if (sourceAnswer.getQuestionType() == QuestionType.COMPOSITE) {
                numNewAnswersExpected += ((CompositeAnswer)sourceAnswer).getValue().size();

                List<Object> originalAnswerValues = new ArrayList<>();
                List<Object> copiedAnswerValues = new ArrayList<>();

                for (AnswerRow originalRow : (List<AnswerRow>)sourceAnswer.getValue()) {
                    for (Answer originalChildAnswer : originalRow.getValues()) {
                        if (originalChildAnswer != null) {
                            originalAnswerValues.add(originalChildAnswer.getValue());
                        }
                    }
                }

                for (AnswerRow copiedRow : (List<AnswerRow>)copiedAnswer.getValue()) {
                    for (Answer copiedChildAnswer : copiedRow.getValues()) {
                        if (copiedChildAnswer != null) {
                            copiedAnswerValues.add(copiedChildAnswer.getValue());
                        }
                    }
                }
                Assert.assertEquals(originalAnswerValues, copiedAnswerValues);

            } else {
                Assert.assertEquals(sourceAnswer.getValue(), copiedAnswer.getValue());
            }
        }

        int newAnswerCount = handle.createQuery("select count(1) from answer").mapTo(Integer.class).findOnly();
        Assert.assertEquals("Number of answer rows went from " + initialNumberOfAnswers + " to " + newAnswerCount,
                initialNumberOfAnswers + numNewAnswersExpected, newAnswerCount);

    }

    /**
     * Tuple to keep track of source question and answers
     */
    private static class SourceQuestion<T> {

        private final QuestionDef sourceQuestionDef;

        private final Answer<T> sourceAnswer;

        private CompositeAnswerCopyConfiguration compositeCopyConfiguration;

        public SourceQuestion(QuestionDef sourceQuestionDef, Answer<T> sourceAnswer) {
            this.sourceQuestionDef = sourceQuestionDef;
            this.sourceAnswer = sourceAnswer;
        }

        public SourceQuestion(QuestionDef sourceQuestionDef, Answer<T> sourceAnswer,
                              CompositeAnswerCopyConfiguration compositeCopyConfiguration) {
            this(sourceQuestionDef, sourceAnswer);
            this.compositeCopyConfiguration = compositeCopyConfiguration;
        }

        public QuestionDef getSourceQuestionDef() {
            return sourceQuestionDef;
        }

        public Answer<T> getSourceAnswer() {
            return sourceAnswer;
        }

        public CompositeAnswerCopyConfiguration getCompositeCopyConfiguration() {
            return compositeCopyConfiguration;
        }
    }
}
