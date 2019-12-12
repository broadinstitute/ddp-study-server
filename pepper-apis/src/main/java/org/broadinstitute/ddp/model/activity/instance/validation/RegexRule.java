package org.broadinstitute.ddp.model.activity.instance.validation;

import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.MiscUtil;

/**
 * A validation rule on text answer that matches against a regex pattern.
 * Expects the regex to follow Java's regex syntax.
 */
public class RegexRule extends Rule<TextAnswer> {

    @NotNull
    @SerializedName("regexPattern")
    private String pattern;

    /**
     * Instantiates RegexRule object with id.
     */
    public static RegexRule of(Long id, String message, String hint, boolean allowSave, String pattern) {
        RegexRule rule = RegexRule.of(message, hint, allowSave, pattern);
        rule.setId(id);
        return rule;
    }

    /**
     * Instantiates RegexRule object.
     */
    public static RegexRule of(String message, String hint, boolean allowSave, String pattern) {
        MiscUtil.checkNonNull(pattern, "pattern");
        MiscUtil.checkRegexPattern(pattern);
        return new RegexRule(message, hint, allowSave, pattern);
    }

    private RegexRule(String message, String hint, boolean allowSave, String pattern) {
        super(RuleType.REGEX, message, hint, allowSave);
        this.pattern = pattern;
    }

    @Override
    public boolean validate(Question<TextAnswer> question, TextAnswer answer) {
        return answer != null && answer.getValue() != null && answer.getValue().matches(pattern);
    }

    public String getPattern() {
        return pattern;
    }
}
