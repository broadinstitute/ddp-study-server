package org.broadinstitute.ddp.content;

import static org.broadinstitute.ddp.DataDonationPlatform.MDC_STUDY;
import static org.broadinstitute.ddp.content.VelocityUtil.extractVelocityVariablesFromTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.cache.I18nTranslationStore;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.service.I18nTranslationService;
import org.slf4j.MDC;

public class I18nTemplateFromJSONRenderer {

    static String render(
            Template template,
            String templateText,
            String isoLangCode,
            I18nContentRenderer renderer,
            I18nTranslationService i18nTranslationService,
            Map<String, Object> initialContext) {

        Map<String, Object> context = new HashMap<>();
        if (initialContext != null) {
            context.putAll(initialContext);
        }
        List<String> variables = getTemplateVariables(template != null ? template.getTemplateId() : null, templateText);
        if (variables != null && variables.size() > 0) {
            String studyGuid = MDC.get(MDC_STUDY);
            boolean variablesDetected = true;
            for (String variable : variables) {
                String variableTxt = i18nTranslationService.getTranslation(variable, studyGuid, isoLangCode);
                if (variableTxt == null) {
                    // one of variables not found - cancel template evaluation and will try to evaluate it an old way
                    variablesDetected = false;
                    break;
                } else {
                    context.put(variable, variableTxt);
                }
            }
            if (variablesDetected) {
                return renderer.renderToString(templateText, context);
            }
        }
        return null;
    }

    private static List<String> getTemplateVariables(Long templateId, String templateText) {
        List<String> variables = null;
        if (templateId != null) {
            variables = I18nTranslationStore.INSTANCE.getTemplateVariables(templateId);
        }
        if (variables == null) {
            variables = extractVelocityVariablesFromTemplate(templateText);
            if (variables != null && variables.size() > 0 && templateId != null) {
                I18nTranslationStore.INSTANCE.putTemplateVariables(templateId, variables);
            }
        }
        return variables;
    }
}
