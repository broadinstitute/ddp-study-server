package org.broadinstitute.ddp.content;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

public class I18nContextUtil {

    /**
     * Creates Velocity context for a case when activity instance and user data not available.
     * The example of such case: study data export.
     * @param useDefaultsForDdpMethods boolean value defining if default values should be generated/used in DDP methods
     *                                 answer() and isGovernedParticipant()
     * @return Map with Velocity context (basic element in it - an instance of object {@link RenderValueProvider} which
     *     stored to the map with key {@link I18nTemplateConstants#DDP}
     */
    public static Map<String, Object> createRendererInitialContextWithoutUserAndInstanceData(boolean useDefaultsForDdpMethods) {
        Map<String, Object> context = new HashMap<>();
        ZoneId zone = ZoneOffset.UTC;
        context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                .setParticipantTimeZone(zone)
                .setDate(LocalDate.now(zone))
                .setUseDefaultsForDdpMethods(useDefaultsForDdpMethods)
                .build());
        return context;
    }
}
