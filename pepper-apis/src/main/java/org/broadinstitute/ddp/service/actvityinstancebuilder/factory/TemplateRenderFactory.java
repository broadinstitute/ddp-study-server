package org.broadinstitute.ddp.service.actvityinstancebuilder.factory;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;

/**
 * The factory implementing the code responsible for the {@link Template}'s rendering
 */
public class TemplateRenderFactory {

    /**
     * Add to {@link AIBuilderContext#getRenderedTemplates()} map a key/value pair: templateId/rendered template string.
     * This map will be used when applying templates to built {@link ActivityInstance} parts.
     * @param ctx Context where map {@link AIBuilderContext#getRenderedTemplates()} stored.
     * @param template added and rendered template
     * @return Long templateID
     */
    public Long renderTemplate(AIBuilderContext ctx, Template template) {
        if (template != null) {
            if (!ctx.getParams().isDisableTemplatesRendering()) {
                ctx.getRenderedTemplates().put(template.getTemplateId(), template.render(
                        ctx.getIsoLangCode(), ctx.getI18nContentRenderer(), ctx.getRendererInitialContext()));
            }
            return template.getTemplateId();
        }
        return null;
    }

    /**
     * A detected {@link Template} text can contain a template engine expression and needs to be rendered (processed)
     * by TemplateEngine (currently used Velocity Engine).
     * This method try to render 'templateText' by a template engine.
     * @param ctx Context where map {@link AIBuilderContext#getRenderedTemplates()} stored.
     * @param templateText - template text of a {@link Template} for e certain language
     * @return String rendered (processed by a template engine); if no expression detected or 'templateText' is
     *     null then returned a value of 'templateText'
     */
    public String renderTemplate(AIBuilderContext ctx, String templateText) {
        if (templateText != null) {
            return ctx.getI18nContentRenderer().renderToString(templateText, ctx.getRendererInitialContext());
        }
        return null;
    }
}
