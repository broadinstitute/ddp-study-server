package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class BoolQuestionDef extends QuestionDef {

    @Valid
    @NotNull
    @SerializedName("trueTemplate")
    private Template trueTemplate;

    @Valid
    @NotNull
    @SerializedName("falseTemplate")
    private Template falseTemplate;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String stableId, Template prompt, Template trueTemplate, Template falseTemplate) {
        return new Builder()
                .setStableId(stableId)
                .setPrompt(prompt)
                .setTrueTemplate(trueTemplate)
                .setFalseTemplate(falseTemplate);
    }

    public BoolQuestionDef(String stableId, boolean isRestricted, Template promptTemplate,
                           Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                           List<RuleDef> validations, Template trueTemplate, Template falseTemplate,
                           boolean hideNumber) {
        super(QuestionType.BOOLEAN,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber);

        this.trueTemplate = MiscUtil.checkNonNull(trueTemplate, "trueTemplate");
        this.falseTemplate = MiscUtil.checkNonNull(falseTemplate, "falseTemplate");
    }

    public Template getTrueTemplate() {
        return trueTemplate;
    }

    public Template getFalseTemplate() {
        return falseTemplate;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private Template trueTemplate;
        private Template falseTemplate;

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setTrueTemplate(Template trueTemplate) {
            this.trueTemplate = trueTemplate;
            return this;
        }

        public Builder setFalseTemplate(Template falseTemplate) {
            this.falseTemplate = falseTemplate;
            return this;
        }

        public BoolQuestionDef build() {
            BoolQuestionDef question = new BoolQuestionDef(stableId,
                                                            isRestricted,
                                                            prompt,
                                                            getAdditonalInfoHeader(),
                                                            getAdditonalInfoFooter(),
                                                            validations,
                                                            trueTemplate,
                                                            falseTemplate,
                                                            hideNumber);
            configure(question);
            return question;
        }
    }
}
