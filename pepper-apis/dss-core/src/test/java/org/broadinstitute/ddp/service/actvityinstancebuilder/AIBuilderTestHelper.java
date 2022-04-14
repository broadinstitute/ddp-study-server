package org.broadinstitute.ddp.service.actvityinstancebuilder;

import static java.util.Collections.emptyList;
import static org.broadinstitute.ddp.content.RendererInitialContextCreator.RenderContextSource.FORM_RESPONSE_AND_ACTIVITY_DEF;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.Q_ID_1;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.Q_ID_2;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.Q_ID_3;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.Q_STABLE_ID_1;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.Q_STABLE_ID_2;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.Q_STABLE_ID_3;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.createActivityDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.createContentBlockDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.createFormResponse;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.createFormSectionDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.createQuestionBoolBlockDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderTestUtil.createQuestionTextBlockDef;
import static org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderParams.createParams;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.content.RendererInitialContextCreator.RenderContextSource;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;

public class AIBuilderTestHelper {

    public static final long CREATED_AT = System.currentTimeMillis() - 20000;
    public static final long UPDATED_AT = System.currentTimeMillis() - 10000;

    public static final String LANG_CODE = "en";

    public static final String INTRO_SECTION_CODE = "intro";

    public static final String PARTICIPANT_FIRST_NAME = "Lorenzo";
    public static final String PARTICIPANT_LAST_NAME = "Montana";

    public static final String USER_GUID = "user_guid_000";
    public static final String STUDY_GUID = "study_guid_111";
    public static final String INSTANCE_GUID = "instance_guid_222";

    public static final String FORM_NAME = "form name";
    public static final String FORM_TITLE = "form title";
    public static final String FORM_SUBTITLE = "form subtitle";
    public static final String FORM_READONLY_HINT = "readonly hint";

    public static final String SECTION_TITLE_BOLD = "This is <strong>bold</strong> section name";
    public static final String SECTION_TITLE = "This is bold section name";

    public static final String BLOCK_TITLE_TEXT = "block title text";
    public static final String BLOCK_BODY_TEXT = "block body text";
    public static final String QUESTION_TEXT = "Question text";
    public static final String QUESTION_TEXT_1 = "Question text 1";
    public static final String QUESTION_TEXT_3 = "This is bold text prompt 3";
    public static final String QUESTION_TEXT_BOLD_3 = "This is <strong>bold</strong> text prompt 3";
    public static final String BOOL_YES_TEXT = "yes";
    public static final String BOOL_NO_TEXT = "no";

    public static final String CONDITIONAL_PROMPT_TEXT = "Choose";
    public static final String CONDITIONAL_YES_TEXT = "Yes";
    public static final String CONDITIONAL_NO_TEXT = "No";
    public static final String CONDITIONAL_NESTED_1_TEXT = "Question 1";
    public static final String CONDITIONAL_NESTED_2_TEXT = "Text 2";

    public static final String GROUP_TITLE_TEXT = "Group text";
    public static final String GROUP_PROMPT_TEXT = "Choose";
    public static final String GROUP_YES_TEXT = "Left";
    public static final String GROUP_NO_TEXT = "Right";
    public static final String GROUP_NESTED_TEXT = "Nested text";


    public static AIBuilderContext buildActivityInstance(FormActivityDef formActivityDef) {
        return buildActivityInstance(formActivityDef, ContentStyle.BASIC, false);
    }

    public static AIBuilderContext buildActivityInstance(FormActivityDef formActivityDef, ContentStyle style,
                                                         boolean disableTemplatesRendering) {
        return AIBuilderFactory.createAIBuilder(
                AIBuilderFactory.createAIBuilderFactory().setTemplateRenderHelper(new TemplateRenderHelperTest()),
                null,
                createParams(USER_GUID, STUDY_GUID, INSTANCE_GUID)
                        .setIsoLangCode(LANG_CODE)
                        .setStyle(style)
                        .setReadPreviousInstanceId(false)
                        .setDisableTemplatesRendering(disableTemplatesRendering))
                .checkParams()
                .readFormInstanceData(createFormResponse(INSTANCE_GUID, CREATED_AT, UPDATED_AT))
                .readActivityDef(formActivityDef)
                .createRendererContext(FORM_RESPONSE_AND_ACTIVITY_DEF)
                .startBuild()
                .buildFormInstance()
                .buildFormChildren()
                .renderFormTitles()
                .renderContent()
                .setDisplayNumbers()
                .updateBlockStatuses()
                .populateSnapshottedAddress()
                .endBuild()
                .getContext();
    }

    public static FormSectionDef createBodySection(List<FormBlockDef> blocks) {
        return new FormSectionDef("s1",
                new Template(40L, TemplateType.TEXT, null, SECTION_TITLE_BOLD, 1), null, blocks);
    }

    public static FormActivityDef createFormActivityDef() {
        return createFormActivityDef(FORM_TITLE, FORM_SUBTITLE);
    }

    public static FormActivityDef createFormActivityDef(String title, String subtitle) {
        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                title, subtitle, FORM_READONLY_HINT,
                createFormSectionDef(INTRO_SECTION_CODE),
                createBodySection(Arrays.asList(
                        createContentBlockDef(30L, "s1b0", BLOCK_TITLE_TEXT,
                                31L, "s1b1", BLOCK_BODY_TEXT),
                        createQuestionTextBlockDef(Q_ID_1, Q_STABLE_ID_1, false, 1L, QUESTION_TEXT),
                        createQuestionBoolBlockDef(Q_ID_2, Q_STABLE_ID_2, false,
                                10L, QUESTION_TEXT_1, 11L, BOOL_YES_TEXT, 12L, BOOL_NO_TEXT, false),
                        createQuestionTextBlockDef(Q_ID_3, Q_STABLE_ID_3, false, 1L, QUESTION_TEXT_BOLD_3)))
        );
    }

    public static FormActivityDef createEmptyFormActivityDef() {
        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT, null, null);
    }

    public static FormActivityDef createFormActivityDefWithoutQuestions() {
        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT,
                createFormSectionDef(INTRO_SECTION_CODE),
                createBodySection(emptyList()));
    }

    static FormActivityDef createFormActivityDefWithOneDeprecatedQuestion() {
        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT,
                createFormSectionDef(INTRO_SECTION_CODE),
                createBodySection(Arrays.asList(
                    createContentBlockDef(30L, "s1b0", BLOCK_TITLE_TEXT,
                            31L, "s1b1", BLOCK_BODY_TEXT),
                    createQuestionTextBlockDef(Q_ID_1, Q_STABLE_ID_1, false, 1L, QUESTION_TEXT),
                    createQuestionBoolBlockDef(Q_ID_2, Q_STABLE_ID_2, true,
                            10L, QUESTION_TEXT_1, 11L, BOOL_YES_TEXT, 12L, BOOL_NO_TEXT, false),
                    createQuestionTextBlockDef(Q_ID_3, Q_STABLE_ID_3, false, 1L, QUESTION_TEXT_BOLD_3))));
    }

    public static FormActivityDef createFormActivityDefWithRequiredAnswer() {
        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT,
                createFormSectionDef(INTRO_SECTION_CODE),
                createBodySection(Arrays.asList(
                        createContentBlockDef(30L, "s1b0", BLOCK_TITLE_TEXT,
                                31L, "s1b1", BLOCK_BODY_TEXT),
                        createQuestionBoolBlockDef(Q_ID_2, Q_STABLE_ID_2, false,
                                10L, QUESTION_TEXT_1, 11L, BOOL_YES_TEXT, 12L, BOOL_NO_TEXT, true))));
    }

    public static FormActivityDef createFormActivityDefWithNestedBlocks(boolean controlDeprecated) {
        FormSectionDef intro = createFormSectionDef(INTRO_SECTION_CODE);

        FormSectionDef bodySection = AIBuilderTestUtil.createSectionWithConditionalBlockDef("st1", controlDeprecated, 1L,
                CONDITIONAL_PROMPT_TEXT, 2L, CONDITIONAL_YES_TEXT, 3L, CONDITIONAL_NO_TEXT,
                "stNested", 4L, CONDITIONAL_NESTED_1_TEXT, 5L, CONDITIONAL_NESTED_2_TEXT);

        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT, intro, bodySection);
    }

    static FormActivityDef createFormActivityDefWithGroupBlock(boolean nestedQuestionDeprecated) {
        FormSectionDef intro = createFormSectionDef(INTRO_SECTION_CODE);

        FormSectionDef bodySection = AIBuilderTestUtil.createSectionWithGroupBlockDef("st1", nestedQuestionDeprecated, 1L,
                GROUP_TITLE_TEXT, 2L, GROUP_PROMPT_TEXT, 3L, GROUP_YES_TEXT, 4L, GROUP_NO_TEXT,
                5L, GROUP_NESTED_TEXT);

        return createActivityDef(LANG_CODE, STUDY_GUID, FORM_NAME,
                FORM_TITLE, FORM_SUBTITLE, FORM_READONLY_HINT, intro, bodySection);
    }

    static class TemplateRenderHelperTest extends TemplateRenderHelper {

        @Override
        public void createRendererInitialContext(AIBuilderContext ctx, RenderContextSource renderContextSource) {
            Map<String, Object> context = new HashMap<>();
            context.put(I18nTemplateConstants.DDP,
                    new RenderValueProvider.Builder()
                            .withFormResponse(ctx.getFormResponse(), ctx.getFormActivityDef(), ctx.getIsoLangCode())
                            .setParticipantFirstName(PARTICIPANT_FIRST_NAME)
                            .setParticipantLastName(PARTICIPANT_LAST_NAME)
                            .build());
            putLastUpdatedToRenderContext(ctx, context);
            ctx.getRendererInitialContext().putAll(context);
        }
    }
}
