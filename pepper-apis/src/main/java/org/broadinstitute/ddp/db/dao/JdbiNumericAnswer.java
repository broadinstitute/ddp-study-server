package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiNumericAnswer extends SqlObject {

    @SqlUpdate("insert into numeric_answer (answer_id, int_value) values (:answerId, :intValue)")
    int insertNumericInteger(@Bind("answerId") long answerId, @Bind("intValue") Long intValue);

    @SqlUpdate("update numeric_answer set int_value = :intValue where answer_id = :answerId")
    int updateNumericInteger(@Bind("answerId") long answerId, @Bind("intValue") Long intValue);

    @SqlUpdate("delete from numeric_answer where answer_id = :answerId")
    int deleteById(@Bind("answerId") long answerId);

    @SqlQuery("select qsc.stable_id,"
            + "       nt.numeric_type_code as numeric_type,"
            + "       ans.answer_id,"
            + "       ans.answer_guid,"
            + "       na.int_value"
            + "  from answer as ans"
            + "  join numeric_answer as na on na.answer_id = ans.answer_id"
            + "  join question as q on q.question_id = ans.question_id"
            + "  join question_stable_code as qsc on qsc.question_stable_code_id = q.question_stable_code_id"
            + "  join numeric_question as nq on nq.question_id = q.question_id"
            + "  join numeric_type as nt on nt.numeric_type_id = nq.numeric_type_id"
            + " where ans.answer_id = :answerId")
    @RegisterRowMapper(NumericAnswerRowMapper.class)
    Optional<NumericAnswer> findById(@Bind("answerId") long answerId);

    class NumericAnswerRowMapper implements RowMapper<NumericAnswer> {
        @Override
        public NumericAnswer map(ResultSet rs, StatementContext ctx) throws SQLException {
            NumericType type = NumericType.valueOf(rs.getString("numeric_type"));
            if (type == NumericType.INTEGER) {
                return new NumericIntegerAnswer(
                        rs.getLong(SqlConstants.AnswerTable.ID),
                        rs.getString(SqlConstants.QuestionTable.STABLE_ID),
                        rs.getString(SqlConstants.AnswerTable.GUID),
                        (Long) rs.getObject("int_value"));
            } else {
                throw new DaoException("Unhandled numeric type: " + type);
            }
        }
    }
}
