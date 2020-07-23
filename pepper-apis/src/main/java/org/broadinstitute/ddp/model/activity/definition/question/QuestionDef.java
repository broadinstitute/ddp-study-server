package org.broadinstitute.ddp.model.activity.definition.question;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

public abstract class QuestionDef {

    @NotNull
    @SerializedName("questionType")
    protected QuestionType questionType;

    @NotBlank
    @SerializedName("stableId")
    protected String stableId;

    @SerializedName("isRestricted")
    protected boolean isRestricted;

    @SerializedName("isDeprecated")
    protected boolean isDeprecated;

    @Valid
    @NotNull
    @SerializedName("promptTemplate")
    protected Template promptTemplate;

    @Valid
    @SerializedName("tooltipTemplate")
    protected Template tooltipTemplate;

    @Valid
    @Nullable
    @SerializedName("additionalInfoHeaderTemplate")
    protected Template additionalInfoHeaderTemplate;

    @Valid
    @Nullable
    @SerializedName("additionalInfoFooterTemplate")
    protected Template additionalInfoFooterTemplate;

    @NotNull
    @SerializedName("validations")
    protected List<@Valid @NotNull RuleDef> validations;

    @SerializedName("hideNumber")
    private boolean hideNumber;

    @SerializedName("writeOnce")
    protected boolean writeOnce;

    protected transient Long questionId;

    protected QuestionDef(QuestionType questionType, String stableId, boolean isRestricted,
                          Template promptTemplate, Template additionalInfoHeaderTemplate,
                          Template additionalInfoFooterTemplate, List<RuleDef> validations,
                          boolean hideNumber, boolean writeOnce) {
        this.questionType = MiscUtil.checkNonNull(questionType, "questionType");
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.promptTemplate = MiscUtil.checkNonNull(promptTemplate, "promptTemplate");
        this.additionalInfoHeaderTemplate = additionalInfoHeaderTemplate;
        this.additionalInfoFooterTemplate = additionalInfoFooterTemplate;
        this.isRestricted = isRestricted;
        this.validations = MiscUtil.checkNonNull(validations, "validations");
        this.hideNumber = hideNumber;
        this.writeOnce = writeOnce;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public String getStableId() {
        return stableId;
    }

    public boolean isRestricted() {
        return isRestricted;
    }

    public boolean isDeprecated() {
        return isDeprecated;
    }

    public Template getPromptTemplate() {
        return promptTemplate;
    }

    public Template getTooltipTemplate() {
        return tooltipTemplate;
    }

    @Nullable
    public Template getAdditionalInfoHeaderTemplate() {
        return additionalInfoHeaderTemplate;
    }

    @Nullable
    public Template getAdditionalInfoFooterTemplate() {
        return additionalInfoFooterTemplate;
    }

    public List<RuleDef> getValidations() {
        return validations;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public boolean shouldHideNumber() {
        return hideNumber;
    }

    public boolean isWriteOnce() {
        return writeOnce;
    }

    public void setWriteOnce(boolean writeOnce) {
        this.writeOnce = writeOnce;
    }

    /**
     * Builder that helps construct common elements of a question block.
     *
     * @param <T> Type of the subclass builder
     */
    protected abstract static class AbstractQuestionBuilder<T extends AbstractQuestionBuilder<T>> {

        protected String stableId;
        protected Template prompt;
        protected Template tooltip;
        private Template additionalInfoHeader;
        private Template additionalInfoFooter;
        protected boolean hideNumber;
        protected boolean writeOnce;

        protected Long questionId = null;
        protected boolean isRestricted = false;
        protected boolean isDeprecated = false;
        protected List<RuleDef> validations = new ArrayList<>();

        /**
         * Returns the subclass builder instance to enable method chaining.
         */
        protected abstract T self();

        /**
         * Configure the base properties of a question.
         *
         * @param question the question to configure
         */
        protected void configure(QuestionDef question) {
            question.setQuestionId(questionId);
            question.isDeprecated = isDeprecated;
            question.tooltipTemplate = tooltip;
        }

        public T setStableId(String stableId) {
            this.stableId = stableId;
            return self();
        }

        public T setHideNumber(boolean hideNumber) {
            this.hideNumber = hideNumber;
            return self();
        }

        public T setWriteOnce(boolean writeOnce) {
            this.writeOnce = writeOnce;
            return self();
        }

        public T setPrompt(Template prompt) {
            this.prompt = prompt;
            return self();
        }

        public T setTooltip(Template tooltip) {
            this.tooltip = tooltip;
            return self();
        }

        protected Template getAdditionalInfoHeader() {
            return additionalInfoHeader;
        }

        protected Template getAdditionalInfoFooter() {
            return additionalInfoFooter;
        }

        public T setAdditionalInfoHeader(Template additionalInfoHeader) {
            this.additionalInfoHeader = additionalInfoHeader;
            return self();
        }

        public T setAdditionalInfoFooter(Template additionalInfoFooter) {
            this.additionalInfoFooter = additionalInfoFooter;
            return self();
        }

        public T setQuestionId(Long questionId) {
            this.questionId = questionId;
            return self();
        }

        public T setRestricted(boolean restricted) {
            isRestricted = restricted;
            return self();
        }

        public T setDeprecated(boolean deprecated) {
            isDeprecated = deprecated;
            return self();
        }

        public T addValidation(RuleDef validation) {
            this.validations.add(validation);
            return self();
        }

        public T addValidations(Collection<RuleDef> validations) {
            this.validations.addAll(validations);
            return self();
        }

        public T clearValidations() {
            this.validations.clear();
            return self();
        }
    }

    public static class Deserializer implements JsonDeserializer<QuestionDef> {
        @Override
        public QuestionDef deserialize(JsonElement elem, Type type, JsonDeserializationContext ctx) throws JsonParseException {
            QuestionType questionType = parseQuestionType(elem);
            switch (questionType) {
                case AGREEMENT:
                    return ctx.deserialize(elem, AgreementQuestionDef.class);
                case BOOLEAN:
                    return ctx.deserialize(elem, BoolQuestionDef.class);
                case TEXT:
                    return ctx.deserialize(elem, TextQuestionDef.class);
                case DATE:
                    return ctx.deserialize(elem, DateQuestionDef.class);
                case NUMERIC:
                    return ctx.deserialize(elem, NumericQuestionDef.class);
                case PICKLIST:
                    return ctx.deserialize(elem, PicklistQuestionDef.class);
                case COMPOSITE:
                    return ctx.deserialize(elem, CompositeQuestionDef.class);
                default:
                    throw new JsonParseException(String.format("Question type '%s' is not supported", questionType));
            }
        }

        private QuestionType parseQuestionType(JsonElement elem) {
            try {
                return QuestionType.valueOf(elem.getAsJsonObject().get("questionType").getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Could not determine question type", e);
            }
        }
    }
}
