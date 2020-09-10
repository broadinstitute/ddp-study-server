package org.broadinstitute.ddp.content;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    private String participantGuid;
    private String participantFirstName;
    private String participantLastName;
    private LocalDate participantBirthDate;
    private ZoneId participantTimeZone;
    private LocalDate date;
    private String testResultCode;
    private String testResultReason;
    private Instant testResultTimeCompleted;

    private RenderValueProvider() {
        // Use builder.
    }

    /**
     * Returns participant's guid, if available.
     */
    public String participantGuid() {
        return participantGuid;
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
     * Returns participant's birth date in given format, if available.
     */
    public String participantBirthDate(String format) {
        if (participantBirthDate == null) {
            return null;
        }
        try {
            return DateTimeFormatter.ofPattern(format).format(participantBirthDate);
        } catch (Exception e) {
            LOG.warn("Error formatting participant birth date value '{}' using format '{}'", participantBirthDate, format, e);
            return participantBirthDate.toString();
        }
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
     * Returns test result code, if available.
     */
    public String testResultCode() {
        return testResultCode;
    }

    /**
     * Returns test result reason, if available.
     */
    public String testResultReason() {
        return testResultReason;
    }

    /**
     * Returns test result time completed in given format, if available.
     */
    public String testResultTimeCompleted(String format) {
        if (testResultTimeCompleted == null) {
            return null;
        }
        try {
            ZoneId zone = participantTimeZone == null ? ZoneOffset.UTC : participantTimeZone;
            return DateTimeFormatter.ofPattern(format).withZone(zone).format(testResultTimeCompleted);
        } catch (Exception e) {
            LOG.warn("Error formatting test result time completed value '{}' using format '{}'", testResultTimeCompleted, format, e);
            return testResultTimeCompleted.toString();
        }
    }

    // Get provided values as a map to save as snapshot. Should not be called within templates.
    public Map<String, String> getSnapshot() {
        var snapshot = new HashMap<String, String>();
        if (participantGuid != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.PARTICIPANT_GUID, participantGuid);
        }
        if (participantFirstName != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.PARTICIPANT_FIRST_NAME, participantFirstName);
        }
        if (participantLastName != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.PARTICIPANT_LAST_NAME, participantLastName);
        }
        if (participantBirthDate != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.PARTICIPANT_BIRTH_DATE, participantBirthDate.toString());
        }
        if (participantTimeZone != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.PARTICIPANT_TIME_ZONE, participantTimeZone.toString());
        }
        if (date != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.DATE, date.toString());
        }
        if (testResultCode != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.TEST_RESULT_CODE, testResultCode);
        }
        if (testResultReason != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.TEST_RESULT_REASON, testResultReason);
        }
        if (testResultTimeCompleted != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.TEST_RESULT_TIME_COMPLETED, testResultTimeCompleted.toString());
        }
        return snapshot;
    }

    public static final class Builder {
        private RenderValueProvider provider;

        public Builder() {
            provider = new RenderValueProvider();
        }

        public Builder setParticipantGuid(String participantGuid) {
            provider.participantGuid = participantGuid;
            return this;
        }

        public Builder setParticipantFirstName(String participantFirstName) {
            provider.participantFirstName = participantFirstName;
            return this;
        }

        public Builder setParticipantLastName(String participantLastName) {
            provider.participantLastName = participantLastName;
            return this;
        }

        public Builder setParticipantBirthDate(LocalDate participantBirthDate) {
            provider.participantBirthDate = participantBirthDate;
            return this;
        }

        public Builder setParticipantTimeZone(ZoneId participantTimeZone) {
            provider.participantTimeZone = participantTimeZone;
            return this;
        }

        public Builder setDate(LocalDate date) {
            provider.date = date;
            return this;
        }

        public Builder setTestResultCode(String testResultCode) {
            provider.testResultCode = testResultCode;
            return this;
        }

        public Builder setTestResultReason(String testResultReason) {
            provider.testResultReason = testResultReason;
            return this;
        }

        public Builder setTestResultTimeCompleted(Instant testResultTimeCompleted) {
            provider.testResultTimeCompleted = testResultTimeCompleted;
            return this;
        }

        public Builder withSnapshot(Map<String, String> snapshot) {
            String value = snapshot.get(I18nTemplateConstants.Snapshot.PARTICIPANT_GUID);
            if (value != null) {
                provider.participantGuid = value;
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.PARTICIPANT_FIRST_NAME);
            if (value != null) {
                provider.participantFirstName = value;
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.PARTICIPANT_LAST_NAME);
            if (value != null) {
                provider.participantLastName = value;
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.PARTICIPANT_BIRTH_DATE);
            if (value != null) {
                provider.participantBirthDate = LocalDate.parse(value);
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.PARTICIPANT_TIME_ZONE);
            if (value != null) {
                provider.participantTimeZone = ZoneId.of(value);
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.DATE);
            if (value != null) {
                provider.date = LocalDate.parse(value);
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.TEST_RESULT_CODE);
            if (value != null) {
                provider.testResultCode = value;
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.TEST_RESULT_REASON);
            if (value != null) {
                provider.testResultReason = value;
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.TEST_RESULT_TIME_COMPLETED);
            if (value != null) {
                provider.testResultTimeCompleted = Instant.parse(value);
            }

            return this;
        }

        public RenderValueProvider build() {
            RenderValueProvider copy = new RenderValueProvider();
            copy.participantGuid = provider.participantGuid;
            copy.participantFirstName = provider.participantFirstName;
            copy.participantLastName = provider.participantLastName;
            copy.participantBirthDate = provider.participantBirthDate;
            copy.participantTimeZone = provider.participantTimeZone;
            copy.date = provider.date;
            copy.testResultCode = provider.testResultCode;
            copy.testResultReason = provider.testResultReason;
            copy.testResultTimeCompleted = provider.testResultTimeCompleted;
            return copy;
        }
    }
}
