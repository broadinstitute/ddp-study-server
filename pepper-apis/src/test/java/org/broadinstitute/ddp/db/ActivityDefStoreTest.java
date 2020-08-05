package org.broadinstitute.ddp.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class ActivityDefStoreTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
        });
    }

    @Test
    public void testCountQuestionsAndAnswers() {
        TransactionWrapper.useTxn(handle -> {
            var form = createDummyForm();
            handle.attach(ActivityDao.class)
                    .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));

            var instanceDto = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(form.getActivityId(), testData.getUserGuid());

            var answerDao = handle.attach(AnswerDao.class);
            answerDao.createAnswer(testData.getUserId(), instanceDto.getId(),
                    new AgreementAnswer(null, "AGREE1", null, true));
            answerDao.createAnswer(testData.getUserId(), instanceDto.getId(),
                    new BoolAnswer(null, "BOOL1", null, true));
            answerDao.createAnswer(testData.getUserId(), instanceDto.getId(),
                    new TextAnswer(null, "TEXT1", null, ""));   // Answered but empty.

            // Not answering numeric question.

            // Composite is unwrapped. Picklist child answered, date answer empty.
            var composite = new CompositeAnswer(null, "COMP1", null);
            composite.addRowOfChildAnswers(
                    new PicklistAnswer(null, "PICK1", null, List.of(new SelectedPicklistOption("PO1"))),
                    new DateAnswer(null, "DATE1", null, null, null, null));
            answerDao.createAnswer(testData.getUserId(), instanceDto.getId(), composite);

            Pair<Integer, Integer> counts = ActivityDefStore.getInstance().countQuestionsAndAnswers(
                    handle, testData.getUserGuid(), form, instanceDto.getGuid());
            assertNotNull(counts);
            assertEquals("should count all regular/control/nested/unwrapped questions",
                    (Integer) 6, counts.getLeft());
            assertEquals("should count all non-empty and unwrapped answers",
                    (Integer) 3, counts.getRight());

            handle.rollback();
        });
    }

    private FormActivityDef createDummyForm() {
        var agreement = new AgreementQuestionDef("AGREE1", false, Template.text("1"), null, null, null,
                List.of(new RequiredRuleDef(Template.text("hint"))), false, false);

        var conditional = new ConditionalBlockDef(BoolQuestionDef
                .builder("BOOL1", Template.text("2"), Template.text("yes"), Template.text("no"))
                .build());
        conditional.addNestedBlock(new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, "TEXT1", Template.text("3"))
                .build()));

        var group = new GroupBlockDef();
        group.addNestedBlock(new QuestionBlockDef(NumericQuestionDef
                .builder(NumericType.INTEGER, "NUM1", Template.text("4"))
                .build()));

        var child1 = PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, "PICK1", Template.text("5"))
                .addOption(new PicklistOptionDef("PO1", Template.text("po1")))
                .build();
        var child2 = DateQuestionDef
                .builder(DateRenderMode.TEXT, "DATE1", Template.text("6"))
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .build();
        var composite = CompositeQuestionDef.builder()
                .setStableId("COMP1")
                .setPrompt(Template.text("not-counted"))
                .addChildrenQuestions(child1, child2)
                .setAllowMultiple(false)
                .setUnwrapOnExport(true)
                .build();

        return FormActivityDef.generalFormBuilder("ACT", "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity"))
                .setIntroduction(new FormSectionDef(null, List.of(new QuestionBlockDef(agreement))))
                .addSection(new FormSectionDef(null, List.of(conditional)))
                .addSection(new FormSectionDef(null, List.of(group)))
                .setClosing(new FormSectionDef(null, List.of(new QuestionBlockDef(composite))))
                .build();
    }
}
