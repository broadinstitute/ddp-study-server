package org.broadinstitute.ddp.model.mail;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;

@Value
@AllArgsConstructor
public class MailTemplateSubstitution {
    Map<String, String> values = new HashMap<>();

    public MailTemplateSubstitution withValue(final String name, final String value) {
        values.put(name, value);

        return this;
    }
}
