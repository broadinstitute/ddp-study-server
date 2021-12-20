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
 * Default renderer finds template variables from a list of {@link TemplateVariable}'s
 * connected to a {@link Template}. And for each of variables it detects translations (for each of a specified
 * languages). Translations are stored in table `i18n_template_substitution`.<br>
 * Note: the template default renderer is used in a case when template has references
 * to {@link TemplateVariable}'s (i.e. it's method {@link Template#getVariables()} returns
 * non-empty list.
 */
public class I18nTemplateDefaultRenderer {

    static String render(
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
        return renderer.renderToString(templateText, VelocityUtil.convertVariablesWithCompoundNamesToMap(variablesTxt));
    }
}
