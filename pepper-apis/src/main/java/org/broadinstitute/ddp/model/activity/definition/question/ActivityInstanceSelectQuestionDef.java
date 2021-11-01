package org.broadinstitute.ddp.model.activity.definition.question;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import javax.validation.constraints.NotNull;
import java.util.List;

public final class ActivityInstanceSelectQuestionDef extends QuestionDef {

    @NotNull
    @SerializedName("activityCodes")
    private List<String> activityCodes;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String stableId, Template prompt) {
        return new Builder()
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public ActivityInstanceSelectQuestionDef(String stableId, boolean isRestricted, Template promptTemplate,
                                             Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                                             List<RuleDef> validations, List<String> activityCodes,
                                             boolean hideNumber, boolean writeOnce) {
        super(QuestionType.ACTIVITY_INSTANCE_SELECT,
                stableId,
                isRestricted,
                promptTemplate,
                additionalInfoHeaderTemplate,
                additionalInfoFooterTemplate,
                validations,
                hideNumber,
                writeOnce);
        this.activityCodes = activityCodes;
    }

    public List<String> getActivityCodes() {
        return activityCodes;
    }

    public void setActivityCodes(List<String> activityCodes) {
        this.activityCodes = activityCodes;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private List<String> activityCodes;

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setActivityCodes(List<String> activityCodes) {
            this.activityCodes = activityCodes;
            return this;
        }

        public ActivityInstanceSelectQuestionDef build() {
            ActivityInstanceSelectQuestionDef question = new ActivityInstanceSelectQuestionDef(stableId,
                                                            isRestricted,
                                                            prompt,
                                                            getAdditionalInfoHeader(),
                                                            getAdditionalInfoFooter(),
                                                            validations,
                                                            activityCodes,
                                                            hideNumber,
                                                            writeOnce);
            configure(question);
            return question;
        }
    }
}
