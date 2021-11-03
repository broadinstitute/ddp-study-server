package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.util.CollectionMiscUtil.addNonNullsToSet;

import java.io.Serializable;
import java.util.Set;

import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class TextQuestionDto extends QuestionDto implements Serializable {

    private TextInputType inputType;
    private SuggestionType suggestionType;
    private Long placeholderTemplateId;
    private Long confirmPlaceholderTemplateId;
    private boolean confirmEntry;
    private Long confirmPromptTemplateId;
    private Long mismatchMessageTemplateId;

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

    public TextInputType getInputType() {
        return inputType;
    }

    public SuggestionType getSuggestionType() {
        return suggestionType;
    }

    public Long getPlaceholderTemplateId() {
        return placeholderTemplateId;
    }

    public Long getConfirmPlaceholderTemplateId() {
        return confirmPlaceholderTemplateId;
    }

    public boolean isConfirmEntry() {
        return confirmEntry;
    }

    public Long getMismatchMessageTemplateId() {
        return mismatchMessageTemplateId;
    }

    public Long getConfirmPromptTemplateId() {
        return confirmPromptTemplateId;
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
