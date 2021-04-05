package org.broadinstitute.ddp.service.actvityinstancebuilder.util;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.AIBuilderContext;

/**
 * Provides creation of renderer context (for a template engine) and populates it with
 * data which needs during templates rendering.
 */
public class RendererInitialContextHandler {

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
    public static void createRendererInitialContext(AIBuilderContext ctx) {
        Map<String, String> commonSnapshot = I18nContentRenderer
                .newValueProviderBuilder(ctx.getHandle(), ctx.getFormResponse().getParticipantId())
                .build().getSnapshot();

        Map<String, String> snapshot = ctx.getHandle().attach(ActivityInstanceDao.class).findSubstitutions(
                ctx.getFormResponse().getId());

        Map<String, Object> context = new HashMap<>();
        context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                .withSnapshot(commonSnapshot)
                .withSnapshot(snapshot)
                .build());

        LocalDate lastUpdatedDate = ctx.getFormActivityDef().getLastUpdated() == null
                ? null : ctx.getFormActivityDef().getLastUpdated().toLocalDate();
        context.put(I18nTemplateConstants.LAST_UPDATED, lastUpdatedDate);
        ctx.setLastUpdatedDate(lastUpdatedDate);

        ctx.getRendererInitialContext().putAll(context);
    }

    /**
     * Rebuild renderer initial context by adding to already existing data the generated {@link FormInstance}
     */
    public static void addInstanceToRendererInitialContext(AIBuilderContext ctx, FormInstance formInstance) {
        ctx.getRendererInitialContext().put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder(
                (RenderValueProvider) ctx.getRendererInitialContext().get(I18nTemplateConstants.DDP))
                .withFormInstance(formInstance)
                .build());
        ctx.getRendererInitialContext().put(I18nTemplateConstants.LAST_UPDATED, ctx.getLastUpdatedDate());
    }
}
