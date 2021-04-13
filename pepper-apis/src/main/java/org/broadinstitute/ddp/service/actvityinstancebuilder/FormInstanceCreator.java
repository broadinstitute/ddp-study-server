package org.broadinstitute.ddp.service.actvityinstancebuilder;


import static org.broadinstitute.ddp.service.actvityinstancebuilder.util.TemplateHandler.addAndRenderTemplate;
import static org.broadinstitute.ddp.util.TemplateRenderUtil.toPlainText;
import static org.broadinstitute.ddp.util.TranslationUtil.extractOptionalActivityTranslation;

import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.service.actvityinstancebuilder.util.RendererInitialContextHandler;
import org.broadinstitute.ddp.service.actvityinstancebuilder.util.TemplateHandler;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;

/**
 * Creates {@link FormInstance}
 */
public class FormInstanceCreator {

    public FormInstance createFormInstance(AIBuilderContext ctx) {
        RendererInitialContextHandler.createRendererInitialContext(ctx);
        return constructFormInstance(ctx);
    }

    public void addChildren(AIBuilderContext ctx) {
        var formActivityDef = ctx.getFormActivityDef();
        var formSectionCreator = ctx.creators().getFormSectionCreator();
        ctx.getFormInstance().setIntroduction(formSectionCreator.createSection(ctx, formActivityDef.getIntroduction()));
        ctx.getFormInstance().setClosing(formSectionCreator.createSection(ctx, formActivityDef.getClosing()));
        formActivityDef.getSections().forEach(s -> {
            ctx.getFormInstance().getBodySections().add(formSectionCreator.createSection(ctx, s));
        });
    }

    public void renderTitleAndSubtitle(AIBuilderContext ctx) {
        var title = extractOptionalActivityTranslation(ctx.getFormActivityDef().getTranslatedTitles(), ctx.getIsoLangCode());
        var subtitle = extractOptionalActivityTranslation(ctx.getFormActivityDef().getTranslatedSubtitles(), ctx.getIsoLangCode());
        ctx.getFormInstance().setTitle(TemplateHandler.renderTemplate(ctx, title));
        ctx.getFormInstance().setSubtitle(TemplateHandler.renderTemplate(ctx, subtitle));
    }

    public void updateBlockStatuses(AIBuilderContext ctx) {
        ctx.getFormInstance().updateBlockStatuses(
                ctx.getHandle(),
                ctx.getInterpreter(),
                ctx.getUserGuid(),
                ctx.getOperatorGuid(),
                ctx.getFormResponse().getGuid(),
                null);
    }

    public void renderContent(AIBuilderContext ctx, Renderable.Provider<String> rendered) {
        ctx.getFormInstance().getAllSections().forEach(s ->
                s.applyRenderedTemplates(rendered, ctx.getStyle()));
        ctx.getFormInstance().setReadonlyHint(
                toPlainText(ctx.getFormInstance().getReadonlyHintTemplateId(), rendered, ctx.getStyle()));
        ctx.getFormInstance().setActivityDefinitionLastUpdatedText(
                toPlainText(ctx.getFormInstance().getLastUpdatedTextTemplateId(), rendered, ctx.getStyle()));
    }

    private FormInstance constructFormInstance(AIBuilderContext ctx) {
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
                addAndRenderTemplate(ctx, formActivityDef.getReadonlyHintTemplate()),
                formActivityDef.getIntroduction() != null ? formActivityDef.getIntroduction().getSectionId() : null,
                formActivityDef.getClosing() != null ? formActivityDef.getClosing().getSectionId() : null,
                formResponse.getCreatedAt(),
                formResponse.getFirstCompletedAt(),
                addAndRenderTemplate(ctx, formActivityDef.getLastUpdatedTextTemplate()),
                formActivityDef.getLastUpdated(),
                canDelete,
                formActivityDef.isFollowup(),
                formResponse.getHidden(),
                formActivityDef.isExcludeFromDisplay(),
                formResponse.getSectionIndex()
        );

        return formInstance;
    }
}
