package org.broadinstitute.ddp.service.actvityinstancebuilder;


import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.definition.template.Template;

/**
 * Abstract creator class
 */
public class ElementCreator {

    protected final ActivityInstanceFromActivityDefStoreBuilder.Context context;

    public ElementCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
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
