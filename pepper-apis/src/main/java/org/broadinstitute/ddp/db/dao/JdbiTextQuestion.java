package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.TextQuestionDto;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiTextQuestion extends SqlObject {

    @SqlUpdate("insert into text_question (question_id, input_type_id, suggestion_type_id, placeholder_template_id, "
            + " confirm_entry, confirm_prompt_template_id, mismatch_message_template_id)"
            + " values (:questionId,"
            + "   (select text_question_input_type_id from text_question_input_type where text_question_input_type_code = :inputType),"
            + "   (select suggestion_type_id from suggestion_type where suggestion_type_code = :suggestionType),"
            + "   :placeholderTemplateId,"
            + "   :confirmEntry,"
            + "   :confirmPromptTemplateId,"
            + "   :mismatchMessageTemplateId)"
    )
    int insert(@Bind("questionId") long questionId,
               @Bind("inputType") TextInputType inputType,
               @Bind("suggestionType") SuggestionType suggestionType,
               @Bind("placeholderTemplateId") Long placeholderTemplateId,
               @Bind("confirmEntry") boolean confirmEntry,
               @Bind("confirmPromptTemplateId") Long confirmPromptTemplateId,
               @Bind("mismatchMessageTemplateId") Long mismatchMessageTemplateId);

    @SqlUpdate("UPDATE text_question as tq "
            + "INNER JOIN text_question_input_type AS i_type ON i_type.text_question_input_type_code = :inputType "
            + "INNER JOIN suggestion_type AS s_type ON s_type.suggestion_type_code = :suggestionType "
            + "SET tq.input_type_id = i_type.text_question_input_type_id, "
            + "     tq.suggestion_type_id = s_type.suggestion_type_id, "
            + "     tq.placeholder_template_id = :placeholderTemplateId "
            + "WHERE tq.question_id = :questionId")
    boolean update(@Bind("questionId") long questionId,
                    @Bind("inputType") TextInputType inputType,
                    @Bind("suggestionType") SuggestionType suggestionType,
                    @Bind("placeholderTemplateId") Long placeholderTemplateId);


    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoAndSuggestionsByQuestionId")
    @RegisterConstructorMapper(value = TextQuestionDto.class, prefix = "p")
    @UseRowReducer(RowReducer.class)
    Optional<TextQuestionDto> findDtoByQuestionId(@Bind("questionId") long questionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoAndSuggestionsByActivityId")
    @RegisterConstructorMapper(value = TextQuestionDto.class, prefix = "p")
    @UseRowReducer(RowReducer.class)
    List<TextQuestionDto> findDtoByActivityId(@Bind("activityId") long activityId);

    class RowReducer implements LinkedHashMapRowReducer<Long, TextQuestionDto> {
        @Override
        public void accumulate(Map<Long, TextQuestionDto> map, RowView rowView) {
            TextQuestionDto textQuestionDto = map.computeIfAbsent(rowView.getColumn("p_question_id", Long.class),
                    id -> rowView.getRow(TextQuestionDto.class));
            if (rowView.getColumn("c_suggestion", String.class) != null) {
                textQuestionDto.addSuggestion(rowView.getColumn("c_suggestion", String.class));
            }
        }
    }
}
