package org.broadinstitute.ddp.model.activity.definition.validation;

import java.lang.reflect.Type;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.MiscUtil;

public abstract class RuleDef {

    @NotNull
    @SerializedName("ruleType")
    protected RuleType ruleType;

    @Valid
    @SerializedName("hintTemplate")
    protected Template hintTemplate;

    @SerializedName("allowSave")
    protected boolean allowSave = false;

    protected transient Long ruleId;

    RuleDef(RuleType ruleType, Template hintTemplate) {
        this.ruleType = MiscUtil.checkNonNull(ruleType, "ruleType");
        this.hintTemplate = hintTemplate;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public Template getHintTemplate() {
        return hintTemplate;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public void setAllowSave(boolean allowSave) {
        this.allowSave = allowSave;
    }

    public boolean getAllowSave() {
        return this.allowSave;
    }

    public static class Deserializer implements JsonDeserializer<RuleDef> {
        @Override
        public RuleDef deserialize(JsonElement elem, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            RuleType ruleType = parseRuleType(elem);
            switch (ruleType) {
                case AGE_RANGE:
                    return ctx.deserialize(elem, AgeRangeRuleDef.class);
                case COMPLETE:
                    return ctx.deserialize(elem, CompleteRuleDef.class);
                case DATE_RANGE:
                    return ctx.deserialize(elem, DateRangeRuleDef.class);
                case DAY_REQUIRED:
                case MONTH_REQUIRED:
                case YEAR_REQUIRED:
                    return ctx.deserialize(elem, DateFieldRequiredRuleDef.class);
                case INT_RANGE:
                    return ctx.deserialize(elem, IntRangeRuleDef.class);
                case LENGTH:
                    return ctx.deserialize(elem, LengthRuleDef.class);
                case NUM_OPTIONS_SELECTED:
                    return ctx.deserialize(elem, NumOptionsSelectedRuleDef.class);
                case REGEX:
                    return ctx.deserialize(elem, RegexRuleDef.class);
                case REQUIRED:
                    return ctx.deserialize(elem, RequiredRuleDef.class);
                default:
                    throw new JsonParseException(String.format("Rule type '%s' is not supported", ruleType));
            }
        }

        private RuleType parseRuleType(JsonElement elem) {
            try {
                return RuleType.valueOf(elem.getAsJsonObject().get("ruleType").getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Could not determine rule type", e);
            }
        }
    }
}
