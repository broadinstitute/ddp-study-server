package org.broadinstitute.ddp.service.actvityinstancebuilder;

import java.time.LocalDate;
import java.util.Map;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RendererInitialContextCreator;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;

/**
 * Contains content renderer context initialization and template render methods.
 * Provides creation of renderer context (for a template engine) and populates it with
 * data which needs during templates rendering.
 */
public class TemplateRenderHelper {

    /**
     * Creates renderer initial context and populates it with common data.
     * The created context stored in {@link AIBuilderContext#getRendererInitialContext()}
     *
     * <p>The following types of data stored to the renderer context:
     * <ul>
     *     <li>user profile data (stored to map with key="ddp");</li>
     *     <li>activity instance substitutions (stored to map with key="ddp");</li>
     *     <li>form activity last updated date (if previous version exists).</li>
     * </ul>
     */
    public void createRendererInitialContext(AIBuilderContext ctx, RendererInitialContextCreator.RenderContextSource renderContextSource) {
        Map<String, Object> context = RendererInitialContextCreator.createRendererInitialContext(
                ctx.getHandle(),
                ctx.getInstanceGuid(),
                ctx.getFormResponse() != null ? ctx.getFormResponse().getParticipantId() : ctx.getFormInstance().getParticipantUserId(),
                ctx.getOperatorGuid(),
                ctx.getStudyGuid(),
                ctx.getIsoLangCode(),
                ctx.getFormActivityDef(),
                ctx.getFormResponse(),
                ctx.getFormInstance(),
                ctx.getActivitySnapshots(),
                renderContextSource
        );
        putLastUpdatedToRenderContext(ctx, context);
        ctx.getRendererInitialContext().putAll(context);
    }

    protected void putLastUpdatedToRenderContext(AIBuilderContext ctx, Map<String, Object> context) {
        LocalDate lastUpdatedDate = ctx.getFormActivityDef().getLastUpdated() == null
                ? null : ctx.getFormActivityDef().getLastUpdated().toLocalDate();
        context.put(I18nTemplateConstants.LAST_UPDATED, I18nContentRenderer.convertToString(lastUpdatedDate));
    }

    /**
     * Add to {@link AIBuilderContext#getTemplates()} map a key/value pair: templateId/template.
     * This map will be used when rendering templates and applying templates to built {@link ActivityInstance} parts.
     * @param ctx Context where map {@link AIBuilderContext#getTemplates()} stored.
     * @param template added template
     * @return Long templateID
     */
    public Long addTemplate(AIBuilderContext ctx, Template template) {
        if (template != null) {
            if (!ctx.getParams().isDisableTemplatesRendering()) {
                ctx.getTemplates().put(template.getTemplateId(), template);
            }
            return template.getTemplateId();
        }
        return null;
    }

    /**
     * A detected {@link Template} text can contain a template engine expression and needs to be rendered (processed)
     * by TemplateEngine (currently used Velocity Engine).
     * This method try to render 'templateText' by a template engine.
     * @param ctx Context where map {@link AIBuilderContext#getTemplates()} stored.
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
