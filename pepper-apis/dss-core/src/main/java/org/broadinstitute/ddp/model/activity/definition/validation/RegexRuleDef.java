package org.broadinstitute.ddp.model.activity.definition.validation;

import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class RegexRuleDef extends RuleDef {

    @NotNull
    @SerializedName("pattern")
    private String pattern;

    public RegexRuleDef(Template hintTemplate, String pattern) {
        super(RuleType.REGEX, hintTemplate);
        MiscUtil.checkNonNull(pattern, "pattern");
        MiscUtil.checkRegexPattern(pattern);
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }
}
