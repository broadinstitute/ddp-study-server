package org.broadinstitute.ddp.service.actvityinstanceassembler;

import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.definition.template.Template;

public class RenderTemplateUtil {


    public static void renderTemplate(
            Template template,
            Renderable renderable,
            ActivityInstanceAssembleService.Context context) {
        renderTemplate(template, null, renderable, context);
    }

    public static void renderTemplate(
            Template template,
            Long templateId,
            Renderable renderable,
            ActivityInstanceAssembleService.Context context) {
        if (template != null) {
            String renderedResult = template.render(context.getIsoLangCode());
            context.getRenderedTemplates().put(templateId != null ? templateId : template.getTemplateId(), renderedResult);
            if (renderable != null) {
                renderable.applyRenderedTemplates(context.getRenderedTemplates()::get, context.getStyle());
            }
        }
    }
}
