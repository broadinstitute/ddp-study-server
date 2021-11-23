package org.broadinstitute.ddp.model.activity.definition.template;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;

public class TemplateUtil {

    /**
     * Creates Velocity context for a case when activity instance and user data not available.
     * The example of such case: study data export.
     * @param useDefaultsForDdpMethods boolean value defining if default values should be generated/used in DDP methods
     *                                 answer() and isGovernedParticipant()
     * @return Map with Velocity context (basic element in it - an instance of object {@link RenderValueProvider} which
     *     stored to the map with key {@link I18nTemplateConstants#DDP}
     */
    static Map<String, Object> createRendererInitialContextWithoutUserAndInstanceData(boolean useDefaultsForDdpMethods) {
        Map<String, Object> context = new HashMap<>();
        ZoneId zone = ZoneOffset.UTC;
        context.put(I18nTemplateConstants.DDP, new RenderValueProvider.Builder()
                .setParticipantTimeZone(zone)
                .setDate(LocalDate.now(zone))
                .setUseDefaultsForDdpMethods(useDefaultsForDdpMethods)
                .build());
        return context;
    }

    public static String render(
            String templateText,
            Collection<TemplateVariable> templateVariables,
            String languageCode,
            I18nContentRenderer renderer,
            Map<String, Object> initialContext) {

        Map<String, Object> variablesTxt = new HashMap<>();
        if (initialContext != null) {
            variablesTxt.putAll(initialContext);
        }
        if (templateVariables != null) {
            for (TemplateVariable variable : templateVariables) {
                Optional<Translation> translation = variable.getTranslation(languageCode);
                if (translation.isEmpty()) {
                    translation = variable.getTranslation(LanguageStore.getDefault().getIsoCode());
                }
                variablesTxt.put(variable.getName(), translation.<Object>map(Translation::getText).orElse(null));
            }
        }
        return renderer.renderToString(templateText, variablesTxt);
    }

    public static String render(
            String templateText,
            Collection<TemplateVariable> templateVariables,
            String languageCode,
            boolean useDefaultsForDdpMethods) {
        return render(
                templateText,
                templateVariables,
                languageCode,
                new I18nContentRenderer(),
                createRendererInitialContextWithoutUserAndInstanceData(useDefaultsForDdpMethods));
    }

    /**
     * This method renders values with context containing reference to {@link RenderValueProvider} (with key = 'DDP').
     * Therefore it is available all methods of this class. But methods should return default values
     * (parameter `useDefaultsForDdpMethods`=`true`).
     * Setting this parameter to `true` forces the following behaviour for some of DDP methods
     * (answer(), isGovernedParticipant()) defined in {@link RenderValueProvider}:
     * <pre>
     *     - isGovernedParticipant() returns both parameters separated with slash (`isTrueString`/`isFalseString`);
     *     - answer() returns fallbackValue.
     * </pre>
     */
    public static String renderWithDefaultValues(
            String templateText,
            Collection<TemplateVariable> templateVariables,
            String languageCode) {
        return render(templateText, templateVariables, languageCode, true);
    }
}
