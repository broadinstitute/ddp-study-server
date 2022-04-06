package org.broadinstitute.ddp.model.event.activityinstancecreation;

import static java.util.Collections.singletonList;
import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
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
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.event.ActivityInstanceStatusChangeSignal;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests {@link ActivityInstanceCreationFromAnswersEventSyncProcessor}
 */
public class ActivityInstanceCreationFromAnswersEventSyncProcessorTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    private static final String SOURCE_QUESTION__STABLE_ID = "COMPOSITE_SOURCE";
    private static final String PICKLIST_SOURCE__STABLE_ID = "PICKLIST_SOURCE";
    private static final String[] PICKLIST_OPTION__STABLE_ID = {"PO1", "PO2"};
    private static final String[] PICKLIST_OPTION__TPL_TEXT = {"po1", "po2"};
    private static final String TARGET_QUESTION__STABLE_ID = "PICKLIST_TARGET";


    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    /**
     * Check the creation of activity instances from answers entered in the current (source) instance.
     * Verify that instances were created and answers were copied to target.
     */
    @Test
    public void testActivityInstanceCreationFromAnswers() {
        TransactionWrapper.useTxn(handle -> {

            // create source (current) instance
            FormActivityDef sourceFormDef = createSourceActivityDef(handle);
            ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
            ActivityInstanceDto instanceDto = activityInstanceDao.insertInstance(sourceFormDef.getActivityId(), testData.getUserGuid());

            // create source answer (composite answer with 2 picklists and a selected option in each)
            CompositeAnswer sourceAnswer = new CompositeAnswer(null, SOURCE_QUESTION__STABLE_ID, null);
            for (int i = 0; i < PICKLIST_OPTION__STABLE_ID.length; i++) {
                sourceAnswer.addRowOfChildAnswers(new PicklistAnswer(null, PICKLIST_SOURCE__STABLE_ID, null,
                        singletonList(new SelectedPicklistOption(PICKLIST_OPTION__STABLE_ID[i]))));
            }
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceDto.getId(), sourceAnswer);

            // create target activity definition
            FormActivityDef targetFormDef = createTargetActivityDef(handle);

            // create signal
            EventSignal signal = new ActivityInstanceStatusChangeSignal(
                    testData.getUserId(),
                    testData.getUserId(),
                    testData.getUserGuid(),
                    testData.getStudyGuid(),
                    instanceDto.getId(),
                    instanceDto.getActivityId(),
                    testData.getStudyId(),
                    testData.getStudyGuid(),
                    CREATED
            );

            // run the process of target activity instances creation
            ActivityInstanceCreationService creationService = new ActivityInstanceCreationService(signal);
            ActivityInstanceCreationEventSyncProcessor activityInstancesCreationProcessor =
                    new ActivityInstanceCreationFromAnswersEventSyncProcessor(handle, signal, targetFormDef.getActivityId(),
                            SOURCE_QUESTION__STABLE_ID, TARGET_QUESTION__STABLE_ID, creationService);
            activityInstancesCreationProcessor.processInstancesCreation();

            //-- verify created instances and copied answers data
            List<ActivityInstanceDto> newInstances = handle.attach(JdbiActivityInstance.class)
                    .findAllByUserGuidAndActivityCode(testData.getUserGuid(), targetFormDef.getActivityCode(), testData.getStudyId());
            assertEquals(2, newInstances.size());
            for (int i = 0; i < newInstances.size(); i++) {
                var inst = newInstances.get(i);
                assertEquals(targetFormDef.getActivityId(), (Long) inst.getActivityId());
                FormResponse formResponse = handle.attach(ActivityInstanceDao.class).findFormResponseWithAnswersByInstanceId(
                        inst.getId()).get();
                assertEquals(1, formResponse.getAnswers().size());
                Answer answer = formResponse.getAnswers().get(0);
                assertTrue(answer instanceof PicklistAnswer);
                var selected = ((PicklistAnswer) answer).getValue();
                assertEquals(1, selected.size());
                assertEquals(PICKLIST_OPTION__STABLE_ID[i], selected.get(0).getStableId());
            }

            handle.rollback();
        });
    }

    private FormActivityDef createSourceActivityDef(Handle handle) {
        var builder = PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, PICKLIST_SOURCE__STABLE_ID, Template.text("1"));
        for (int i = 0; i < PICKLIST_OPTION__STABLE_ID.length; i++) {
            builder.addOption(new PicklistOptionDef(PICKLIST_OPTION__STABLE_ID[i], Template.text(PICKLIST_OPTION__TPL_TEXT[i])));
        }
        var pickListQuestion = builder.build();

        var composite = CompositeQuestionDef.builder()
                .setStableId(SOURCE_QUESTION__STABLE_ID)
                .setPrompt(Template.text("source question"))
                .addChildrenQuestions(pickListQuestion)
                .setAllowMultiple(false)
                .setUnwrapOnExport(true)
                .build();

        var formDef = FormActivityDef.generalFormBuilder("ACT_SOURCE", "v1", testData.getStudyGuid())
                .addName(new Translation("en", "source activity"))
                .setClosing(new FormSectionDef(null, List.of(new QuestionBlockDef(composite))))
                .build();

        handle.attach(ActivityDao.class).insertActivity(formDef, RevisionMetadata.now(testData.getUserId(), "source activity"));
        return formDef;
    }

    private FormActivityDef createTargetActivityDef(Handle handle) {
        var builder = PicklistQuestionDef.buildMultiSelect(PicklistRenderMode.LIST, TARGET_QUESTION__STABLE_ID, Template.text("1"));
        for (int i = 0; i < PICKLIST_OPTION__STABLE_ID.length; i++) {
            builder.addOption(new PicklistOptionDef(PICKLIST_OPTION__STABLE_ID[i], Template.text(PICKLIST_OPTION__TPL_TEXT[i])));
        }
        var pickListQuestion = builder.build();

        var formDef = FormActivityDef.generalFormBuilder("ACT_TARGET", "v1", testData.getStudyGuid())
                .addName(new Translation("en", "target activity"))
                .setClosing(new FormSectionDef(null, List.of(new QuestionBlockDef(pickListQuestion))))
                .build();

        handle.attach(ActivityDao.class).insertActivity(formDef, RevisionMetadata.now(testData.getUserId(), "target activity"));
        return formDef;
    }
}
