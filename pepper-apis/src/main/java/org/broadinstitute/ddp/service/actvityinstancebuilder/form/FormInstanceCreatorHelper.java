package org.broadinstitute.ddp.service.actvityinstancebuilder.form;


import static org.broadinstitute.ddp.util.TemplateRenderUtil.toPlainText;
import static org.broadinstitute.ddp.util.TranslationUtil.extractOptionalActivityTranslation;

import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;

/**
 * Creates methods used during {@link FormInstance} building process.
 */
public class FormInstanceCreatorHelper {

    public void addChildren(AIBuilderContext ctx) {
        var formActivityDef = ctx.getFormActivityDef();
        var formSectionCreator = ctx.getAIBuilderFactory().getAICreatorsFactory().getFormSectionCreator();
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
        ctx.getFormInstance().setTitle(ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(ctx, title));
        ctx.getFormInstance().setSubtitle(ctx.getAIBuilderFactory().getTemplateRenderFactory().renderTemplate(ctx, subtitle));
    }

    public void updateBlockStatuses(AIBuilderContext ctx) {
        ctx.getAIBuilderFactory().getUpdateBlockStatusFactory().updateBlockStatuses(
                ctx.getHandle(),
                ctx.getFormInstance(),
                ctx.getInterpreter(),
                ctx.getUserGuid(),
                ctx.getOperatorGuid(),
                ctx.getFormResponse().getGuid(),
                ctx.getParams().getInstanceSummary());
    }

    public void renderContent(AIBuilderContext ctx, Renderable.Provider<String> rendered) {
        ctx.getFormInstance().getAllSections().forEach(s ->
                s.applyRenderedTemplates(rendered, ctx.getStyle()));
        ctx.getFormInstance().setReadonlyHint(
                toPlainText(ctx.getFormInstance().getReadonlyHintTemplateId(), rendered, ctx.getStyle()));
        ctx.getFormInstance().setActivityDefinitionLastUpdatedText(
                toPlainText(ctx.getFormInstance().getLastUpdatedTextTemplateId(), rendered, ctx.getStyle()));
    }
}
