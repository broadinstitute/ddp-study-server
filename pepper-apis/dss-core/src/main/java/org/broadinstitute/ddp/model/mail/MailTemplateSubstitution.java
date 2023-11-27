package org.broadinstitute.ddp.model.mail;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.exception.DDPException;

import java.util.HashMap;
import java.util.Map;

@Value
@AllArgsConstructor
public class MailTemplateSubstitution {
    Map<String, String> values = new HashMap<>();

    public MailTemplateSubstitution withValue(final String name, final String value) {
        if (StringUtils.isBlank(name) || value == null) {
            throw new DDPException("Incorrect substitution value. The name should not be blank and value should not be null");
        }

        values.put(name, value);
        return this;
    }
}
