package org.broadinstitute.ddp.db.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class AgreementAnswerDto {

    private String answerGuid;
    private boolean answer;
    private String questionStableId;

    public AgreementAnswerDto(String answerGuid, boolean answer, String questionStableId) {
        this.answerGuid = answerGuid;
        this.answer = answer;
        this.questionStableId = questionStableId;
    }

    public String getAnswerGuid() {
        return answerGuid;
    }

    public boolean getAnswer() {
        return answer;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public static class FieldMapper implements RowMapper<AgreementAnswerDto> {
        @Override
        public AgreementAnswerDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new AgreementAnswerDto(
                    rs.getString(SqlConstants.AnswerTable.GUID),
                    rs.getBoolean(SqlConstants.AnswerTable.ANSWER),
                    rs.getString(SqlConstants.QuestionTable.STABLE_ID)
            );
        }
    }
}
