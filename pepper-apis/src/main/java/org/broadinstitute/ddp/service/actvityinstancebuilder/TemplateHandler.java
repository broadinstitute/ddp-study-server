package org.broadinstitute.ddp.service.actvityinstancebuilder;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;

public class TemplateHandler {

    /**
     * Add to {@link Context#getRenderedTemplates()} map a key/value pair: templateId/rendered template string.
     * This map will be used when applying templates to built {@link ActivityInstance} parts.
     * @param ctx Context where map {@link Context#getRenderedTemplates()} stored.
     * @param template added and rendered template
     * @return Long templateID
     */
    public static Long addAndRenderTemplate(Context ctx, Template template) {
        if (template != null) {
            ctx.getRenderedTemplates().put(template.getTemplateId(), template.render(
                    ctx.getIsoLangCode(), ctx.getI18nContentRenderer(), ctx.getRendererInitialContext()));
            return template.getTemplateId();
        }
        return null;
    }
}
