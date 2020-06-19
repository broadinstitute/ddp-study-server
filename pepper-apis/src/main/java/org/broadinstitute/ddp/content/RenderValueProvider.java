package org.broadinstitute.ddp.content;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides methods that can be called within templates to get certain values from the system.
 */
public class RenderValueProvider {

    private static final Logger LOG = LoggerFactory.getLogger(RenderValueProvider.class);

    private String participantFirstName;
    private String participantLastName;
    private LocalDate date;

    private RenderValueProvider() {
        // Use builder.
    }

    /**
     * Returns participant's first name, if available.
     */
    public String participantFirstName() {
        return participantFirstName;
    }

    /**
     * Returns participant's last name, if available.
     */
    public String participantLastName() {
        return participantLastName;
    }

    /**
     * Returns today's date in given format. Might return a snapshot-ed date.
     */
    public String date(String format) {
        try {
            return DateTimeFormatter.ofPattern(format).format(date);
        } catch (Exception e) {
            LOG.warn("Error formatting date value '{}' using format '{}'", date, format, e);
            return date.toString();
        }
    }

    /**
     * Get provided values as a map to save as snapshot. Should not be called within templates.
     */
    public Map<String, String> getSnapshot() {
        var snapshot = new HashMap<String, String>();
        if (participantFirstName != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.PARTICIPANT_FIRST_NAME, participantFirstName);
        }
        if (participantLastName != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.PARTICIPANT_LAST_NAME, participantLastName);
        }
        if (date != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.DATE, date.toString());
        }
        return snapshot;
    }

    public static final class Builder {
        private RenderValueProvider provider;

        public Builder() {
            provider = new RenderValueProvider();
        }

        public Builder setParticipantFirstName(String participantFirstName) {
            provider.participantFirstName = participantFirstName;
            return this;
        }

        public Builder setParticipantLastName(String participantLastName) {
            provider.participantLastName = participantLastName;
            return this;
        }

        public Builder setDate(LocalDate date) {
            provider.date = date;
            return this;
        }

        public Builder withSnapshot(Map<String, String> snapshot) {
            String value = snapshot.get(I18nTemplateConstants.Snapshot.PARTICIPANT_FIRST_NAME);
            if (value != null) {
                provider.participantFirstName = value;
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.PARTICIPANT_LAST_NAME);
            if (value != null) {
                provider.participantLastName = value;
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.DATE);
            if (value != null) {
                provider.date = LocalDate.parse(value);
            }

            return this;
        }

        public RenderValueProvider build() {
            RenderValueProvider copy = new RenderValueProvider();
            copy.participantFirstName = provider.participantFirstName;
            copy.participantLastName = provider.participantLastName;
            copy.date = provider.date;
            return copy;
        }
    }
}
