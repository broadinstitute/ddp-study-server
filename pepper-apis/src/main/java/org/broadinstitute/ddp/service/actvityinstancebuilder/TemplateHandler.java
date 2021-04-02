package org.broadinstitute.ddp.service.actvityinstancebuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;

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

    /**
     * A detected {@link Template} text can contain a template engine expression and needs to be rendered (processed)
     * by TemplateEngine (currently used Velocity Engine).
     * This method try to render 'templateText' by a template engine.
     * @param ctx Context where map {@link Context#getRenderedTemplates()} stored.
     * @param templateText - template text of a {@link Template} for e certain language
     * @return String rendered (processed by a template engine); if no expression detected or 'templateText' is
     *     null then returned a value of 'templateText'
     */
    public static String renderTemplate(Context ctx, String templateText) {
        if (templateText != null) {
            return ctx.getI18nContentRenderer().renderToString(templateText, ctx.getRendererInitialContext());
        }
        return templateText;
    }

    public static Map<String, Object> createRendererInitialContext(Context ctx) {
        Map<String, String> commonSnapshot = I18nContentRenderer
                .newValueProviderBuilder(ctx.getHandle(), ctx.getFormResponse().getParticipantId())
                .build().getSnapshot();

        ctx.setCommonSnapshot(commonSnapshot);
        Map<String, String> snapshot = ctx.getHandle().attach(ActivityInstanceDao.class).findSubstitutions(
                ctx.getFormResponse().getId());
        ctx.setSnapshot(snapshot);

        Map<String, Object> context = new HashMap<>();
        context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                .withSnapshot(commonSnapshot)
                .withSnapshot(snapshot)
                .build());

        LocalDate lastUpdatedDate = ctx.getFormActivityDef().getLastUpdated() == null
                ? null : ctx.getFormActivityDef().getLastUpdated().toLocalDate();
        context.put(I18nTemplateConstants.LAST_UPDATED, lastUpdatedDate);

        return context;
    }

    public static void addInstanceToRendererInitialContext(Context ctx, FormInstance formInstance) {
        ctx.getRendererInitialContext().put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                .withFormInstance(formInstance)
                .withSnapshot(ctx.getCommonSnapshot())
                .withSnapshot(ctx.getSnapshot())
                .build());
    }
}
