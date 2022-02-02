package org.broadinstitute.ddp.content;

import static org.broadinstitute.ddp.content.I18nContextUtil.createRendererInitialContextWithoutUserAndInstanceData;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.service.I18nTranslationService;


/**
 * Facade class providing {@link Template}'s rendering.
 * It tries to render a template with new approach (taking translations from table `i18n_translation': it tries
 * to find translations by template variables specified in {@link Template#getTemplateText()}).
 * And if it is not found in `i18n_translation' then template is rendered using a default approach:
 * gets variables from {@link Template#getVariables()} and gets translations from variables:
 * {@link TemplateVariable#getTranslations()}.
 *
 */
public class I18nTemplateRenderFacade {

    private static final Map<String, Object> INITIAL_CONTEXT_NO_INSTANCE =
            createRendererInitialContextWithoutUserAndInstanceData(true);

    public static final I18nTemplateRenderFacade INSTANCE = new I18nTemplateRenderFacade();

    private final I18nContentRenderer renderer = new I18nContentRenderer();
    private final I18nTranslationService i18nTranslationService = new I18nTranslationService();


    /**
     * Render a specified template: detect a list of variables defined in a template, detect values of each variable
     * and do template rendering (evaluation of a final text where variables replaced to it's values for a current language).
     * Initially called a rendering with new algorithm (trying to find template variable translations from
     * JSON doc stored in DB table `i18n-translation`).
     * If variables not found in the JSON then resolve template variables using default approach: where template
     * variables stored in a connected list of {@link TemplateVariable}.
     *
     * @param template       template which text to render (evaluate)
     * @param isoLangCode    language for which to detect a translation
     * @param initialContext Velocity initial context
     * @return String - rendered (evaluated) template text (where where variables replaced to it's values for
     *     a specified language)
     */
    public String renderTemplate(Template template, String isoLangCode, Map<String, Object> initialContext) {
        String renderedTemplate = renderTemplateFromJson(template, template.getTemplateText(), isoLangCode, initialContext);
        if (renderedTemplate == null) {
            renderedTemplate = renderTemplateDefault(template.getTemplateText(), template.getVariables(), isoLangCode, initialContext);
        }
        return renderedTemplate;
    }

    /**
     * This method can render template values with context containing reference to
     * {@link RenderValueProvider} (with key = 'DDP').
     * Therefore it is available all methods of this class. But methods should return default values
     * (parameter `useDefaultsForDdpMethods`=`true`).
     * Setting this parameter to `true` forces the following behaviour for some of DDP methods
     * (answer(), isGovernedParticipant()) defined in {@link RenderValueProvider}:
     * <pre>
     *     - isGovernedParticipant() returns both parameters separated with slash (`isTrueString`/`isFalseString`);
     *     - answer() returns fallbackValue.
     * </pre>
     */
    public String renderTemplate(
            Template template,
            String templateText,
            Collection<TemplateVariable> templateVariables,
            String isoLangCode,
            boolean useDefaultsForDdpMethods) {
        String renderedTemplate = renderTemplateFromJson(template, templateText, isoLangCode,
                getInitialContextNoInstance(useDefaultsForDdpMethods));
        if (renderedTemplate == null) {
            renderedTemplate = renderTemplateDefault(templateText, templateVariables,
                    isoLangCode, getInitialContextNoInstance(useDefaultsForDdpMethods));
        }
        return renderedTemplate;
    }

    public String renderTemplateWithDefaultValues(
            String templateText,
            Collection<TemplateVariable> templateVariables,
            String isoLangCode) {
        return renderTemplate(null, templateText, templateVariables, isoLangCode, true);
    }

    private String renderTemplateFromJson(
            Template template,
            String templateText,
            String isoLangCode,
            Map<String, Object> initialContext) {
        return I18nTemplateFromJSONRenderer.render(template, templateText, isoLangCode, renderer, i18nTranslationService, initialContext);
    }

    private String renderTemplateDefault(
            String templateText,
            Collection<TemplateVariable> templateVariables,
            String isoLangCode,
            Map<String, Object> initialContext) {
        return I18nTemplateDefaultRenderer.render(templateText, templateVariables, isoLangCode, renderer, initialContext);
    }

    /**
     * Reuse the same map in order to avoid map creation each time.
     */
    public static Map<String, Object> getInitialContextNoInstance(boolean useDefaultForDdpMethods) {
        RenderValueProvider renderValueProvider = (RenderValueProvider) INITIAL_CONTEXT_NO_INSTANCE.get(I18nTemplateConstants.DDP);
        renderValueProvider.setDate(LocalDate.now(ZoneOffset.UTC));
        renderValueProvider.setUseDefaultsForDdpMethods(useDefaultForDdpMethods);
        return INITIAL_CONTEXT_NO_INSTANCE;
    }
}
