package org.broadinstitute.ddp.content;

import static org.broadinstitute.ddp.content.SelectedPickListOptionRenderUtil.selectedOptionsRender;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.dsm.KitReasonType;
import org.broadinstitute.ddp.model.dsm.TestResult;

/**
 * Provides methods that can be called within templates to get certain values from the system.
 */
@Slf4j
public class RenderValueProvider implements Cloneable {
    /**
     * If this value is `true` then  DDP methods (answer(), isGovernedParticipant()) defined in {@link RenderValueProvider} will
     * return pre-defined values:
     * <pre>
     *     - isGovernedParticipant() returns both parameters separated with slash (`isTrueString`/`isFalseString`);
     *     - answer() returns fallbackValue.
     * </pre>
     */
    private boolean useDefaultsForDdpMethods = false;

    private String participantGuid;
    private String participantFirstName;
    private String participantLastName;
    private LocalDate participantBirthDate;
    private ZoneId participantTimeZone;
    private LocalDate firstCompletedDate;
    private LocalDate date;
    private String kitRequestId;
    private KitReasonType kitReasonType;
    private String testResultCode;
    private Instant testResultTimeCompleted;
    private Integer activityInstanceNumber;
    private Boolean governedParticipant;
    private String addressGuid;

    // To minimize database round-trips, we lookup answers using existing objects that should have the answer objects.
    // Depending on what's available for the provider, we'll use either response + activity or the instance object.
    private FormResponse formResponse;
    private FormActivityDef formActivity;
    private String isoLangCode;
    private FormInstance formInstance;

    private RenderValueProvider() {
        // Use builder.
    }

    @Override
    public RenderValueProvider clone() throws CloneNotSupportedException {
        var newProvider = (RenderValueProvider)super.clone();

        newProvider.participantGuid = participantGuid;
        newProvider.participantFirstName = participantFirstName;
        newProvider.participantLastName = participantLastName;
        newProvider.participantBirthDate = participantBirthDate;
        newProvider.participantTimeZone = participantTimeZone;
        newProvider.firstCompletedDate = firstCompletedDate;
        newProvider.date = date;
        newProvider.kitRequestId = kitRequestId;
        newProvider.kitReasonType = kitReasonType;
        newProvider.testResultCode = testResultCode;
        newProvider.testResultTimeCompleted = testResultTimeCompleted;
        newProvider.activityInstanceNumber = activityInstanceNumber;
        newProvider.formResponse = formResponse;
        newProvider.formActivity = formActivity;
        newProvider.isoLangCode = isoLangCode;
        newProvider.formInstance = formInstance;
        newProvider.governedParticipant = governedParticipant;
        newProvider.addressGuid = addressGuid;
        newProvider.useDefaultsForDdpMethods = useDefaultsForDdpMethods;
        return newProvider;
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
            log.warn("Error formatting participant birth date value '{}' using format '{}'", participantBirthDate, format, e);
            return participantBirthDate.toString();
        }
    }

    /**
     * If the current activity has been previously completed, returns the date of completion.
     *
     * <p>See {@link java.time.format.DateTimeFormatter#ofPattern(String)} for more information
     * about supported format strings.
     * @param format The date format string to use.
     * @param defaultValue The string to return if there is no valid date
     * @return the formatted date of first completion, or `defaultValue` if one does not exist
     */
    public String firstCompletedDate(String format, String defaultValue) {
        if (firstCompletedDate == null) {
            return defaultValue;
        }

        try {
            return DateTimeFormatter.ofPattern(format).format(firstCompletedDate);
        } catch (Exception e) {
            log.warn("Error formatting submission date value '{}' using format '{}'", firstCompletedDate, format, e);
            return firstCompletedDate.toString();
        }
    }

    /**
     * Returns today's date in given format. Might return a snapshot-ed date.
     */
    public String date(String format) {
        try {
            return DateTimeFormatter.ofPattern(format).format(date);
        } catch (Exception e) {
            log.warn("Error formatting date value '{}' using format '{}'", date, format, e);
            return date.toString();
        }
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setFirstCompletedDate(LocalDate date) {
        this.firstCompletedDate = date;
    }

    public void setUseDefaultsForDdpMethods(boolean useDefaultsForDdpMethods) {
        this.useDefaultsForDdpMethods = useDefaultsForDdpMethods;
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
            log.warn("Error formatting test result time completed value '{}' using format '{}'", testResultTimeCompleted, format, e);
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
     * Select a string depending on the type of the current participant: if he/she a governed participant or
     *
     * @return String - if governedParticipant is not null and == true, then return 'ifTrueString', else return 'ifFalseString'
     */
    public String isGovernedParticipant(String ifTrueString, String ifFalseString) {
        if (governedParticipant == null && useDefaultsForDdpMethods) {
            return ifTrueString + '/' + ifFalseString;
        } else {
            return governedParticipant != null && governedParticipant ? ifTrueString : ifFalseString;
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
     * Returns a string value depending on whether a specified PICKLIST question's option
     * is selected or not.
     *
     * <p>If {@link useDefaultsForDdpMethods} is true, this method will always return
     * a value of the form "stringIfMatches/stringOtherwise".
     *
     * <p>If an activity does not have a response,
     * or the question does not have an associated answer, a match will be assumed,
     * and the `stringIfMatches` value will be returned.
     * @param questionStableId  the stable id of the PICKLIST question
     * @param optionStableId    the stable id of the option to check
     * @param stringIfMatches    string to return if the option is selected, or there is no answer
     * @param stringOtherwise    string to return if the option is not selected
     * @return the string value based on the option's selected state
     */
    public String checkAnswer(String questionStableId, String optionStableId,
                              String stringIfMatches, String stringOtherwise) {
        //only used during elastic export
        if (useDefaultsForDdpMethods) {
            return String.format("%s/%s", stringIfMatches, stringOtherwise);
        }

        // Defaulting to `true` is intentional- a null response or answer
        // is assumed to be "selected"
        // (bskinner 2022.08.25)
        var optionIsSelected = true;

        if (formResponse != null) {
            var answer = formResponse.getAnswer(questionStableId);

            if (answer != null) {
                if (answer.getQuestionType() != QuestionType.PICKLIST) {
                    var message = String.format("Only %s answers are supported by the `checkAnswer` macro [activity:%s,question:%s]",
                            QuestionType.PICKLIST,
                            formResponse.getActivityCode(),
                            questionStableId);
                    throw new DDPException(message);
                }

                optionIsSelected = ((PicklistAnswer) answer).getValue()
                        .stream()
                        .anyMatch((selected) -> selected.getStableId().equals(optionStableId));
            }
        }

        if (optionIsSelected) {
            return stringIfMatches;
        } else {
            return stringOtherwise;
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
     * @return answer string representation of answer (in case of picklist: option) if formResponse
     *     or formInstance objects are available, otherwise null
     */
    public String answer(String questionStableId, String fallbackValue) {
        return answer(questionStableId, fallbackValue, false);
    }

    /**
     * Returns the answer for the question, or the fallback value if no answer.
     *
     * @param questionStableId         the question stable id
     * @param fallbackValue            the fallback value
     * @param useDetailTextForPickList if true and detecting answer of a picklist then get a detailText instead of
     *                                 option label.
     * @return answer string representation of answer (in case of picklist: option or detailText) if formResponse
     *     or formInstance objects are available, otherwise null
     * @see #answer(String, String)
     */
    public String answer(String questionStableId, String fallbackValue, boolean useDetailTextForPickList) {
        if (formResponse != null) {
            return renderAnswerUsingFormResponse(questionStableId, fallbackValue, useDetailTextForPickList);
        } else if (formInstance != null) {
            return renderAnswerUsingFormInstance(questionStableId, fallbackValue, useDetailTextForPickList);
        } else {
            if (useDefaultsForDdpMethods) {
                return fallbackValue;
            } else {
                // No objects to use to lookup answers. Returning null here will keep this part of the template untouched,
                // in case we want to come back and do a second round of rendering.
                return null;
            }
        }
    }

    private String renderAnswerUsingFormResponse(String questionStableId, String fallbackValue, boolean useDetailTextForPickList) {
        QuestionDef questionDef = formActivity.getQuestionByStableId(questionStableId);
        Answer answer = formResponse.getAnswer(questionStableId);
        if (answer == null || answer.isEmpty()) {
            // No answer response for this question yet, so use fallback.
            return fallbackValue;
        }
        switch (answer.getQuestionType()) {
            case PICKLIST:
                return selectedOptionsRender(questionDef, answer, isoLangCode, useDetailTextForPickList);
            case MATRIX: // Fall-through
            case COMPOSITE: // Fall-through
            case FILE:
                // Have not decided what composite or file answers will look like yet.
                throw new DDPException("Rendering answer type " + answer.getQuestionType() + " is currently not supported");
            default:
                // Everything else will get turned into a string.
                return answer.getValue().toString();
        }
    }

    /**
     * Render template with setting an answer value instead of $ddp.answer(...).
     * NOTE: in order to avoid NPE replace null values of getDetailLabel() or getOptionLabel() with `fallbackValue`.
     *
     * @param questionStableId         stable ID of a question which answer to render
     * @param fallbackValue            default value which set in case if answer not set or empty
     * @param useDetailTextForPickList if it is `true` then use detailText() (instead of option) in case of picklist.
     * @return String - rendered template
     */
    private String renderAnswerUsingFormInstance(String questionStableId, String fallbackValue, boolean useDetailTextForPickList) {
        Question question = formInstance.getQuestionByStableId(questionStableId);
        Answer answer = question != null && question.isAnswered() ? (Answer) question.getAnswers().get(0) : null;
        if (answer == null || answer.isEmpty()) {
            return fallbackValue;
        }
        switch (answer.getQuestionType()) {
            case PICKLIST:
                return selectedOptionsRender(question, answer, useDetailTextForPickList);
            case MATRIX: // Fall-through
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
        if (firstCompletedDate != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.DDP_FIRST_COMPLETED_DATE, firstCompletedDate.toString());
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
        if (governedParticipant != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.IS_GOVERNED_PARTICIPANT, governedParticipant.toString());
        }
        if (addressGuid != null) {
            snapshot.put(I18nTemplateConstants.Snapshot.ADDRESS_GUID, addressGuid);
        }
        return snapshot;
    }

    @AllArgsConstructor
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

        public Builder setFirstCompletedDate(LocalDate date) {
            provider.firstCompletedDate = date;
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

        public Builder setGovernedParticipant(Boolean governedParticipant) {
            provider.governedParticipant = governedParticipant;
            return this;
        }

        public Builder setAddressGuid(String addressGuid) {
            provider.addressGuid = addressGuid;
            return this;
        }

        public Builder setUseDefaultsForDdpMethods(boolean useDefaultsForDdpMethods) {
            provider.useDefaultsForDdpMethods = useDefaultsForDdpMethods;
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

            value = snapshot.get(I18nTemplateConstants.Snapshot.DDP_FIRST_COMPLETED_DATE);
            if (value != null) {
                provider.firstCompletedDate = LocalDate.parse(value);
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

            value = snapshot.get(I18nTemplateConstants.Snapshot.IS_GOVERNED_PARTICIPANT);
            if (value != null) {
                provider.governedParticipant = Boolean.valueOf(value);
            }

            value = snapshot.get(I18nTemplateConstants.Snapshot.ADDRESS_GUID);
            if (value != null) {
                provider.addressGuid = value;
            }

            return this;
        }

        public RenderValueProvider build() {
            RenderValueProvider newProvider = null;

            try {
                newProvider = (RenderValueProvider)provider.clone();
            } catch (CloneNotSupportedException cloneException) {
                // As of writing this comment, RenderValueProvider completely implements
                // clone() and there shouldn't be any cases where a CloneNotSupportedException
                // is thrown. That said, handle it just in case something changes.
                // (bskinner - 2022.09.12)
                var message = String.format("Failed to clone instance of %s", this.getClass().getName());
                throw new DDPException(message, cloneException);
            }

            return newProvider;
        }
    }
}
