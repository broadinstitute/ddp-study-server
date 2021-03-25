package org.broadinstitute.ddp.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.jdbi.v3.core.Handle;

public class TemplateRenderUtil {

    public static String toPlainText(Long templateId, Renderable.Provider<String> rendered, ContentStyle style) {
        String renderedValue = null;
        if (templateId != null) {
            renderedValue = rendered.get(templateId);
            if (renderedValue != null) {
                if (style == ContentStyle.BASIC) {
                    return HtmlConverter.getPlainText(renderedValue);
                }
            }
        }
        return renderedValue;
    }

    public static Map<String, Object> createRendererInitialContext(
            Handle handle,
            long participantId,
            long instanceId,
            LocalDateTime activityDefinitionLastUpdated) {
        Map<String, String> commonSnapshot = I18nContentRenderer
                .newValueProviderBuilder(handle, participantId)
                .build().getSnapshot();
        Map<String, String> snapshot = handle.attach(ActivityInstanceDao.class).findSubstitutions(instanceId);
        Map<String, Object> context = new HashMap<>();
        context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                .withSnapshot(commonSnapshot)
                .withSnapshot(snapshot)
                .build());
        LocalDate lastUpdatedDate = activityDefinitionLastUpdated == null ? null : activityDefinitionLastUpdated.toLocalDate();
        context.put(I18nTemplateConstants.LAST_UPDATED, lastUpdatedDate);
        return context;
    }
}
