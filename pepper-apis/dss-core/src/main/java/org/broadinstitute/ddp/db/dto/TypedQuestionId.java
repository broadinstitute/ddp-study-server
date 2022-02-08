package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.constants.SqlConstants.QuestionTable;
import static org.broadinstitute.ddp.constants.SqlConstants.QuestionTypeTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class TypedQuestionId {

    private QuestionType type;
    private long id;

    public TypedQuestionId(QuestionType type, long id) {
        this.type = type;
        this.id = id;
    }

    public QuestionType getType() {
        return type;
    }

    public long getId() {
        return id;
    }

    public static class TypedQuestionIdMapper implements RowMapper<TypedQuestionId> {
        @Override
        public TypedQuestionId map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new TypedQuestionId(
                    QuestionType.valueOf(rs.getString(QuestionTypeTable.CODE)),
                    rs.getLong(QuestionTable.ID));
        }
    }
}
