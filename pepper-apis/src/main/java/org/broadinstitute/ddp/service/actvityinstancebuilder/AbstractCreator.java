package org.broadinstitute.ddp.service.actvityinstancebuilder;


import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;

/**
 * Base class extended by {@link ActivityInstance} creators.
 */
public abstract class AbstractCreator {

    protected final ActivityInstanceFromDefinitionBuilder.Context context;

    public AbstractCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
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
