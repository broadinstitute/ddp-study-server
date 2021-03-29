package org.broadinstitute.ddp.service.actvityinstancebuilder;


import static org.broadinstitute.ddp.service.actvityinstancebuilder.TemplateHandler.addAndRenderTemplate;
import static org.broadinstitute.ddp.util.TemplateRenderUtil.toPlainText;
import static org.broadinstitute.ddp.util.TranslationUtil.extractOptionalActivityTranslation;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;

/**
 * Creates {@link FormInstance}
 */
public class FormInstanceCreator {

    public FormInstance createFormInstance(Context ctx) {
        var formInstance = constructFormInstance(ctx);
        addChildren(ctx, formInstance);
        formInstance.setDisplayNumbers();
        renderContent(formInstance, ctx.getRenderedTemplates()::get, ctx.getStyle());
        updateBlockStatuses(ctx, formInstance);
        return formInstance;
    }

    private FormInstance constructFormInstance(Context ctx) {
        var formActivityDef = ctx.getFormActivityDef();
        var formResponse = ctx.getFormResponse();

        var title = extractOptionalActivityTranslation(formActivityDef.getTranslatedTitles(), ctx.getIsoLangCode());
        var subtitle = extractOptionalActivityTranslation(formActivityDef.getTranslatedSubtitles(), ctx.getIsoLangCode());

        boolean readonly = ActivityInstanceUtil.isReadonly(
                formActivityDef.getEditTimeoutSec(),
                formResponse.getCreatedAt(),
                formResponse.getLatestStatus().getType().name(),
                formActivityDef.isWriteOnce(),
                formResponse.getReadonly());

        var formInstance = new FormInstance(
                formResponse.getParticipantId(),
                formResponse.getId(),
                formResponse.getActivityId(),
                formResponse.getActivityCode(),
                formActivityDef.getFormType(),
                formResponse.getGuid(),
                title,
                subtitle,
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
                formActivityDef.canDeleteInstances(),
                formActivityDef.isFollowup(),
                formResponse.getHidden(),
                formActivityDef.isExcludeFromDisplay(),
                formResponse.getSectionIndex()
        );
        return formInstance;
    }

    private void addChildren(Context ctx, FormInstance formInstance) {
        var formActivityDef = ctx.getFormActivityDef();
        var formSectionCreator = ctx.creators().getFormSectionCreator();
        formInstance.setIntroduction(formSectionCreator.createSection(ctx, formActivityDef.getIntroduction()));
        formInstance.setClosing(formSectionCreator.createSection(ctx, formActivityDef.getClosing()));
        formActivityDef.getSections().forEach(s -> {
            formInstance.getBodySections().add(formSectionCreator.createSection(ctx, s));
        });
    }

    private void updateBlockStatuses(Context ctx, FormInstance formInstance) {
        formInstance.updateBlockStatuses(
                ctx.getHandle(),
                ctx.getInterpreter(),
                ctx.getUserGuid(),
                ctx.getOperatorGuid(),
                ctx.getFormResponse().getGuid(),
                null);
    }

    private void renderContent(FormInstance formInstance, Renderable.Provider<String> rendered, ContentStyle style) {
        formInstance.getAllSections().forEach(s -> s.applyRenderedTemplates(rendered, style));
        formInstance.setReadonlyHint(toPlainText(formInstance.getReadonlyHintTemplateId(), rendered, style));
        formInstance.setActivityDefinitionLastUpdatedText(toPlainText(formInstance.getLastUpdatedTextTemplateId(), rendered, style));
    }
}
