package org.broadinstitute.ddp.service.actvityinstancebuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;

/**
 * Contains content renderer context initialization and template render methods.
 * Provides creation of renderer context (for a template engine) and populates it with
 * data which needs during templates rendering.
 */
public class TemplateRenderHelper {

    public enum RenderContextSource {
        FORM_RESPONSE_AND_ACTIVITY_DEF,
        FORM_INSTANCE
    }

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
    public void createRendererInitialContext(AIBuilderContext ctx, RenderContextSource renderContextSource) {
        Map<String, String> commonSnapshot = I18nContentRenderer
                .newValueProviderBuilder(ctx.getHandle(), ctx.getFormResponse().getParticipantId(),
                        ctx.getOperatorGuid(), ctx.getStudyGuid())
                .build().getSnapshot();

        Map<String, String> snapshot = ctx.getHandle().attach(ActivityInstanceDao.class).findSubstitutions(
                ctx.getFormResponse().getId());
        ctx.getActivitySnapshots().putAll(snapshot);

        Map<String, Object> context = new HashMap<>();
        switch (renderContextSource) {
            case FORM_RESPONSE_AND_ACTIVITY_DEF:
                context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                        .withFormResponse(ctx.getFormResponse(), ctx.getFormActivityDef(), ctx.getIsoLangCode())
                        .withSnapshot(commonSnapshot)
                        .withSnapshot(snapshot)
                        .build());
                break;
            case FORM_INSTANCE:
                context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                        .withFormInstance(ctx.getFormInstance())
                        .withSnapshot(commonSnapshot)
                        .withSnapshot(snapshot)
                        .build());
                break;
            default:
                throw new DDPException("Unhandled renderContextSource " + renderContextSource);
        }

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
