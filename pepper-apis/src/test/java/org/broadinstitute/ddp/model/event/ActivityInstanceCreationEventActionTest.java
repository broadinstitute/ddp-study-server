package org.broadinstitute.ddp.model.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.copy.CopyConfigurationPair;
import org.broadinstitute.ddp.model.copy.CopyLocation;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityInstanceCreationEventActionTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCreateInstanceAndCopyAnswers() {
        TransactionWrapper.useTxn(handle -> {
            // Setup source and target activities and questions
            TextQuestionDef childS1 = TextQuestionDef.builder(TextInputType.TEXT, "cs1", Template.text("")).build();
            DateQuestionDef childS2 = DateQuestionDef.builder(DateRenderMode.TEXT, "cs2", Template.text(""))
                    .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR).build();
            TestFormActivity act1 = TestFormActivity.builder()
                    .withAgreementQuestion(true)
                    .withBoolQuestion(true)
                    .withDateFullQuestion(true)
                    .withNumericIntQuestion(true)
                    .withTextQuestion(true)
                    .withPicklistSingleList(true, new PicklistOptionDef("op1", Template.text(""), Template.text("")))
                    .withCompositeQuestion(true, childS1, childS2)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            TextQuestionDef childT1 = TextQuestionDef.builder(TextInputType.TEXT, "ct1", Template.text("")).build();
            DateQuestionDef childT2 = DateQuestionDef.builder(DateRenderMode.TEXT, "ct2", Template.text(""))
                    .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR).build();
            TestFormActivity act2 = TestFormActivity.builder()
                    .withAgreementQuestion(true)
                    .withBoolQuestion(true)
                    .withDateFullQuestion(true)
                    .withNumericIntQuestion(true)
                    .withTextQuestion(true)
                    .withPicklistSingleList(true, new PicklistOptionDef("op1", Template.text(""), Template.text("")))
                    .withCompositeQuestion(true, childT1, childT2)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            // Setup source answer data
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            ActivityInstanceDto instance1 = instanceDao
                    .insertInstance(act1.getDef().getActivityId(), testData.getUserGuid());

            AnswerDao answerDao = handle.attach(AnswerDao.class);
            answerDao.createAnswer(testData.getUserId(), instance1.getId(),
                    new AgreementAnswer(null, act1.getAgreementQuestion().getStableId(), null, true));
            answerDao.createAnswer(testData.getUserId(), instance1.getId(),
                    new BoolAnswer(null, act1.getBoolQuestion().getStableId(), null, true));
            answerDao.createAnswer(testData.getUserId(), instance1.getId(),
                    new DateAnswer(null, act1.getDateFullQuestion().getStableId(), null, 1987, 3, 14));
            answerDao.createAnswer(testData.getUserId(), instance1.getId(),
                    new NumericIntegerAnswer(null, act1.getNumericIntQuestion().getStableId(), null, 21L));
            answerDao.createAnswer(testData.getUserId(), instance1.getId(),
                    new TextAnswer(null, act1.getTextQuestion().getStableId(), null, "from-source"));
            answerDao.createAnswer(testData.getUserId(), instance1.getId(),
                    new PicklistAnswer(null, act1.getPicklistSingleListQuestion().getStableId(), null, List.of(
                            new SelectedPicklistOption("op1", "details1"))));

            CompositeAnswer compAnswer = new CompositeAnswer(null, act1.getCompositeQuestion().getStableId(), null);
            compAnswer.addRowOfChildAnswers(
                    new TextAnswer(null, childS1.getStableId(), null, "row 1"),
                    new DateAnswer(null, childS2.getStableId(), null, 2020, 9, null));
            answerDao.createAnswer(testData.getUserId(), instance1.getId(), compAnswer);

            // Setup event configuration
            var config = new CopyConfiguration(testData.getStudyId(), List.of(
                    // Target profile
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act1.getTextQuestion().getStableId()),
                            new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_LAST_NAME)),
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act1.getDateFullQuestion().getStableId()),
                            new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_BIRTH_DATE)),

                    // Target various answer types
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act1.getAgreementQuestion().getStableId()),
                            new CopyAnswerLocation(act2.getAgreementQuestion().getStableId())),
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act1.getBoolQuestion().getStableId()),
                            new CopyAnswerLocation(act2.getBoolQuestion().getStableId())),
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act1.getDateFullQuestion().getStableId()),
                            new CopyAnswerLocation(act2.getDateFullQuestion().getStableId())),
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act1.getNumericIntQuestion().getStableId()),
                            new CopyAnswerLocation(act2.getNumericIntQuestion().getStableId())),
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act1.getTextQuestion().getStableId()),
                            new CopyAnswerLocation(act2.getTextQuestion().getStableId())),
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act1.getPicklistSingleListQuestion().getStableId()),
                            new CopyAnswerLocation(act2.getPicklistSingleListQuestion().getStableId())),

                    // Target composite child answers
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(childS1.getStableId()),
                            new CopyAnswerLocation(childT1.getStableId())),
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(childS2.getStableId()),
                            new CopyAnswerLocation(childT2.getStableId()))
            ));

            long triggerId = handle.attach(EventTriggerDao.class).insertStaticTrigger(EventTriggerType.USER_REGISTERED);
            long actionId = handle.attach(EventActionDao.class)
                    .insertInstanceCreationAction(act2.getDef().getActivityId(), config);
            long eventConfigId = handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId,
                    testData.getStudyId(), Instant.now().toEpochMilli(), null, null, null, null, false, 1);

            long copyConfigId = handle.createQuery("select copy_configuration_id from activity_instance_creation_action"
                    + " where activity_instance_creation_action_id = :id")
                    .bind("id", actionId).mapTo(Long.class).findOnly();

            // Trigger the action
            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getStudyId(), EventTriggerType.USER_REGISTERED);
            var eventConfig = new EventConfiguration(new EventConfigurationDto(
                    eventConfigId,
                    EventTriggerType.USER_REGISTERED,
                    EventActionType.ACTIVITY_INSTANCE_CREATION,
                    0, false, null, null, null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null, null,
                    act2.getDef().getActivityId(), copyConfigId,
                    null, null, null, null));

            long start = Instant.now().toEpochMilli();
            eventConfig.doAction(null, handle, signal);
            long elapsed = Instant.now().toEpochMilli() - start;
            System.out.println("took " + elapsed + " ms to run action");

            // Check profile
            UserProfileDto profile = handle.attach(JdbiProfile.class).getUserProfileByUserId(testData.getUserId());
            assertEquals("from-source", profile.getLastName());
            assertEquals(LocalDate.of(1987, 3, 14), profile.getBirthDate());

            // Check newly created instance
            List<FormResponse> instances = instanceDao
                    .findFormResponsesSubsetWithAnswersByUserId(testData.getUserId(), Set.of(act2.getDef().getActivityId()))
                    .collect(Collectors.toList());
            assertEquals(1, instances.size());
            FormResponse actualInstance = instances.get(0);
            assertEquals(InstanceStatusType.CREATED, actualInstance.getLatestStatus().getType());

            // Check for various target answers
            assertEquals(7, actualInstance.getAnswers().size());

            Answer actualAnswer = actualInstance.getAnswer(act2.getAgreementQuestion().getStableId());
            assertNotNull(actualAnswer);
            assertEquals(QuestionType.AGREEMENT, actualAnswer.getQuestionType());
            assertTrue(((AgreementAnswer) actualAnswer).getValue());

            actualAnswer = actualInstance.getAnswer(act2.getBoolQuestion().getStableId());
            assertNotNull(actualAnswer);
            assertEquals(QuestionType.BOOLEAN, actualAnswer.getQuestionType());
            assertTrue(((BoolAnswer) actualAnswer).getValue());

            actualAnswer = actualInstance.getAnswer(act2.getDateFullQuestion().getStableId());
            assertNotNull(actualAnswer);
            assertEquals(QuestionType.DATE, actualAnswer.getQuestionType());
            assertEquals(new DateValue(1987, 3, 14), ((DateAnswer) actualAnswer).getValue());

            actualAnswer = actualInstance.getAnswer(act2.getNumericIntQuestion().getStableId());
            assertNotNull(actualAnswer);
            assertEquals(QuestionType.NUMERIC, actualAnswer.getQuestionType());
            assertEquals((Long) 21L, ((NumericIntegerAnswer) actualAnswer).getValue());

            actualAnswer = actualInstance.getAnswer(act2.getTextQuestion().getStableId());
            assertNotNull(actualAnswer);
            assertEquals(QuestionType.TEXT, actualAnswer.getQuestionType());
            assertEquals("from-source", ((TextAnswer) actualAnswer).getValue());

            // Check for target picklist answer
            actualAnswer = actualInstance.getAnswer(act2.getPicklistSingleListQuestion().getStableId());
            assertNotNull(actualAnswer);
            assertEquals(QuestionType.PICKLIST, actualAnswer.getQuestionType());

            var selected = ((PicklistAnswer) actualAnswer).getValue();
            assertEquals(1, selected.size());
            assertEquals("op1", selected.get(0).getStableId());
            assertEquals("details1", selected.get(0).getDetailText());

            // Check for target composite answer
            actualAnswer = actualInstance.getAnswer(act2.getCompositeQuestion().getStableId());
            assertNotNull(actualAnswer);
            assertEquals(QuestionType.COMPOSITE, actualAnswer.getQuestionType());

            var rows = ((CompositeAnswer) actualAnswer).getValue();
            assertEquals(1, rows.size());

            var row1 = rows.get(0).getValues();
            assertEquals(2, row1.size());
            assertEquals(QuestionType.TEXT, row1.get(0).getQuestionType());
            assertEquals("row 1", ((TextAnswer) row1.get(0)).getValue());
            assertEquals(QuestionType.DATE, row1.get(1).getQuestionType());
            assertEquals(new DateValue(2020, 9, null), ((DateAnswer) row1.get(1)).getValue());

            handle.rollback();
        });
    }
}
