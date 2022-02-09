package org.broadinstitute.ddp.service.actvityinstancebuilder.form;


import static org.broadinstitute.ddp.util.TemplateRenderUtil.toPlainText;
import static org.broadinstitute.ddp.util.TranslationUtil.extractOptionalActivityTranslation;

import java.util.Map;
import java.util.TreeMap;

import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;

/**
 * Creates {@link FormInstance}
 */
public class FormInstanceCreator {

    public FormInstance createFormInstance(AIBuilderContext ctx) {
        var formActivityDef = ctx.getFormActivityDef();
        var formResponse = ctx.getFormResponse();

        boolean readonly = ActivityInstanceUtil.isReadonly(
                formActivityDef.getEditTimeoutSec(),
                formResponse.getCreatedAt(),
                formResponse.getLatestStatus().getType().name(),
                formActivityDef.isWriteOnce(),
                formResponse.getReadonly());

        boolean isFirstInstance = ctx.getPreviousInstanceId() == null;
        boolean canDelete = ActivityInstanceUtil.computeCanDelete(
                formActivityDef.canDeleteInstances(),
                formActivityDef.getCanDeleteFirstInstance(),
                isFirstInstance);

        var formInstance = new FormInstance(
                formResponse.getParticipantId(),
                formResponse.getId(),
                formResponse.getActivityId(),
                formResponse.getActivityCode(),
                formActivityDef.getFormType(),
                formResponse.getGuid(),
                null,       // 'title' is rendered and assigned after FormInstance creation completed
                null,     // 'subTitle' is rendered and assigned after FormInstance creation completed
                formResponse.getLatestStatus() != null ? formResponse.getLatestStatus().getType().name() : null,
                readonly,
                formActivityDef.getListStyleHint(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, formActivityDef.getReadonlyHintTemplate()),
                formActivityDef.getIntroduction() != null ? formActivityDef.getIntroduction().getSectionId() : null,
                formActivityDef.getClosing() != null ? formActivityDef.getClosing().getSectionId() : null,
                formResponse.getCreatedAt(),
                formResponse.getFirstCompletedAt(),
                ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                        ctx, formActivityDef.getLastUpdatedTextTemplate()),
                formActivityDef.getLastUpdated(),
                canDelete,
                formActivityDef.isFollowup(),
                formResponse.getHidden(),
                formActivityDef.isExcludeFromDisplay(),
                formResponse.getSectionIndex()
        );

        return formInstance;
    }

    public void addChildren(AIBuilderContext ctx) {
        var formActivityDef = ctx.getFormActivityDef();
        var formSectionCreator = ctx.getAIBuilderFactory().getFormSectionCreator();
        ctx.getFormInstance().setIntroduction(formSectionCreator.createSection(ctx, formActivityDef.getIntroduction()));
        ctx.getFormInstance().setClosing(formSectionCreator.createSection(ctx, formActivityDef.getClosing()));
        if (formActivityDef.getSections() != null) {
            formActivityDef.getSections().forEach(s -> {
                ctx.getFormInstance().getBodySections().add(formSectionCreator.createSection(ctx, s));
            });
        }
    }

    public void renderTitleAndSubtitle(AIBuilderContext ctx) {
        var title = extractOptionalActivityTranslation(ctx.getFormActivityDef().getTranslatedTitles(), ctx.getIsoLangCode());
        var subtitle = extractOptionalActivityTranslation(ctx.getFormActivityDef().getTranslatedSubtitles(), ctx.getIsoLangCode());
        ctx.getFormInstance().setTitle(ctx.getAIBuilderFactory().getTemplateRenderHelper().renderTemplate(ctx, title));
        ctx.getFormInstance().setSubtitle(ctx.getAIBuilderFactory().getTemplateRenderHelper().renderTemplate(ctx, subtitle));
    }

    public void updateBlockStatuses(AIBuilderContext ctx) {
        ctx.getAIBuilderFactory().getFormInstanceCreatorHelper().updateBlockStatuses(
                ctx.getHandle(),
                ctx.getFormInstance(),
                ctx.getInterpreter(),
                ctx.getUserGuid(),
                ctx.getOperatorGuid(),
                ctx.getFormResponse().getGuid(),
                ctx.getParams().getInstanceSummary());
    }

    /**
     * Render templates
     */
    public void renderContent(AIBuilderContext ctx) {
        Map<Long, String> renderedTemplates = new TreeMap<>();
        renderTemplates(ctx, renderedTemplates);
        applyRenderedTemplates(ctx, renderedTemplates);
    }

    private void renderTemplates(AIBuilderContext ctx, Map<Long, String> renderedTemplates) {
        ctx.getTemplates().values().forEach(t ->
                renderedTemplates.put(t.getTemplateId(),
                        t.render(ctx.getIsoLangCode(), ctx.getRendererInitialContext()))
        );
    }

    private void applyRenderedTemplates(AIBuilderContext ctx, Map<Long, String> renderedTemplates) {
        ctx.getFormInstance().getAllSections().forEach(s ->
                s.applyRenderedTemplates(renderedTemplates::get, ctx.getStyle()));
        ctx.getFormInstance().setReadonlyHint(
                toPlainText(ctx.getFormInstance().getReadonlyHintTemplateId(), renderedTemplates::get, ctx.getStyle()));
        ctx.getFormInstance().setActivityDefinitionLastUpdatedText(
                toPlainText(ctx.getFormInstance().getLastUpdatedTextTemplateId(), renderedTemplates::get, ctx.getStyle()));
    }
}
