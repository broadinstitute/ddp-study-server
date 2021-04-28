package org.broadinstitute.ddp.content;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.dsm.KitReasonType;
import org.broadinstitute.ddp.model.dsm.TestResult;
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
    private String kitRequestId;
    private KitReasonType kitReasonType;
    private String testResultCode;
    private Instant testResultTimeCompleted;
    private Integer activityInstanceNumber;

    // To minimize database round-trips, we lookup answers using existing objects that should have the answer objects.
    // Depending on what's available for the provider, we'll use either response + activity or the instance object.
    private FormResponse formResponse;
    private FormActivityDef formActivity;
    private String isoLangCode;
    private FormInstance formInstance;

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
     * Returns the kit request id, if available.
     */
    public String kitRequestId() {
        return kitRequestId;
    }

    /**
     * Returns test result code, if available.
     */
    public String testResultCode() {
        return testResultCode;
    }

    /**
     * Returns test result code in user-friendly display, using the provided text.
     */
    public String testResultDisplay(String posText, String negText, String otherText) {
        if (StringUtils.isBlank(testResultCode)) {
            return null;
        }
        if (TestResult.POSITIVE_CODE.equals(testResultCode)) {
            return posText;
        } else if (TestResult.NEGATIVE_CODE.equals(testResultCode)) {
            return negText;
        } else {
            return otherText;
        }
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

    /**
     * Returns the activity instance number, if available.
     */
    public String activityInstanceNumber() {
        if (activityInstanceNumber == null) {
            return null;
        } else {
            return String.valueOf(activityInstanceNumber);
        }
    }

    /**
     * Provides more flexibility for how to display an activity instance number, if available. The activity instance
     * number will first be adjusted by subtracting the given offset. Then, if the adjusted number is less than the
     * given cutoff number, then no number will be displayed. The cutoff number effectively serves as the first number
     * to be displayed after the offset adjustment. The prefix is useful for optionally adding additional text when a
     * number is displayed (e.g. prepending a space or a "#" symbol).
     *
     * @param offsetToSubtract subtract this amount from the number
     * @param numberCutoff     if adjusted number is less than this cutoff number, no number will be displayed
     * @param prefix           a prefix to prepend
     * @return adjusted number
     */
    public String activityInstanceNumberDisplay(int offsetToSubtract, int numberCutoff, String prefix) {
        if (activityInstanceNumber == null) {
            return null;
        }
        int adjustedNumber = activityInstanceNumber - offsetToSubtract;
        if (adjustedNumber < numberCutoff) {
            return "";
        } else {
            return prefix + adjustedNumber;
        }
    }

    /**
     * Returns the answer for the question, or the fallback value if no answer. Need to assign the appropriate form
     * object(s) beforehand as sources for looking up answers, otherwise no substitution will be performed.
     *
     * <p>Because common case is displaying the answer within content, this substitution will by default renders the
     * "user-friendly" view of the answer. For example, if answer is a picklist, then the picklist option text will be
     * rendered rather than the option stable id.
     *
     * @param questionStableId the question stable id
     * @param fallbackValue    the fallback value
     * @return answer string representation if available, otherwise null
     */
    public String answer(String questionStableId, String fallbackValue) {
        if (formResponse != null) {
            return renderAnswerUsingFormResponse(questionStableId, fallbackValue);
        } else if (formInstance != null) {
            return renderAnswerUsingFormInstance(questionStableId, fallbackValue);
        } else {
            // No objects to use to lookup answers. Returning null here will keep this part of the template untouched,
            // in case we want to come back and do a second round of rendering.
            return null;
        }
    }

    private String renderAnswerUsingFormResponse(String questionStableId, String fallbackValue) {
        Answer answer = formResponse.getAnswer(questionStableId);
        if (answer == null || answer.isEmpty()) {
            // No answer response for this question yet, so use fallback.
            return fallbackValue;
        }
        switch (answer.getQuestionType()) {
            case PICKLIST:
                QuestionDef questionDef = formActivity.getQuestionByStableId(questionStableId);
                Map<String, PicklistOptionDef> options = ((PicklistQuestionDef) questionDef)
                        .getAllPicklistOptions().stream()
                        .collect(Collectors.toMap(PicklistOptionDef::getStableId, Function.identity()));
                return ((PicklistAnswer) answer).getValue().stream()
                        .map(selected -> options.get(selected.getStableId())
                                .getOptionLabelTemplate()
                                .render(isoLangCode))
                        .collect(Collectors.joining(","));
            case COMPOSITE: // Fall-through
            case FILE:
                // Have not decided what composite or file answers will look like yet.
                throw new DDPException("Rendering answer type " + answer.getQuestionType() + " is currently not supported");
            default:
                // Everything else will get turned into a string.
                return answer.getValue().toString();
        }
    }

    private String renderAnswerUsingFormInstance(String questionStableId, String fallbackValue) {
        Question question = formInstance.getQuestionByStableId(questionStableId);
        Answer answer = question != null && question.isAnswered()
                ? (Answer) question.getAnswers().get(0) : null;
        if (answer == null || answer.isEmpty()) {
            return fallbackValue;
        }
        switch (answer.getQuestionType()) {
            case PICKLIST:
                Map<String, String> options = ((PicklistQuestion) question)
                        .streamAllPicklistOptions()
                        .collect(Collectors.toMap(PicklistOption::getStableId, PicklistOption::getOptionLabel));
                return ((PicklistAnswer) answer).getValue().stream()
                        .map(selected -> options.get(selected.getStableId()))
                        .collect(Collectors.joining(","));
            case COMPOSITE: // Fall-through
            case FILE:
                throw new DDPException("Rendering answer type " + answer.getQuestionType() + " is currently not supported");
            default:
                return answer.getValue().toString();
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
        if (kitRequestId != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.KIT_REQUEST_ID, kitRequestId);
        }
        if (kitReasonType != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.KIT_REASON_TYPE, kitReasonType.name());
        }
        if (testResultCode != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.TEST_RESULT_CODE, testResultCode);
        }
        if (testResultTimeCompleted != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.TEST_RESULT_TIME_COMPLETED, testResultTimeCompleted.toString());
        }
        return snapshot;
    }

    public static final class Builder {
        private RenderValueProvider provider;

        public Builder(RenderValueProvider provider) {
            this.provider = cloneProvider(provider);
        }

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

        public Builder setKitRequestId(String kitRequestId) {
            provider.kitRequestId = kitRequestId;
            return this;
        }

        public Builder setKitReasonType(KitReasonType kitReasonType) {
            provider.kitReasonType = kitReasonType;
            return this;
        }

        public Builder setTestResultCode(String testResultCode) {
            provider.testResultCode = testResultCode;
            return this;
        }

        public Builder setTestResultTimeCompleted(Instant testResultTimeCompleted) {
            provider.testResultTimeCompleted = testResultTimeCompleted;
            return this;
        }

        public Builder setActivityInstanceNumber(Integer activityInstanceNumber) {
            provider.activityInstanceNumber = activityInstanceNumber;
            return this;
        }

        /**
         * If caller has a FormResponse object available, then use this.
         */
        public Builder withFormResponse(FormResponse formResponse, FormActivityDef formActivity, String isoLangCode) {
            provider.formResponse = formResponse;
            provider.formActivity = formActivity;
            provider.isoLangCode = isoLangCode;
            return this;
        }

        /**
         * If caller has a FormInstance object available, then use this.
         */
        public Builder withFormInstance(FormInstance formInstance) {
            provider.formInstance = formInstance;
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

            value = snapshot.get(I18nTemplateConstants.Snapshot.KIT_REQUEST_ID);
            if (value != null) {
                provider.kitRequestId = value;
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.KIT_REASON_TYPE);
            if (value != null) {
                provider.kitReasonType = KitReasonType.valueOf(value);
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.TEST_RESULT_CODE);
            if (value != null) {
                provider.testResultCode = value;
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.TEST_RESULT_TIME_COMPLETED);
            if (value != null) {
                provider.testResultTimeCompleted = Instant.parse(value);
            }

            return this;
        }

        public RenderValueProvider build() {
            return cloneProvider(provider);
        }

        private RenderValueProvider cloneProvider(RenderValueProvider provider) {
            RenderValueProvider copy = new RenderValueProvider();
            copy.participantGuid = provider.participantGuid;
            copy.participantFirstName = provider.participantFirstName;
            copy.participantLastName = provider.participantLastName;
            copy.participantBirthDate = provider.participantBirthDate;
            copy.participantTimeZone = provider.participantTimeZone;
            copy.date = provider.date;
            copy.kitRequestId = provider.kitRequestId;
            copy.kitReasonType = provider.kitReasonType;
            copy.testResultCode = provider.testResultCode;
            copy.testResultTimeCompleted = provider.testResultTimeCompleted;
            copy.activityInstanceNumber = provider.activityInstanceNumber;
            copy.formResponse = provider.formResponse;
            copy.formActivity = provider.formActivity;
            copy.isoLangCode = provider.isoLangCode;
            copy.formInstance = provider.formInstance;
            return copy;
        }
    }
}
