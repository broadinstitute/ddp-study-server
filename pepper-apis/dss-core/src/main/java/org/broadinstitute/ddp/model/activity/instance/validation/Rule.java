package org.broadinstitute.ddp.model.activity.instance.validation;

import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.MiscUtil;

/**
 * A validation rule that runs against answers for questions.
 *
 * @param <T> the type of answer this rule checks against
 */
public abstract class Rule<T extends Answer> implements Validable<T> {

    @NotNull
    @SerializedName("rule")
    protected RuleType type;

    @NotNull
    @SerializedName("message")
    protected String message;

    @SerializedName("allowSave")
    protected boolean allowSave;

    protected transient Long id;
    protected transient String defaultMessage;
    protected transient String correctionHint;

    Rule(RuleType type, String defaultMessage, String correctionHint, boolean allowSave) {
        this.type = MiscUtil.checkNonNull(type, "type");
        this.defaultMessage = MiscUtil.checkNonNull(defaultMessage, "defaultMessage");
        this.correctionHint = correctionHint;
        this.message = (correctionHint != null ? correctionHint : defaultMessage);
        this.allowSave = allowSave;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public RuleType getRuleType() {
        return type;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public String getCorrectionHint() {
        return correctionHint;
    }

    public boolean getAllowSave() {
        return allowSave;
    }
}
