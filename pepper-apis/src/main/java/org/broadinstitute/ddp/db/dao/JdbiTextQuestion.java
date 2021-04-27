package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiTextQuestion extends SqlObject {

    @SqlUpdate("insert into text_question (question_id, input_type_id, suggestion_type_id, placeholder_template_id, "
            + " confirm_placeholder_template_id, confirm_entry, confirm_prompt_template_id, mismatch_message_template_id)"
            + " values (:questionId,"
            + "   (select text_question_input_type_id from text_question_input_type where text_question_input_type_code = :inputType),"
            + "   (select suggestion_type_id from suggestion_type where suggestion_type_code = :suggestionType),"
            + "   :placeholderTemplateId,"
            + "   :confirmPlaceholderTemplateId,"
            + "   :confirmEntry,"
            + "   :confirmPromptTemplateId,"
            + "   :mismatchMessageTemplateId)"
    )
    int insert(@Bind("questionId") long questionId,
               @Bind("inputType") TextInputType inputType,
               @Bind("suggestionType") SuggestionType suggestionType,
               @Bind("placeholderTemplateId") Long placeholderTemplateId,
               @Bind("confirmPlaceholderTemplateId") Long confirmPlaceholderTemplateId,
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

}
