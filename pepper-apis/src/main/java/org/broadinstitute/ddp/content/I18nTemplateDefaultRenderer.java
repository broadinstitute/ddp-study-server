package org.broadinstitute.ddp.content;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;

/**
 * {@link Template} default renderer.
 * Default renderer detects {@link TemplateVariable}'s from a list of {@link TemplateVariable}'s
 * connected to a {@link Template}. And for each of variable it detects a translation  (for a specified
 * language). Translations are stored in table `i18n_template_substitution`.
 */
public class I18nTemplateDefaultRenderer {

    static String render(
            Template template,
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
}
