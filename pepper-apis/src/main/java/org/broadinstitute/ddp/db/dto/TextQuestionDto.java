package org.broadinstitute.ddp.db.dto;

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
    private boolean confirmEntry;
    private Long confirmPromptTemplateId;
    private Long mismatchMessageTemplateId;

    @JdbiConstructor
    public TextQuestionDto(@Nested QuestionDto questionDto,
                           @ColumnName("input_type") TextInputType inputType,
                           @ColumnName("suggestion_type") SuggestionType suggestionType,
                           @ColumnName("placeholder_template_id") Long placeholderTemplateId,
                           @ColumnName("confirm_entry") boolean confirmEntry,
                           @ColumnName("confirm_prompt_template_id") Long confirmPromptTemplateId,
                           @ColumnName("mismatch_message_template_id") Long mismatchMessageTemplateId) {
        super(questionDto);
        this.inputType = inputType;
        this.suggestionType = suggestionType;
        this.placeholderTemplateId = placeholderTemplateId;
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
        var ids = super.getTemplateIds();
        if (placeholderTemplateId != null) {
            ids.add(placeholderTemplateId);
        }
        if (confirmPromptTemplateId != null) {
            ids.add(confirmPromptTemplateId);
        }
        if (mismatchMessageTemplateId != null) {
            ids.add(mismatchMessageTemplateId);
        }
        return ids;
    }
}
