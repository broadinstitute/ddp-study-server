package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.BooleanRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

@Data
public final class BoolQuestionDef extends QuestionDef {

    @Valid
    @NotNull
    @SerializedName("trueTemplate")
    private Template trueTemplate;

    @Valid
    @NotNull
    @SerializedName("falseTemplate")
    private Template falseTemplate;

    @NotNull
    @SerializedName("renderMode")
    private BooleanRenderMode renderMode;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(final String stableId, final Template prompt,
            final Template trueTemplate, final Template falseTemplate) {
        return new Builder()
                .setStableId(stableId)
                .setPrompt(prompt)
                .setTrueTemplate(trueTemplate)
                .setFalseTemplate(falseTemplate);
    }

    public BoolQuestionDef(final String stableId, final boolean isRestricted,
                            final Template promptTemplate, final Template additionalInfoHeaderTemplate,
                            final Template additionalInfoFooterTemplate, final List<RuleDef> validations,
                            final boolean hideNumber, final boolean writeOnce,
                            @NonNull Template trueTemplate, @NonNull final Template falseTemplate,
                            final BooleanRenderMode renderMode) {
        super(QuestionType.BOOLEAN,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber,
                writeOnce);

        this.trueTemplate = trueTemplate;
        this.falseTemplate = falseTemplate;
        this.renderMode = Optional.ofNullable(renderMode).orElse(BooleanRenderMode.RADIO_BUTTONS);
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private Template trueTemplate;
        private Template falseTemplate;
        private BooleanRenderMode renderMode;

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setTrueTemplate(final Template trueTemplate) {
            this.trueTemplate = trueTemplate;
            return this;
        }

        public Builder setFalseTemplate(final Template falseTemplate) {
            this.falseTemplate = falseTemplate;
            return this;
        }

        public Builder setRenderMode(final BooleanRenderMode renderMode) {
            this.renderMode = renderMode;
            return this;
        }

        public BoolQuestionDef build() {
            BoolQuestionDef question = new BoolQuestionDef(stableId,
                                                            isRestricted,
                                                            prompt,
                                                            getAdditionalInfoHeader(),
                                                            getAdditionalInfoFooter(),
                                                            validations,
                                                            hideNumber,
                                                            writeOnce,
                                                            trueTemplate,
                                                            falseTemplate,
                                                            renderMode);
            configure(question);
            return question;
        }
    }
}
