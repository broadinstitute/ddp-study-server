package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class FileQuestion extends Question<FileAnswer> {

    @SerializedName("maxFileSize")
    private long maxFileSize;

    @SerializedName("mimeTypes")
    private List<String> mimeTypes;
    
    public FileQuestion(String stableId, long promptTemplateId,
                        boolean isRestricted, boolean isDeprecated,
                        Boolean readonly, Long tooltipTemplateId,
                        Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
                        List<FileAnswer> answers, List<Rule<FileAnswer>> validations,
                        long maxFileSize, List<String> mimeTypes) {
        super(QuestionType.FILE, stableId, promptTemplateId, isRestricted, isDeprecated, readonly,
                tooltipTemplateId, additionalInfoHeaderTemplateId, additionalInfoFooterTemplateId,
                answers, validations);
        this.maxFileSize = maxFileSize;
        this.mimeTypes = mimeTypes;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }
}
