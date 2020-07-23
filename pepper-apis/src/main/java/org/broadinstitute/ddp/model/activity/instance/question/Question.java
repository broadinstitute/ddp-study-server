package org.broadinstitute.ddp.model.activity.instance.question;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.MiscUtil;

public abstract class Question<T extends Answer> implements Renderable {

    @NotNull
    @SerializedName("questionType")
    protected QuestionType questionType;

    @NotBlank
    @SerializedName("stableId")
    protected String stableId;

    @NotNull
    @SerializedName("prompt")
    protected String prompt;

    @NotNull
    @SerializedName("textPrompt")
    protected String textPrompt;

    @SerializedName("tooltip")
    protected String tooltip;

    @SerializedName("readonly")
    private Boolean readonly;

    @Nullable
    @SerializedName("additionalInfoHeader")
    protected String additionalInfoHeader;

    @Nullable
    @SerializedName("additionalInfoFooter")
    protected String additionalInfoFooter;

    @NotNull
    @SerializedName("answers")
    protected List<T> answers = new ArrayList<>();

    // Important: we store this using the raw type instead of the parameterized `Rule<T>`. Gson seems to have trouble
    // with generic types, so this helps facilitate serialization. The public interface is kept parameterized so we
    // can leverage type safety.
    @NotNull
    @SerializedName("validations")
    protected List<Rule> validations = new ArrayList<>();

    @SerializedName("validationFailures")
    protected List<ActivityValidationFailure> activityValidationFailures;

    protected transient long questionId;
    protected transient boolean isRestricted;
    protected transient boolean isDeprecated;
    protected transient long promptTemplateId;
    protected transient Long tooltipTemplateId;
    protected transient Long additionalInfoHeaderTemplateId;
    protected transient Long additionalInfoFooterTemplateId;
    private transient boolean shouldHideQuestionNumber;

    public Question(QuestionType questionType, String stableId, long promptTemplateId,
                    boolean isRestricted, boolean isDeprecated, Boolean readonly, Long tooltipTemplateId,
                    Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
                    List<T> answers, List<Rule<T>> validations) {
        this(questionType, stableId, promptTemplateId, answers, validations);
        this.isRestricted = isRestricted;
        this.isDeprecated = isDeprecated;
        this.readonly = readonly;
        this.tooltipTemplateId = tooltipTemplateId;
        this.additionalInfoHeaderTemplateId = additionalInfoHeaderTemplateId;
        this.additionalInfoFooterTemplateId = additionalInfoFooterTemplateId;
    }

    public Question(QuestionType questionType, String stableId, long promptTemplateId,
                    List<T> answers, List<Rule<T>> validations) {
        this.questionType = MiscUtil.checkNonNull(questionType, "questionType");
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.promptTemplateId = promptTemplateId;
        setAnswers(answers);
        setValidations(validations);
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public String getStableId() {
        return stableId;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getTextPrompt() {
        return textPrompt;
    }

    public String getTooltip() {
        return tooltip;
    }

    public String getAdditionalInfoHeader() {
        return additionalInfoHeader;
    }

    public String getAdditionalInfoFooter() {
        return additionalInfoFooter;
    }
    
    public List<T> getAnswers() {
        return answers;
    }

    public void setAnswers(List<T> answers) {
        if (answers != null) {
            this.answers = answers;
        }
    }

    public List<Rule<T>> getValidations() {
        return (List) validations;
    }

    public void setValidations(List<Rule<T>> validations) {
        if (validations != null) {
            this.validations = (List) validations;
        }
    }

    public long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(long questionId) {
        this.questionId = questionId;
    }

    public boolean isRestricted() {
        return isRestricted;
    }

    public void setRestricted(boolean restricted) {
        isRestricted = restricted;
    }

    public boolean isDeprecated() {
        return isDeprecated;
    }

    public void setDeprecated(boolean deprecated) {
        isDeprecated = deprecated;
    }

    public long getPromptTemplateId() {
        return promptTemplateId;
    }

    public Long getTooltipTemplateId() {
        return tooltipTemplateId;
    }

    public Boolean getReadonly() {
        return readonly;
    }

    public void makeReadonly() {
        this.readonly = true;
    }

    public void setActivityValidationFailures(List<ActivityValidationFailure> activityValidationFailures) {
        this.activityValidationFailures = activityValidationFailures;
    }

    /**
     * Run through validations that are not checked on initial save
     *
     * @return true if everything valid, otherwise false
     */
    public boolean passesDeferredValidations() {
        return passesDeferredValidations(answers);
    }

    public boolean passesDeferredValidations(List<T> answerValues) {
        return requiredRulesPass(answerValues) && allowSaveRulesPass(answerValues);
    }

    protected boolean allowSaveRulesPass(List<T> answerValues) {
        List<Rule> rulesToCheck = validations.stream().filter(rule -> rule.getAllowSave()).collect(toList());
        if (rulesToCheck.isEmpty()) {
            return true;
        } else {
            return answerValues.stream()
                    .noneMatch(answer ->
                            rulesToCheck.stream()
                                    .anyMatch(rule -> !rule.validate(this, answer))
                    );
        }
    }

    protected boolean requiredRulesPass(List<T> answerValues) {
        Optional<Rule> res = validations.stream().filter(rule -> rule.getRuleType().equals(RuleType.REQUIRED)).findFirst();
        if (res.isPresent()) {
            RequiredRule<T> required = (RequiredRule<T>) res.get();
            if (answerValues.isEmpty()) {
                return false;
            }
            for (T answer : answerValues) {
                if (!required.validate(this, answer)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Question is complete if it has at least one answer
     *
     * @return true if complete, otherwise false
     */
    public boolean isAnswered() {
        return !getAnswers().isEmpty();
    }

    /**
     * Determines if question is required or not.
     *
     * @return true if required, otherwise false
     */
    public boolean isRequired() {
        return validations.stream().anyMatch(rule -> rule.getRuleType().equals(RuleType.REQUIRED));
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        registry.accept(promptTemplateId);

        if (tooltipTemplateId != null) {
            registry.accept(tooltipTemplateId);
        }

        if (additionalInfoHeaderTemplateId != null) {
            registry.accept(additionalInfoHeaderTemplateId);
        }

        if (additionalInfoFooterTemplateId != null) {
            registry.accept(additionalInfoFooterTemplateId);
        }
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        prompt = rendered.get(promptTemplateId);
        if (prompt == null) {
            throw new NoSuchElementException("No rendered template found for prompt with id " + promptTemplateId);
        }

        textPrompt = HtmlConverter.getPlainText(prompt);
        if (style == ContentStyle.BASIC) {
            prompt = HtmlConverter.getSimpleText(prompt);
        }

        if (tooltipTemplateId != null) {
            tooltip = HtmlConverter.getPlainText(rendered.get(tooltipTemplateId));
            if (tooltip == null) {
                throw new NoSuchElementException("No rendered template found for tooltip with id " + tooltipTemplateId);
            }
        }

        if (additionalInfoHeaderTemplateId != null) {
            additionalInfoHeader = rendered.get(additionalInfoHeaderTemplateId);
        }

        if (additionalInfoFooterTemplateId != null) {
            additionalInfoFooter = rendered.get(additionalInfoFooterTemplateId);
        }
    }

    public void shouldHideQuestionNumber(boolean shouldHideQuestionNumber) {
        this.shouldHideQuestionNumber = shouldHideQuestionNumber;
    }

    public boolean shouldHideQuestionNumber() {
        return shouldHideQuestionNumber;
    }
}
