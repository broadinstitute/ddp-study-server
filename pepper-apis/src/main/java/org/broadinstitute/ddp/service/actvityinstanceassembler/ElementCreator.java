package org.broadinstitute.ddp.service.actvityinstanceassembler;


import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.definition.template.Template;

/**
 * Abstract creator class
 */
public class ElementCreator {

    protected final ActivityInstanceAssembleService.Context context;

    public ElementCreator(ActivityInstanceAssembleService.Context context) {
        this.context = context;
    }

    protected Long getTemplateId(Template template) {
        if (template != null) {
            context.getRenderedTemplates().put(template.getTemplateId(), template.render(context.getIsoLangCode()));
            return template.getTemplateId();
        }
        return null;
    }

    protected void applyRenderedTemplates(Renderable renderable) {
        renderable.applyRenderedTemplates(context.getRenderedTemplates()::get, context.getStyle());
    }
}
