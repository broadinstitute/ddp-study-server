package org.broadinstitute.ddp.db.dto;

import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.Set;

import static org.broadinstitute.ddp.util.CollectionMiscUtil.addNonNullsToSet;

@Value
public class TextQuestionDto extends QuestionDto implements Serializable {
    TextInputType inputType;
    SuggestionType suggestionType;
    Long placeholderTemplateId;
    Long confirmPlaceholderTemplateId;
    boolean confirmEntry;
    Long confirmPromptTemplateId;
    Long mismatchMessageTemplateId;

    @JdbiConstructor
    public TextQuestionDto(@Nested QuestionDto questionDto,
                           @ColumnName("input_type") TextInputType inputType,
                           @ColumnName("suggestion_type") SuggestionType suggestionType,
                           @ColumnName("placeholder_template_id") Long placeholderTemplateId,
                           @ColumnName("confirm_placeholder_template_id") Long confirmPlaceholderTemplateId,
                           @ColumnName("confirm_entry") boolean confirmEntry,
                           @ColumnName("confirm_prompt_template_id") Long confirmPromptTemplateId,
                           @ColumnName("mismatch_message_template_id") Long mismatchMessageTemplateId) {
        super(questionDto);
        this.inputType = inputType;
        this.suggestionType = suggestionType;
        this.placeholderTemplateId = placeholderTemplateId;
        this.confirmPlaceholderTemplateId = confirmPlaceholderTemplateId;
        this.confirmEntry = confirmEntry;
        this.confirmPromptTemplateId = confirmPromptTemplateId;
        this.mismatchMessageTemplateId = mismatchMessageTemplateId;
    }

    @Override
    public Set<Long> getTemplateIds() {
        return addNonNullsToSet(super.getTemplateIds(),
                placeholderTemplateId,
                confirmPlaceholderTemplateId,
                confirmPromptTemplateId,
                mismatchMessageTemplateId);
    }
}
