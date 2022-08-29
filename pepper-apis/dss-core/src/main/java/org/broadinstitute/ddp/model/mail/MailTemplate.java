package org.broadinstitute.ddp.model.mail;

import lombok.AllArgsConstructor;
import lombok.Value;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.exception.DDPException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value
@AllArgsConstructor
public class MailTemplate {
    String contentType;
    String subject;
    String body;
    List<MailTemplateRepeatableElement> repeatableElements;

    Map<String, List<MailTemplateRepeatableElement>> substitutions = new HashMap<>();

    private String render(final String content) {
        var renderedContent = String.valueOf(content);
        for (final var substitution : substitutions.entrySet()) {
            renderedContent = renderedContent.replace(toKey(substitution.getKey()), StreamEx.of(substitution.getValue())
                    .map(MailTemplateRepeatableElement::render)
                    .joining());
        }

        return renderedContent;
    }

    private String toKey(final String key) {
        return String.format("${%s}", key);
    }

    public String renderBody() {
        return render(body);
    }

    public String renderSubject() {
        return render(subject);
    }

    public void setSubstitutions(final String name, final MailTemplateSubstitution... values) {
        setSubstitutions(name, Arrays.asList(values));
    }

    public void setSubstitutions(final String name, final List<MailTemplateSubstitution> values) {
        substitutions.put(name, StreamEx.of(values)
                .map(value -> transform(name, value))
                .removeBy(MailTemplateRepeatableElement::getName, null)
                .toList());
    }

    private MailTemplateRepeatableElement transform(final String elementName, final MailTemplateSubstitution substitution) {
        return StreamEx.of(repeatableElements)
                .filterBy(MailTemplateRepeatableElement::getName, elementName)
                .findFirst()
                .map(element -> new MailTemplateRepeatableElement(element.getName(), element.getContent(), substitution.getValues()))
                .orElseThrow(() -> new DDPException("Repeatable element " + elementName + " doesn't exist"));
    }
}
