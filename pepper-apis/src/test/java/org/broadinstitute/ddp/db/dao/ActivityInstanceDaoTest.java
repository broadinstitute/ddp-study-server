package org.broadinstitute.ddp.db.dao;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.AnswerDao;
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
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
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

            FormActivityDef form = setupDummyActivity(handle, testData.getStudyGuid());

            ActivityInstanceDto instanceDto = dao.insertInstance(form.getActivityId(), testData.getUserGuid());
            assertTrue(instanceDto.getId() >= 0);
            assertNotNull(instanceDto.getGuid());
            assertEquals(InstanceStatusType.CREATED.name(), instanceDto.getStatusTypeCode());
            assertEquals(1, statusDao.getAllStatuses(instanceDto.getId()).size());

            ActivityInstanceDto actual = jdbiInstance
                    .getByActivityInstanceId(instanceDto.getId()).get();
            assertEquals(instanceDto.getGuid(), actual.getGuid());

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
                    testData.getUserGuid(), InstanceStatusType.CREATED, false, Instant.now().toEpochMilli(), 123L);

            assertTrue(instanceDto.getId() >= 0);
            assertNotNull(instanceDto.getGuid());
            assertEquals(InstanceStatusType.CREATED.name(), instanceDto.getStatusTypeCode());
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
                    testData.getUserGuid(), InstanceStatusType.CREATED, false, Instant.now().toEpochMilli(), 123L);

            CompositeAnswer answer = new CompositeAnswer(null, "composite", null);
            answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklist", null,
                    singletonList(new SelectedPicklistOption("op1"))));
            answer.addRowOfChildAnswers(new PicklistAnswer(null, "picklist", null,
                    singletonList(new SelectedPicklistOption("op1"))));

            String answerGuid = AnswerDao.fromSqlConfig(sqlConfig)
                    .createAnswer(handle, answer, testData.getUserGuid(), instanceDto.getGuid());

            List<FormResponse> actual = dao
                    .findFormResponsesWithAnswersByUserGuids(testData.getStudyId(), new HashSet<>(singletonList(testData.getUserGuid())))
                    .collect(Collectors.toList());

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

    private FormActivityDef setupDummyActivity(Handle handle, String studyGuid) {
        FormActivityDef form = FormActivityDef.generalFormBuilder("ACT", "v1", studyGuid)
                .addName(new Translation("en", "dummy activity"))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
        return form;
    }
}
