package org.broadinstitute.ddp.model.mail;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@Value
@AllArgsConstructor
public class MailTemplateRepeatableElement {
    String name;
    String content;
    Map<String, String> substitutions;

    public String render() {
        var renderedContent = String.valueOf(content);
        for (final var substitution : substitutions.entrySet()) {
            renderedContent = renderedContent.replace(toKey(substitution.getKey()), substitution.getValue());
        }

        return renderedContent;
    }

    private String toKey(final String key) {
        return String.format("${%s}", key);
    }
}
