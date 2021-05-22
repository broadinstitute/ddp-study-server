package org.broadinstitute.ddp.model.activity.definition.question;

import static org.broadinstitute.ddp.constants.ConfigFile.FileUploads.MAX_FILE_SIZE_BYTES;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.Positive;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.interfaces.FileUploadSettings;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.ConfigManager;

public final class FileQuestionDef extends QuestionDef implements FileUploadSettings {

    @Positive
    @SerializedName("maxFileSize")
    private long maxFileSize;

    @SerializedName("mimeTypes")
    private Set<String> mimeTypes;


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
                           long maxFileSize, Set<String> mimeTypes) {
        super(QuestionType.FILE, stableId, isRestricted, promptTemplate,
                additionalInfoHeaderTemplate, additionalInfoFooterTemplate,
                validations, hideNumber, writeOnce);
        String validationError = validateFileMaxSize(maxFileSize);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }
        this.maxFileSize = maxFileSize;
        this.mimeTypes = mimeTypes == null ? new HashSet<>() : mimeTypes;
    }

    @Override
    public long getMaxFileSize() {
        return maxFileSize;
    }

    @Override
    public Collection<String> getMimeTypes() {
        return mimeTypes;
    }

    private static String validateFileMaxSize(long maxFileSize) {
        Long maxFileSizeConf = ConfigManager.getInstance().getConfig().getLong(MAX_FILE_SIZE_BYTES);
        String errorMessage = "Invalid value of maxFileSize=" + maxFileSize + ". ";
        if (maxFileSize <= 0) {
            return errorMessage + "It should be greater than 0.";
        } else if (maxFileSizeConf != null && maxFileSize > maxFileSizeConf) {
            return errorMessage + "It should not exceed config value=" + maxFileSizeConf + ".";
        }
        return null;
    }

    public static final class Builder extends AbstractQuestionBuilder<Builder> {

        private long maxFileSize;
        private Set<String> mimeTypes;

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

        public Builder setMimeTypes(Set<String> mimeTypes) {
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
