package org.broadinstitute.ddp.db.dao;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityInstanceDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testInsertInstance() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao dao = handle.attach(ActivityInstanceDao.class);
            ActivityInstanceStatusDao statusDao = handle.attach(ActivityInstanceStatusDao.class);
            JdbiActivityInstance jdbiInstance = handle.attach(JdbiActivityInstance.class);

            TestFormActivity act = TestFormActivity.builder()
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            ActivityInstanceDto instanceDto = dao.insertInstance(act.getDef().getActivityId(), testData.getUserGuid());
            assertTrue(instanceDto.getId() >= 0);
            assertNotNull(instanceDto.getGuid());
            assertEquals(InstanceStatusType.CREATED, instanceDto.getStatusType());
            assertEquals(1, statusDao.getAllStatuses(instanceDto.getId()).size());

            ActivityInstanceDto actual = jdbiInstance
                    .getByActivityInstanceId(instanceDto.getId()).get();
            assertEquals(instanceDto.getGuid(), actual.getGuid());

            handle.rollback();
        });
    }

    @Test
    public void testInsertWithParentInstance() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(ActivityInstanceDao.class);
            var statusDao = handle.attach(ActivityInstanceStatusDao.class);
            var jdbiInstance = handle.attach(JdbiActivityInstance.class);

            var parentAct = FormActivityDef
                    .generalFormBuilder("ACT_PARENT_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "parent"))
                    .build();
            var nestedAct = FormActivityDef
                    .generalFormBuilder("ACT_NESTED_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "child"))
                    .setParentActivityCode(parentAct.getActivityCode())
                    .build();
            handle.attach(ActivityDao.class).insertActivity(parentAct, List.of(nestedAct),
                    RevisionMetadata.now(testData.getUserId(), "test activity"));
            ActivityInstanceDto parentInstanceDto = dao.insertInstance(parentAct.getActivityId(), testData.getUserGuid());

            ActivityInstanceDto nestedInstanceDto = dao.insertInstance(nestedAct.getActivityId(),
                    testData.getUserGuid(), testData.getUserGuid(), parentInstanceDto.getId());
            assertTrue(nestedInstanceDto.getId() >= 0);
            assertNotNull(nestedInstanceDto.getGuid());
            assertEquals(InstanceStatusType.CREATED, nestedInstanceDto.getStatusType());
            assertEquals(1, statusDao.getAllStatuses(nestedInstanceDto.getId()).size());
            assertEquals((Long) parentInstanceDto.getId(), nestedInstanceDto.getParentInstanceId());
            assertEquals(parentAct.getActivityId(), nestedInstanceDto.getParentActivityId());
            assertEquals(parentAct.getActivityCode(), nestedInstanceDto.getParentActivityCode());

            ActivityInstanceDto actual = jdbiInstance
                    .getByActivityInstanceId(nestedInstanceDto.getId()).get();
            assertEquals(nestedInstanceDto.getGuid(), actual.getGuid());

            handle.rollback();
        });
    }

    @Test
    public void testCreateOnParentCreation() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(ActivityInstanceDao.class);
            var jdbiInstance = handle.attach(JdbiActivityInstance.class);

            var parentAct = FormActivityDef
                    .generalFormBuilder("ACT_PARENT_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "parent"))
                    .build();
            var nestedAct = FormActivityDef
                    .generalFormBuilder("ACT_NESTED_" + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "child"))
                    .setParentActivityCode(parentAct.getActivityCode())
                    .setCreateOnParentCreation(true)
                    .build();
            handle.attach(ActivityDao.class).insertActivity(parentAct, List.of(nestedAct),
                    RevisionMetadata.now(testData.getUserId(), "test activity"));

            ActivityInstanceDto parentInstanceDto = dao.insertInstance(parentAct.getActivityId(), testData.getUserGuid());
            List<ActivityInstanceDto> actualInstances = jdbiInstance.findAllByUserGuidAndActivityCode(
                    testData.getUserGuid(), nestedAct.getActivityCode(), testData.getStudyId());
            assertEquals(1, actualInstances.size());

            ActivityInstanceDto nestedInstanceDto = actualInstances.get(0);
            assertEquals(InstanceStatusType.CREATED, nestedInstanceDto.getStatusType());
            assertEquals((Long) parentInstanceDto.getId(), nestedInstanceDto.getParentInstanceId());

            handle.rollback();
        });
    }

    @Test
    public void testInsertTriggeredInstance() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao dao = handle.attach(ActivityInstanceDao.class);
            ActivityInstanceStatusDao statusDao = handle.attach(ActivityInstanceStatusDao.class);
            JdbiActivityInstance jdbiInstance = handle.attach(JdbiActivityInstance.class);

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "dummy activity"))
                    .setAllowOndemandTrigger(true)
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            assertNotNull(form.getActivityId());

            ActivityInstanceDto instanceDto = dao.insertInstance(form.getActivityId(), testData.getUserGuid(),
                    testData.getUserGuid(), InstanceStatusType.CREATED, false, Instant.now().toEpochMilli(), 123L, null);

            assertTrue(instanceDto.getId() >= 0);
            assertNotNull(instanceDto.getGuid());
            assertEquals(InstanceStatusType.CREATED, instanceDto.getStatusType());
            assertEquals(1, statusDao.getAllStatuses(instanceDto.getId()).size());

            ActivityInstanceDto actual = jdbiInstance.getByActivityInstanceId(instanceDto.getId()).get();
            assertEquals(instanceDto.getGuid(), actual.getGuid());
            assertEquals((Long) 123L, actual.getOnDemandTriggerId());

            handle.rollback();
        });
    }

    @Test
    public void testFindFormResponsesWithAnswers_compositeChildPicklistAnswers() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao dao = handle.attach(ActivityInstanceDao.class);

            FormActivityDef form = FormActivityDef.generalFormBuilder("ACT", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "dummy activity"))
                    .addSection(new FormSectionDef(null, singletonList(new QuestionBlockDef(CompositeQuestionDef.builder()
                            .setStableId("composite")
                            .setPrompt(Template.text("composite question"))
                            .setAllowMultiple(true)
                            .addChildrenQuestions(PicklistQuestionDef
                                    .buildMultiSelect(PicklistRenderMode.LIST, "picklist", Template.text("prompt"))
                                    .addOption(new PicklistOptionDef("op1", Template.text("option")))
                                    .build())
                            .build()
                    ))))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            assertNotNull(form.getActivityId());

            ActivityInstanceDto instanceDto = dao.insertInstance(form.getActivityId(), testData.getUserGuid(),
                    testData.getUserGuid(), InstanceStatusType.CREATED, false, Instant.now().toEpochMilli(), 123L, null);

            CompositeAnswer answer = new CompositeAnswer(null, "composite", null);
            answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklist", null,
                    singletonList(new SelectedPicklistOption("op1"))));
            answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklist", null,
                    singletonList(new SelectedPicklistOption("op1"))));

            String answerGuid = handle.attach(AnswerDao.class)
                    .createAnswer(testData.getUserId(), instanceDto.getId(), answer)
                    .getAnswerGuid();

            List<FormResponse> actual;
            try (Stream<FormResponse> responseStream = dao
                    .findFormResponsesWithAnswersByUserGuids(testData.getStudyId(), new HashSet<>(singletonList(testData.getUserGuid())))) {
                actual = responseStream.collect(Collectors.toList());
            }

            assertEquals(1, actual.size());
            assertEquals(1, actual.get(0).getAnswers().size());

            CompositeAnswer actualAnswer = (CompositeAnswer) actual.get(0).getAnswer("composite");
            assertEquals(answerGuid, actualAnswer.getAnswerGuid());
            assertEquals(2, actualAnswer.getValue().size());

            List<Answer> row1 = actualAnswer.getValue().get(0).getValues();
            assertEquals(1, row1.size());
            assertEquals(1, ((PicklistAnswer) row1.get(0)).getValue().size());

            List<Answer> row2 = actualAnswer.getValue().get(1).getValues();
            assertEquals(1, row2.size());
            assertEquals(1, ((PicklistAnswer) row2.get(0)).getValue().size());

            handle.rollback();
        });
    }

    @Test
    public void testFindFormResponsesSubsetWithAnswersByUserId() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao dao = handle.attach(ActivityInstanceDao.class);

            TestFormActivity act1 = TestFormActivity.builder()
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            dao.insertInstance(act1.getDef().getActivityId(), testData.getUserGuid());

            TestFormActivity act2 = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long activity2Id = act2.getDef().getActivityId();
            long instance2Id = dao.insertInstance(activity2Id, testData.getUserGuid()).getId();

            var answer = new TextAnswer(null, act2.getTextQuestion().getStableId(), null, "my-text");
            answer = (TextAnswer) handle.attach(AnswerDao.class)
                    .createAnswer(testData.getUserId(), instance2Id, answer);

            List<FormResponse> actual = dao
                    .findFormResponsesSubsetWithAnswersByUserId(testData.getUserId(), Set.of(activity2Id))
                    .collect(Collectors.toList());
            assertEquals(1, actual.size());

            FormResponse resp = actual.get(0);
            assertEquals(activity2Id, resp.getActivityId());
            assertEquals(1, resp.getAnswers().size());
            assertEquals(answer.getAnswerGuid(), resp.getAnswers().get(0).getAnswerGuid());

            handle.rollback();
        });
    }

    @Test
    public void testBulkFindSubstitutions() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(ActivityInstanceDao.class);
            try (var stream = dao.bulkFindSubstitutions(Collections.emptySet())) {
                var result = stream.collect(Collectors.toList());
                assertTrue("should return empty list", result.isEmpty());
            }
        });
    }

    @Test
    public void testFindMaxInstancesSeenPerUserByActivityAndVersion() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao dao = handle.attach(ActivityInstanceDao.class);
            TestFormActivity act = TestFormActivity.builder()
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long activityId = act.getDef().getActivityId();
            long versionId = act.getVersionDto().getId();

            assertTrue("no instances so should be empty",
                    dao.findMaxInstancesSeenPerUserByActivityAndVersion(activityId, versionId).isEmpty());

            dao.insertInstance(activityId, testData.getUserGuid());
            dao.insertInstance(activityId, testData.getUserGuid());

            assertEquals(Integer.valueOf(2),
                    dao.findMaxInstancesSeenPerUserByActivityAndVersion(activityId, versionId).orElse(0));

            handle.rollback();
        });
    }

    @Test
    public void testUpdateActivityInstanceCreationMutex() throws InterruptedException {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao dao = handle.attach(ActivityInstanceDao.class);
            TestFormActivity act = TestFormActivity.builder()
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var time = Instant.now();
            int count = dao.upsertActivityInstanceCreationMutex(testData.getUserId(), testData.getStudyId(),
                    act.getDef().getActivityCode(), time);
            assertEquals(1, count);
            time = Instant.now();
            dao.upsertActivityInstanceCreationMutex(testData.getUserId(), testData.getStudyId(),
                    act.getDef().getActivityCode(), time);
            var timeRead = dao.getActivityInstanceCreationMutexLastUpdate(testData.getUserId(), testData.getStudyId(),
                    act.getDef().getActivityCode());
            assertTrue(time.equals(timeRead));
            Thread.sleep(10);
            time = Instant.now();
            dao.upsertActivityInstanceCreationMutex(testData.getUserId(), testData.getStudyId(),
                    act.getDef().getActivityCode(), time);
            timeRead = dao.getActivityInstanceCreationMutexLastUpdate(testData.getUserId(), testData.getStudyId(),
                    act.getDef().getActivityCode());
            assertTrue(time.equals(timeRead));

            handle.rollback();
        });
    }

    @Test
    public void testFindSubstitutionNamesSeenAcrossUsersByActivityAndVersion() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao dao = handle.attach(ActivityInstanceDao.class);
            TestFormActivity act = TestFormActivity.builder()
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long activityId = act.getDef().getActivityId();
            long versionId = act.getVersionDto().getId();

            ActivityInstanceDto instanceDto = dao.insertInstance(activityId, testData.getUserGuid());
            assertTrue("should not have any substitutions yet",
                    dao.findSubstitutionNamesSeenAcrossUsersByActivityAndVersion(activityId, versionId).isEmpty());

            Map<String, String> substitutions = new HashMap<>();
            substitutions.put("foo", "1");
            substitutions.put("bar", "2");
            dao.saveSubstitutions(instanceDto.getId(), substitutions);

            List<String> actualNames = dao
                    .findSubstitutionNamesSeenAcrossUsersByActivityAndVersion(activityId, versionId);
            assertEquals(2, actualNames.size());
            assertTrue(actualNames.contains("foo"));
            assertTrue(actualNames.contains("bar"));

            handle.rollback();
        });
    }
}
