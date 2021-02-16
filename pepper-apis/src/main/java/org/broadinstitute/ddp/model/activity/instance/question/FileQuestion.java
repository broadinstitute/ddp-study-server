package org.broadinstitute.ddp.model.activity.instance.question;

import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class FileQuestion extends Question<FileAnswer> {

    public FileQuestion(String stableId, long promptTemplateId,
                        boolean isRestricted, boolean isDeprecated,
                        Boolean readonly, Long tooltipTemplateId,
                        Long additionalInfoHeaderTemplateId, Long additionalInfoFooterTemplateId,
                        List<FileAnswer> answers, List<Rule<FileAnswer>> validations) {
        super(QuestionType.FILE, stableId, promptTemplateId, isRestricted, isDeprecated, readonly,
                tooltipTemplateId, additionalInfoHeaderTemplateId, additionalInfoFooterTemplateId,
                answers, validations);
    }

}
