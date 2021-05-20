package org.broadinstitute.ddp.model.activity.definition.question;

import java.util.List;
import javax.validation.constraints.Positive;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.interfaces.FileUploadSettings;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class FileQuestionDef extends QuestionDef implements FileUploadSettings {

    @Positive
    @SerializedName("maxFileSize")
    private long maxFileSize;

    @SerializedName("mimeTypes")
    private List<String> mimeTypes;


    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String stableId, Template prompt) {
        return new Builder()
                .setStableId(stableId)
                .setPrompt(prompt);
    }

    public FileQuestionDef(String stableId, boolean isRestricted, Template promptTemplate,
                           Template additionalInfoHeaderTemplate, Template additionalInfoFooterTemplate,
                           List<RuleDef> validations, boolean hideNumber, boolean writeOnce,
                           long maxFileSize, List<String> mimeTypes) {
        super(QuestionType.FILE, stableId, isRestricted, promptTemplate,
                additionalInfoHeaderTemplate, additionalInfoFooterTemplate,
                validations, hideNumber, writeOnce);
        this.maxFileSize = maxFileSize;
        this.mimeTypes = mimeTypes;
    }

    @Override
    public long getMaxFileSize() {
        return maxFileSize;
    }

    @Override
    public List<String> getMimeTypes() {
        return mimeTypes;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private long maxFileSize;
        private List<String> mimeTypes;

        private Builder() {
            // Use static factories.
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setMaxFileSize(Long maxFileSize) {
            this.maxFileSize = maxFileSize;
            return this;
        }

        public Builder setMimeTypes(List<String> mimeTypes) {
            this.mimeTypes = mimeTypes;
            return this;
        }

        public FileQuestionDef build() {
            FileQuestionDef question = new FileQuestionDef(
                    stableId,
                    isRestricted,
                    prompt,
                    getAdditionalInfoHeader(),
                    getAdditionalInfoFooter(),
                    validations,
                    hideNumber,
                    writeOnce,
                    maxFileSize,
                    mimeTypes);
            configure(question);
            return question;
        }
    }
}
