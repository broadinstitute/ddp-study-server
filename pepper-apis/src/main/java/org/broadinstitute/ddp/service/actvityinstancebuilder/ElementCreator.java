package org.broadinstitute.ddp.service.actvityinstancebuilder;


import org.broadinstitute.ddp.model.activity.definition.template.Template;

/**
 * Abstract creator class
 */
public abstract class ElementCreator {

    protected final ActivityInstanceFromDefinitionBuilder.Context context;

    public ElementCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        this.context = context;
    }

    protected Long renderTemplateIfDefined(Template template) {
        if (template != null) {
            context.getRenderedTemplates().put(template.getTemplateId(), template.render(
                    context.getIsoLangCode(), context.getI18nContentRenderer(), context.getRendererInitialContext()));
            return template.getTemplateId();
        }
        return null;
    }
}
