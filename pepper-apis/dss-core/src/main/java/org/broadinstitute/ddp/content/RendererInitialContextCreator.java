package org.broadinstitute.ddp.content;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.jdbi.v3.core.Handle;

/**
 * Helper methods used for Velocity context creation (Map with key-string, value-object).
 * Basic part of this context - an instance of object {@link RenderValueProvider} which
 * stored to the map with key {@link I18nTemplateConstants#DDP}.
 */
public class RendererInitialContextCreator {

    public enum RenderContextSource {
        FORM_RESPONSE_AND_ACTIVITY_DEF,
        FORM_INSTANCE
    }

    /**
     * Creates Velocity context for a case where known user and instance data - this is
     * a typical workflow.
     * @param handle              jdbi handle
     * @param participantId       participant ID
     * @param operatorGuid        operator guid
     * @param studyGuid           study guid
     * @param isoLandCode         language code
     * @param formActivityDef     activity def object (used as a source of activity data together with FormResponse)
     * @param formResponse        FormResponse which can be used as a source of data for calculating logic in {@link RenderValueProvider}
     * @param formInstance        FormInstance which can be used as a source of data for calculating logic in {@link RenderValueProvider}
     * @param activitySnapshots   activity snapshots to be added to Velocity context
     * @param renderContextSource affects which kind of data (FormInstance or FormResponse) is used as a source of
     *                            data for {@link RenderValueProvider}
     * @return Map with Velocity context (basic element in it - an instance of object {@link RenderValueProvider} which
     *     stored to the map with key {@link I18nTemplateConstants#DDP}
     */
    public static Map<String, Object> createRendererInitialContext(
            Handle handle,
            String instanceGuid,
            long participantId,
            String operatorGuid,
            String studyGuid,
            String isoLandCode,
            FormActivityDef formActivityDef,
            FormResponse formResponse,
            FormInstance formInstance,
            Map<String, String> activitySnapshots,
            RenderContextSource renderContextSource) {

        final var instance = handle.attach(ActivityInstanceDao.class).findByActivityInstanceGuid(instanceGuid);
        Map<String, String> commonSnapshot = I18nContentRenderer
                .newValueProviderBuilder(handle, instance.get().getId(), participantId, operatorGuid, studyGuid)
                .build().getSnapshot();

        Map<String, String> snapshot = handle.attach(ActivityInstanceDao.class).findSubstitutions(formResponse.getId());
        activitySnapshots.putAll(snapshot);

        Map<String, Object> context = new HashMap<>();

        switch (renderContextSource) {
            case FORM_RESPONSE_AND_ACTIVITY_DEF:
                context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                        .withFormResponse(formResponse, formActivityDef, isoLandCode)
                        .withSnapshot(commonSnapshot)
                        .withSnapshot(snapshot)
                        .build());
                break;
            case FORM_INSTANCE:
                context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                        .withFormInstance(formInstance)
                        .withSnapshot(commonSnapshot)
                        .withSnapshot(snapshot)
                        .build());
                break;
            default:
                throw new DDPException("Unhandled renderContextSource " + renderContextSource);
        }

        return context;
    }
}
