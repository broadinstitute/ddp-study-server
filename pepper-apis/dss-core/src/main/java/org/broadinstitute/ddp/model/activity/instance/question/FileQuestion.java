package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.interfaces.FileUploadSettings;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class FileQuestion extends Question<FileAnswer> implements FileUploadSettings {

    @SerializedName("maxFileSize")
    private long maxFileSize;

    @NotNull
    @SerializedName("mimeTypes")
    private Set<String> mimeTypes;
    
    public FileQuestion(String stableId, long promptTemplateId,
                        boolean isRestricted, boolean isDeprecated,
                        Boolean readonly, Long tooltipTemplateId,
                        Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
                        List<FileAnswer> answers, List<Rule<FileAnswer>> validations,
                        long maxFileSize, Set<String> mimeTypes) {
        super(QuestionType.FILE, stableId, promptTemplateId, isRestricted, isDeprecated, readonly,
                tooltipTemplateId, additionalInfoHeaderTemplateId, additionalInfoFooterTemplateId,
                answers, validations);
        this.maxFileSize = maxFileSize;
        this.mimeTypes = mimeTypes;
    }

    @Override
    public long getMaxFileSize() {
        return maxFileSize;
    }

    @Override
    public Set<String> getMimeTypes() {
        return mimeTypes;
    }
}
