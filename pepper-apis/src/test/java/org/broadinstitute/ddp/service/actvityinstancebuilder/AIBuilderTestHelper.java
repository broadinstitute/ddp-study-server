package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.FormInstanceTestUtil.createActivityDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.FormInstanceTestUtil.createContentBlockDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.FormInstanceTestUtil.createFormResponse;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.FormInstanceTestUtil.createFormSectionDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.FormInstanceTestUtil.createQuestionBoolBlockDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.FormInstanceTestUtil.createQuestionTextBlockDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderParams.createParams;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.service.actvityinstancebuilder.factory.AIContentRendererFactory;

public class AIBuilderTestHelper {

    static final long CREATED_AT = System.currentTimeMillis() - 20000;
    static final long UPDATED_AT = System.currentTimeMillis() - 10000;

    static final String LANG_CODE = "en";

    static final String INTRO_SECTION_CODE = "intro";

    static final String USER_GUID = "user_guid_000";
    static final String STUDY_GUID = "study_guid_111";
    static final String INSTANCE_GUID = "instance_guid_222";

    static final String FORM_NAME = "form name";
    static final String FORM_TITLE = "form title";
    static final String FORM_SUBTITLE = "form subtitle";
    static final String FORM_READONLY_HINT = "readonly hint";

    static final String BODY_BLOCK_TEXT = "block text";
    static final String QUESTION_TEXT = "Question text";
    static final String QUESTION_TEXT_1 = "Question text 1";
    static final String BOOL_YES_TEXT = "yes";
    static final String BOOL_NO_TEXT = "no";

    static final String CONDITIONAL_PROMPT_TEXT = "Choose";
    static final String CONDITIONAL_YES_TEXT = "Yes";
    static final String CONDITIONAL_NO_TEXT = "No";
    static final String CONDITIONAL_NESTED_1_TEXT = "Question 1";
    static final String CONDITIONAL_NESTED_2_TEXT = "Text 2";

    static final String GROUP_TITLE_TEXT = "Group text";
    static final String GROUP_PROMPT_TEXT = "Choose";
    static final String GROUP_YES_TEXT = "Left";
    static final String GROUP_NO_TEXT = "Right";
    static final String GROUP_NESTED_TEXT = "Nested text";

    static AIBuilderContext buildActivityInstance(FormActivityDef formActivityDef) {
        return AIBuilderFactory.createAIBuilder(
                AIBuilderFactory.createAIBuilderFactory().setAIContentRendererFactory(new AIContentRendererFactoryTest()),
                null,
                createParams(USER_GUID, STUDY_GUID, INSTANCE_GUID)
                        .setIsoLangCode(LANG_CODE)
                        .setStyle(ContentStyle.BASIC)
                        .setReadPreviousInstanceId(false))
                .checkParams()
                .readFormInstanceData(createFormResponse(INSTANCE_GUID, CREATED_AT, UPDATED_AT))
                .readActivityDef(formActivityDef)
                .startBuild()
                .buildFormInstance()
                .buildFormChildren()
                .renderFormTitles()
                .renderContent()
                .setDisplayNumbers()
                .updateBlockStatuses()
                .endBuild()
                .getContext();
    }

    static FormActivityDef createFormActivityDef() {
        FormSectionDef intro = createFormSectionDef(INTRO_SECTION_CODE);

        FormSectionDef bodySection = new FormSectionDef("s1", Arrays.asList(
                createContentBlockDef(2L, "s1b1", BODY_BLOCK_TEXT),
                createQuestionTextBlockDef(1L, "s1b2", false, 1L, QUESTION_TEXT),
                createQuestionBoolBlockDef(1L, "s1b2", false,
                        10L, QUESTION_TEXT_1, 11L, BOOL_YES_TEXT, 12L, BOOL_NO_TEXT)));

        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT, intro, bodySection);
    }

    static FormActivityDef createFormActivityDefWithOneDeprecatedQuestion() {
        FormSectionDef intro = createFormSectionDef(INTRO_SECTION_CODE);

        FormSectionDef bodySection = new FormSectionDef("s1", Arrays.asList(
                createContentBlockDef(2L, "s1b1", BODY_BLOCK_TEXT),
                createQuestionTextBlockDef(1L, "s1b2", false, 1L, QUESTION_TEXT),
                createQuestionBoolBlockDef(1L, "s1b2", true,
                        10L, QUESTION_TEXT_1, 11L, BOOL_YES_TEXT, 12L, BOOL_NO_TEXT)));

        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT, intro, bodySection);
    }

    static FormActivityDef createFormActivityDefWithNestedBlocks(boolean controlDeprecated) {
        FormSectionDef intro = createFormSectionDef(INTRO_SECTION_CODE);

        FormSectionDef bodySection = FormInstanceTestUtil.createSectionWithConditionalBlockDef("st1", controlDeprecated, 1L,
                CONDITIONAL_PROMPT_TEXT, 2L, CONDITIONAL_YES_TEXT, 3L, CONDITIONAL_NO_TEXT,
                "stNested", 4L, CONDITIONAL_NESTED_1_TEXT, 5L, CONDITIONAL_NESTED_2_TEXT);

        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT, intro, bodySection);
    }

    static FormActivityDef createFormActivityDefWithGroupBlock(boolean nestedQuestionDeprecated) {
        FormSectionDef intro = createFormSectionDef(INTRO_SECTION_CODE);

        FormSectionDef bodySection = FormInstanceTestUtil.createSectionWithGroupBlockDef("st1", nestedQuestionDeprecated, 1L,
                GROUP_TITLE_TEXT, 2L, GROUP_PROMPT_TEXT, 3L, GROUP_YES_TEXT, 4L, GROUP_NO_TEXT,
                5L, GROUP_NESTED_TEXT);

        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT, intro, bodySection);
    }

    static class AIContentRendererFactoryTest extends AIContentRendererFactory {

        @Override
        public void createRendererInitialContext(AIBuilderContext ctx) {
            Map<String, Object> context = new HashMap<>();
            context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder().build());
            putLastUpdatedToRenderContext(ctx, context);
            ctx.getRendererInitialContext().putAll(context);
        }
    }
}
