package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.BLOCK_BODY_TEXT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.BLOCK_TITLE_TEXT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.FORM_READONLY_HINT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.FORM_SUBTITLE;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.FORM_TITLE;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.LANG_CODE;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.PARTICIPANT_FIRST_NAME;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.PARTICIPANT_LAST_NAME;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.QUESTION_TEXT_3;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.QUESTION_TEXT_BOLD_3;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.SECTION_TITLE;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.SECTION_TITLE_BOLD;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.buildActivityInstance;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.createEmptyFormActivityDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.createFormActivityDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.createFormActivityDefWithNestedBlocks;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.createFormActivityDefWithoutQuestions;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.ANSWER_1_TEXT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.ANSWER_3_TEXT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.Q_STABLE_ID_1;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.Q_STABLE_ID_3;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.createEmptyTestInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.model.activity.instance.ConditionalBlock;
import org.broadinstitute.ddp.model.activity.instance.ContentBlock;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.model.activity.instance.Numberable;
import org.broadinstitute.ddp.model.activity.instance.QuestionBlock;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.BooleanRenderMode;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormInstanceCreatorHelper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FormInstanceTest {

    private FormInstanceCreatorHelper formInstanceCreatorHelper = new FormInstanceCreatorHelper();

    @BeforeClass
    public static void setupTest() {
        LanguageStore.set(new LanguageDto(1L, LANG_CODE));
    }

    @Test
    public void testRenderContent_contentBlock_standardStyle() {
        var ctx = buildActivityInstance(createFormActivityDef(), ContentStyle.STANDARD, false);

        assertEquals(FORM_TITLE, ctx.getFormInstance().getTitle());
        assertEquals(FORM_SUBTITLE, ctx.getFormInstance().getSubtitle());
        assertEquals(FORM_READONLY_HINT, ctx.getFormInstance().getReadonlyHint());
    }

    @Test
    public void testRenderContent_contentBlock_basicStyle() {
        var ctx = buildActivityInstance(createFormActivityDef());

        assertEquals(FORM_SUBTITLE, ctx.getFormInstance().getSubtitle());
        assertEquals(FORM_READONLY_HINT, ctx.getFormInstance().getReadonlyHint());
        assertEquals(BlockType.CONTENT, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0).getBlockType());
        ContentBlock contentBlock = (ContentBlock)ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0);
        assertEquals(BLOCK_TITLE_TEXT, contentBlock.getTitle());
        assertEquals(BLOCK_BODY_TEXT, contentBlock.getBody());
    }

    @Test
    public void testRenderContent_questionBlock_standardStyle() {
        var ctx = buildActivityInstance(createFormActivityDef(), ContentStyle.STANDARD, false);

        assertEquals(FORM_SUBTITLE, ctx.getFormInstance().getSubtitle());
        assertEquals(BlockType.QUESTION, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(1).getBlockType());
        QuestionBlock questionBlock = (QuestionBlock)ctx.getFormInstance().getBodySections().get(0).getBlocks().get(3);
        assertEquals(QUESTION_TEXT_BOLD_3, questionBlock.getQuestion().getPrompt());
        assertEquals(QUESTION_TEXT_3, questionBlock.getQuestion().getTextPrompt());
    }


    @Test
    public void testRenderContent_questionBlock_basicStyle() {
        var ctx = buildActivityInstance(createFormActivityDef());

        assertEquals(BlockType.QUESTION, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(1).getBlockType());
        QuestionBlock questionBlock = (QuestionBlock)ctx.getFormInstance().getBodySections().get(0).getBlocks().get(3);
        assertEquals(QUESTION_TEXT_3, questionBlock.getQuestion().getTextPrompt());
    }

    @Test
    public void testRenderContent_sectionName_standardStyle() {
        var ctx = buildActivityInstance(createFormActivityDef(), ContentStyle.STANDARD, false);

        assertEquals(SECTION_TITLE_BOLD, ctx.getFormInstance().getBodySections().get(0).getName());
    }

    @Test
    public void testRenderContent_sectionName_basicStyle() {
        var ctx = buildActivityInstance(createFormActivityDef(), ContentStyle.BASIC, false);

        assertEquals(SECTION_TITLE, ctx.getFormInstance().getBodySections().get(0).getName());
    }

    @Test
    public void testRenderContent_specialVarsContext() {
        var ctx = buildActivityInstance(createFormActivityDef());

        assertNotNull(ctx.getRendererInitialContext().get(I18nTemplateConstants.DDP));
        RenderValueProvider renderValueProvider = (RenderValueProvider)ctx.getRendererInitialContext().get(I18nTemplateConstants.DDP);
        assertEquals(PARTICIPANT_FIRST_NAME, renderValueProvider.participantFirstName());
        assertEquals(PARTICIPANT_LAST_NAME, renderValueProvider.participantLastName());
    }

    @Test
    public void testRenderContent_answerSubstitutions() {
        String title = "$ddp.answer(\"" + Q_STABLE_ID_1 + "\",\"fallback\")";
        String subtitle = "$ddp.answer(\"" + Q_STABLE_ID_3 + "\",\"fallback\")";

        var ctx = buildActivityInstance(createFormActivityDef(title, subtitle));

        assertEquals(ANSWER_1_TEXT, ctx.getFormInstance().getTitle());
        assertEquals(ANSWER_3_TEXT, ctx.getFormInstance().getSubtitle());
    }

    @Test
    public void testIsComplete_emptyForm() {
        var ctx = buildActivityInstance(createEmptyFormActivityDef());

        assertTrue(ctx.getFormInstance().isComplete());
    }

    @Test
    public void testIsComplete_noQuestions() {
        var ctx = buildActivityInstance(createFormActivityDefWithoutQuestions());

        assertTrue(ctx.getFormInstance().isComplete());
    }


    @Test
    public void testIsComplete_requiredQuestionWithNoAnswer() {
        RequiredRule<BoolAnswer> req = new RequiredRule<>(1L, null, "required", false);
        BoolQuestion question = new BoolQuestion("SID", 2, Collections.emptyList(),
                    Collections.singletonList(req), 2, 3, BooleanRenderMode.RADIO_BUTTONS);
        QuestionBlock block = new QuestionBlock(question);
        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = createEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        assertFalse(form.isComplete());
    }

    @Test
    public void testNumbering() {
        FormInstance form = createEmptyTestInstance();
        Map<String, Integer> expectedNumberForStableId = new HashMap<>();

        int expectedNumber = 2; // starts at 2 since the intro has a question

        for (int formNumber = 0; formNumber < 3; formNumber++) {
            List<FormBlock> blocks = new ArrayList<>();
            for (int blockNumber = 0; blockNumber < 2; blockNumber++) {
                String stableId = "SID" + formNumber + "_" + blockNumber;
                blocks.add(new QuestionBlock(new BoolQuestion(stableId, 2, Collections.emptyList(), Collections
                        .emptyList(), 2, 3, BooleanRenderMode.RADIO_BUTTONS)));
                expectedNumberForStableId.put(stableId, expectedNumber++);
            }
            form.addBodySections(Collections.singletonList(new FormSection(blocks)));
        }

        List<FormBlock> introBlocks = new ArrayList<>();
        String introQuestionStableId = "intro";
        introBlocks.add(new QuestionBlock(new BoolQuestion(introQuestionStableId,
                2,
                Collections.emptyList(),
                Collections.emptyList(),
                2,
                3,
                BooleanRenderMode.RADIO_BUTTONS)));
        FormSection introSection = new FormSection(introBlocks);
        expectedNumberForStableId.put(introQuestionStableId, 1);

        List<FormBlock> closingBlocks = new ArrayList<>();
        String closingQuestionStableId = "closing";
        introBlocks.add(new QuestionBlock(new BoolQuestion(introQuestionStableId,
                2,
                Collections.emptyList(),
                Collections.emptyList(),
                2,
                3,
                BooleanRenderMode.RADIO_BUTTONS)));
        FormSection closingSection = new FormSection(closingBlocks);
        expectedNumberForStableId.put(closingQuestionStableId, expectedNumberForStableId.size());

        form.setIntroduction(introSection);
        formInstanceCreatorHelper.setDisplayNumbers(form);
        form.setClosing(closingSection);

        for (FormSection formSection : form.getBodySections()) {
            for (FormBlock formBlock : formSection.getBlocks()) {
                int actualDisplayNumber = ((Numberable) formBlock).getDisplayNumber();
                int expectedDisplayNumber = expectedNumberForStableId.get(((QuestionBlock) formBlock).getQuestion()
                        .getStableId());
                Assert.assertEquals(expectedDisplayNumber, actualDisplayNumber);
            }
        }
    }

    @Test
    public void testIsComplete_requiredQuestionWithAnswer() {
        RequiredRule<BoolAnswer> req = new RequiredRule<>(1L, null, "required", false);
        BoolAnswer answer = new BoolAnswer(2L, "SID", "ABC", true);
        BoolQuestion question = new BoolQuestion("SID", 3, Collections.singletonList(answer),
                Collections.singletonList(req), 2, 3, BooleanRenderMode.RADIO_BUTTONS);
        QuestionBlock block = new QuestionBlock(question);
        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = createEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        assertTrue(form.isComplete());
    }

    @Test
    public void testIsComplete_conditionallyShownQuestion() {
        BoolQuestion control = new BoolQuestion("SID", 1, Collections.emptyList(),
                Collections.emptyList(), 2, 3, BooleanRenderMode.RADIO_BUTTONS);
        RequiredRule<BoolAnswer> req = new RequiredRule<>(1L, null, "required", false);
        BoolQuestion nested = new BoolQuestion("SID", 2, Collections.emptyList(),
                Collections.singletonList(req), 4, 5, BooleanRenderMode.RADIO_BUTTONS);
        ConditionalBlock block = new ConditionalBlock(control);
        block.getNested().add(new QuestionBlock(nested));

        FormSection s1 = new FormSection(Collections.singletonList(block));
        FormInstance form = createEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        block.setShown(false);
        assertTrue(form.isComplete());

        block.setShown(true);
        assertFalse(form.isComplete());

        block.getNested().get(0).setShown(false);
        assertTrue(form.isComplete());
    }

    @Test
    public void testIsComplete_groupedQuestionsAreChecked() {
        RequiredRule<BoolAnswer> req = new RequiredRule<>(1L, null, "required", false);
        BoolQuestion nested = new BoolQuestion("SID", 1, Collections.emptyList(),
                Collections.singletonList(req), 2, 3, BooleanRenderMode.RADIO_BUTTONS);
        GroupBlock group = new GroupBlock(null, null);
        group.getNested().add(new QuestionBlock(nested));

        FormSection s1 = new FormSection(Collections.singletonList(group));
        FormInstance form = createEmptyTestInstance();
        form.addBodySections(Collections.singletonList(s1));

        group.setShown(false);
        assertTrue(form.isComplete());

        group.setShown(true);
        assertFalse(form.isComplete());
    }

    @Test
    public void testUpdateBlockStatuses_childInConditionalBlockIsUpdated() {
        var ctx = buildActivityInstance(createFormActivityDefWithNestedBlocks(false));

        assertEquals(BlockType.CONDITIONAL, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0).getBlockType());
        ConditionalBlock block = (ConditionalBlock)ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0);
        assertTrue(block.getNested().get(0).isShown());
    }

    @Test
    public void testCollectHiddenAnswers() {
        var q1 = new QuestionBlock(new BoolQuestion("b1", 1L,
                List.of(new BoolAnswer(1L, "b1", "1", true)), List.of(), 2L, 3L, BooleanRenderMode.RADIO_BUTTONS));
        q1.setShown(true);
        var q2 = new QuestionBlock(new BoolQuestion("b2", 1L,
                List.of(new BoolAnswer(2L, "b2", "2", false)), List.of(), 2L, 3L, BooleanRenderMode.RADIO_BUTTONS));
        q2.setShown(false);
        var cond1 = new ConditionalBlock(new TextQuestion("t1", 1L, null,
                List.of(new TextAnswer(3L, "t1", "3", "cond1")), List.of(), TextInputType.TEXT));
        var nest1 = new QuestionBlock(new TextQuestion("t2", 1L, null,
                List.of(new TextAnswer(4L, "t2", "4", "nest1")), List.of(), TextInputType.TEXT));
        cond1.getNested().add(nest1);
        cond1.setShown(false);
        nest1.setEnabled(false);

        var section = new FormSection(List.of(q1, q2, cond1));
        var form = createEmptyTestInstance();
        form.addBodySections(List.of(section));

        var hidden = form.collectHiddenAndDisabledAnswers();
        assertNotNull(hidden);
        assertEquals(3, hidden.size());

        var answerIds = hidden.stream().map(Answer::getAnswerId).collect(Collectors.toSet());
        assertTrue(answerIds.containsAll(Set.of(2L, 3L, 4L)));
    }

    @Test
    public void testCollectHiddenAnswers_permanentlyHiddenBlock() {
        var q1 = new QuestionBlock(new BoolQuestion("b1", 1L,
                List.of(new BoolAnswer(1L, "b1", "1", true)), List.of(), 2L, 3L, BooleanRenderMode.RADIO_BUTTONS));
        q1.setShown(false);
        q1.setShownExpr("false");
        var section = new FormSection(List.of(q1));
        var form = createEmptyTestInstance();
        form.addBodySections(List.of(section));

        var hidden = form.collectHiddenAndDisabledAnswers();
        assertNotNull(hidden);
        assertTrue(hidden.isEmpty());
    }
}

