package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import javax.validation.constraints.NotNull;
import java.util.List;

public final class DynamicSelectQuestionDef extends QuestionDef {

    @NotNull
    @SerializedName("sourceStableIds")
    private List<String> sourceStableIds;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String stableId, Template prompt) {
        return new Builder()
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public DynamicSelectQuestionDef(String stableId, boolean isRestricted, Template promptTemplate,
                                    Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                                    List<RuleDef> validations, List<String> sourceStableIds,
                                    boolean hideNumber, boolean writeOnce) {
        super(QuestionType.DYNAMIC_SELECT,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber,
                writeOnce);
        this.sourceStableIds = sourceStableIds;
    }

    public List<String> getSourceQuestions() {
        return sourceStableIds;
    }

    public void setSourceQuestions(List<String> sourceStableIds) {
        this.sourceStableIds = sourceStableIds;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private List<String> sourceStableIds;

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setSourceQuestions(List<String> sourceStableIds) {
            this.sourceStableIds = sourceStableIds;
            return this;
        }

        public DynamicSelectQuestionDef build() {
            DynamicSelectQuestionDef question = new DynamicSelectQuestionDef(stableId,
                                                            isRestricted,
                                                            prompt,
                                                            getAdditionalInfoHeader(),
                                                            getAdditionalInfoFooter(),
                                                            validations,
                                                            sourceStableIds,
                                                            hideNumber,
                                                            writeOnce);
            configure(question);
            return question;
        }
    }
}
