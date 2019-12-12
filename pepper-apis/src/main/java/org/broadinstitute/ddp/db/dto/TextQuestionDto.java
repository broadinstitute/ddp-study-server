package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class TextQuestionDto extends QuestionDto {

    private TextInputType inputType;
    private SuggestionType suggestionType;
    private Long placeholderTemplateId;
    private List<String> suggestions;

    @JdbiConstructor
    public TextQuestionDto(@Nested QuestionDto questionDto,
                           @ColumnName("input_type_code") TextInputType inputType,
                           @ColumnName("suggestion_type_code") SuggestionType suggestionType,
                           @ColumnName("placeholder_template_id") Long placeholderTemplateId) {
        super(questionDto);
        this.inputType = inputType;
        this.suggestionType = suggestionType;
        this.placeholderTemplateId = placeholderTemplateId;
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

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void addSuggestion(String suggestionToAdd) {
        if (suggestions == null) {
            suggestions = new ArrayList<>();
        }
        suggestions.add(suggestionToAdd);
    }

}
