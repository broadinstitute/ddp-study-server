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
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
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

    /**
     * Creates a BoolQuestionDef instance. This constructor may be called directly, or a caller may create an instance
     * through the use of {@link Builder}.
     *
     * @param stableId the question's stable id
     * @param isRestricted <code>true</code> if the question is restricted
     * @param promptTemplate the template to use when presenting the question prompt
     * @param additionalInfoHeaderTemplate the template to use for a text block presented before to the question's main
     *                  content.
     * @param additionalInfoFooterTemplate the template to use for a text block presented after the question's content.
     * @param validations a list of validations any {@link BoolAnswer} must pass in order for the
     *                  question to be considered answered.
     * @param hideNumber <code>true</code> if the question's number should not be displayed by the client
     * @param writeOnce <code>true</code> if the question should be considered read-only after the user has saved an
     *                  answer for the question.
     * @param trueTemplate the template to be used for the true option text. May not be <code>null</code>.
     * @param falseTemplate the template to be used for the false option text. May not be <code>null</code>.
     * @param renderMode the appearance a client should use when presenting this question. If <code>null</code> is
     *                   specified, the default value of <code>BooleanRenderMode.RADIO_BUTTONS</code> is used
     */
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
        /*
            A default value is provided here with the intention of easing the migration of currently designed studies which
            do not have this value, keeping `renderMode` well-defined inside the rest of the `DSS` backend. This works
            in conjunction with the Liquibase migration which sets a default value of existing BoolQuestionDef's renderMode
            in the database.
         */
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
