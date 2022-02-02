package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.CONDITIONAL_PROMPT_TEXT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.FORM_READONLY_HINT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.FORM_SUBTITLE;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.FORM_TITLE;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.GROUP_PROMPT_TEXT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.GROUP_TITLE_TEXT;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.LANG_CODE;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.buildActivityInstance;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.createFormActivityDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.createFormActivityDefWithGroupBlock;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.createFormActivityDefWithNestedBlocks;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestHelper.createFormActivityDefWithOneDeprecatedQuestion;
import static org.junit.Assert.assertEquals;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.model.activity.instance.ConditionalBlock;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.model.activity.instance.QuestionBlock;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.junit.BeforeClass;
import org.junit.Test;

public class AIBuilderTest {

    @BeforeClass
    public static void setupTest() {
        LanguageStore.set(new LanguageDto(1L, LANG_CODE));
    }

    @Test
    public void testAIBuilderNormalFlow() {
        var ctx = buildActivityInstance(createFormActivityDef());

        assertEquals(14, ctx.getPassedBuildSteps().size());

        assertEquals(4, ctx.getFormInstance().getBodySections().get(0).getBlocks().size());

        assertEquals(FORM_TITLE, ctx.getFormInstance().getTitle());
        assertEquals(FORM_SUBTITLE, ctx.getFormInstance().getSubtitle());
        assertEquals(FORM_READONLY_HINT, ctx.getFormInstance().getReadonlyHint());

        assertEquals(BlockType.CONTENT, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0).getBlockType());
        assertEquals(BlockType.QUESTION, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(1).getBlockType());
        assertEquals(BlockType.QUESTION, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(2).getBlockType());
    }

    @Test
    public void testAIBuilderWithConditionalBlock() {
        var ctx = buildActivityInstance(createFormActivityDefWithNestedBlocks(false));

        assertEquals(1, ctx.getFormInstance().getBodySections().get(0).getBlocks().size());
        assertEquals(BlockType.CONDITIONAL, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0).getBlockType());
        ConditionalBlock conditionalBlock = (ConditionalBlock)ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0);
        assertEquals(2, conditionalBlock.getNested().size());
        assertEquals(QuestionType.BOOLEAN, conditionalBlock.getControl().getQuestionType());
        assertEquals(CONDITIONAL_PROMPT_TEXT, conditionalBlock.getControl().getPrompt());
        assertEquals(BlockType.QUESTION, conditionalBlock.getNested().get(0).getBlockType());
        assertEquals(BlockType.CONTENT, conditionalBlock.getNested().get(1).getBlockType());
    }

    @Test
    public void testAIBuilderWithGroupBlock() {
        var ctx = buildActivityInstance(createFormActivityDefWithGroupBlock(false));

        assertEquals(1, ctx.getFormInstance().getBodySections().get(0).getBlocks().size());
        assertEquals(BlockType.GROUP, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0).getBlockType());
        GroupBlock groupBlock = (GroupBlock)ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0);
        assertEquals(GROUP_TITLE_TEXT, groupBlock.getTitle());
        assertEquals(2, groupBlock.getNested().size());
        assertEquals(BlockType.QUESTION, groupBlock.getNested().get(0).getBlockType());
        assertEquals(BlockType.CONTENT, groupBlock.getNested().get(1).getBlockType());
        assertEquals(GROUP_PROMPT_TEXT, ((QuestionBlock)groupBlock.getNested().get(0)).getQuestion().getPrompt());
    }

    @Test
    public void testAIBuilderExcludedDeprecatedQuestion() {
        var ctx = buildActivityInstance(createFormActivityDefWithOneDeprecatedQuestion());

        assertEquals(3, ctx.getFormInstance().getBodySections().get(0).getBlocks().size());
    }

    @Test
    public void testAIBuilderExcludedDeprecatedControlQuestion() {
        var ctx = buildActivityInstance(createFormActivityDefWithNestedBlocks(true));

        assertEquals(0, ctx.getFormInstance().getBodySections().get(0).getBlocks().size());
    }

    @Test
    public void testAIBuilderExcludedDeprecatedNestedQuestion() {
        var ctx = buildActivityInstance(createFormActivityDefWithGroupBlock(true));

        assertEquals(1, ctx.getFormInstance().getBodySections().get(0).getBlocks().size());
        assertEquals(BlockType.GROUP, ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0).getBlockType());
        GroupBlock groupBlock = (GroupBlock)ctx.getFormInstance().getBodySections().get(0).getBlocks().get(0);
        assertEquals(GROUP_TITLE_TEXT, groupBlock.getTitle());
        assertEquals(1, groupBlock.getNested().size());
        assertEquals(BlockType.CONTENT, groupBlock.getNested().get(0).getBlockType());
    }
}
