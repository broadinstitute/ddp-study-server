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
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;

/**
 * Contains content renderer context initialization and template render methods.
 * Provides creation of renderer context (for a template engine) and populates it with
 * data which needs during templates rendering.
 */
public class TemplateRenderHelper {

    /**
     * Creates renderer initial context and popualtes it with common data.
     * The created context stored in {@link AIBuilderContext#getRendererInitialContext()}
     *
     * <p>The following types of data stored to the renderer context:
     * <ul>
     *     <li>user profile data (stored to map with key="ddp");</li>
     *     <li>activity instance substitutions (stored to map with key="ddp");</li>
     *     <li>form activity last updated date (if previous version exists).</li>
     * </ul>
     */
    public void createRendererInitialContext(AIBuilderContext ctx) {
        Map<String, String> commonSnapshot = I18nContentRenderer
                .newValueProviderBuilder(ctx.getHandle(), ctx.getFormResponse().getParticipantId(),
                        ctx.getOperatorGuid(), ctx.getStudyGuid())
                .build().getSnapshot();

        Map<String, String> snapshot = ctx.getHandle().attach(ActivityInstanceDao.class).findSubstitutions(
                ctx.getFormResponse().getId());

        Map<String, Object> context = new HashMap<>();
        context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                .withSnapshot(commonSnapshot)
                .withSnapshot(snapshot)
                .build());

        putLastUpdatedToRenderContext(ctx, context);

        ctx.getRendererInitialContext().putAll(context);
    }

    /**
     * Rebuild renderer initial context by adding to already existing data the generated {@link FormInstance}
     */
    public void addInstanceToRendererInitialContext(AIBuilderContext ctx, FormInstance formInstance) {
        ctx.getRendererInitialContext().put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder(
                (RenderValueProvider) ctx.getRendererInitialContext().get(I18nTemplateConstants.DDP))
                .withFormInstance(formInstance)
                .build());
    }

    protected void putLastUpdatedToRenderContext(AIBuilderContext ctx, Map<String, Object> context) {
        LocalDate lastUpdatedDate = ctx.getFormActivityDef().getLastUpdated() == null
                ? null : ctx.getFormActivityDef().getLastUpdated().toLocalDate();
        context.put(I18nTemplateConstants.LAST_UPDATED, I18nContentRenderer.convertToString(lastUpdatedDate));
    }

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
