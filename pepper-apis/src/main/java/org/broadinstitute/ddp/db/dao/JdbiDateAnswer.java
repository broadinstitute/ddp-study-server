package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiDateAnswer extends SqlObject {

    String YEAR = "year";
    String MONTH = "month";
    String DAY = "day";

    @SqlUpdate("insert into date_answer (answer_id, year, month, day) values (:answerId, :year, :month, :day)")
    int insert(long answerId, Integer year, Integer month, Integer day);

    /**
     * Given an answer id and date parameters, insert values into date answer table in db.
     */
    default void insertAnswer(long answerId, Integer year, Integer month, Integer day) {
        int numRowsInserted = insert(answerId, year, month, day);
        if (numRowsInserted != 1) {
            throw new DaoException("Inserted " + numRowsInserted + " date answer rows");
        }
    }

    default void insertAnswer(long answerId, DateValue value) {
        insertAnswer(answerId, value.getYear(), value.getMonth(), value.getDay());
    }

    @SqlUpdate("update date_answer set year = :year, month = :month, day = :day where answer_id = :answerId")
    int updateById(long answerId, Integer year, Integer month, Integer day);

    /**
     * Given an answer id and new date parameters, update date value in db.
     */
    default void updateAnswerById(long answerId, Integer year, Integer month, Integer day) {
        int numRowsUpdated = updateById(answerId, year, month, day);
        if (numRowsUpdated != 1) {
            throw new DaoException("Updated " + numRowsUpdated + " date answer rows");
        }
    }

    default void updateAnswerById(long answerId, DateValue value) {
        updateAnswerById(answerId, value.getYear(), value.getMonth(), value.getDay());
    }

    @SqlUpdate("delete from date_answer where answer_id = :answerId")
    int deleteById(long answerId);

    /**
     * Given an answer id, remove its row from the date answer table in db.
     */
    default void deleteAnswerById(long answerId) {
        int numRowsDeleted = deleteById(answerId);
        if (numRowsDeleted != 1) {
            throw new DaoException("Deleted " + numRowsDeleted + " date answer rows");
        }
    }

    @SqlQuery("queryDateAnswerInfoById")
    @UseStringTemplateSqlLocator
    @RegisterRowMapper(DateAnswerInfoMapper.class)
    Optional<DateAnswer> getById(long answerId);

    @SqlQuery("select da.year, da.month, da.day"
            + " from date_answer as da"
            + " join answer as a on a.answer_id = da.answer_id"
            + " join activity_instance as ai on  ai.activity_instance_id = a.activity_instance_id"
            + " where a.question_id = :questionId"
            + " and ai.activity_instance_guid = :activityInstanceGuid"
            + " order by a.last_updated_at limit 1")
    @RegisterConstructorMapper(DateValue.class)
    Optional<DateValue> findAnswerByQuestionIdActivityInstanceGuid(@Bind("questionId") long questionId,
                                                                   @Bind("activityInstanceGuid") String activityInstanceGuid);

    class DateAnswerInfoMapper implements RowMapper<DateAnswer> {
        @Override
        public DateAnswer map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new DateAnswer(
                    rs.getLong(SqlConstants.AnswerTable.ID),
                    rs.getString(SqlConstants.QuestionTable.STABLE_ID),
                    rs.getString(SqlConstants.AnswerTable.GUID),
                    (Integer) rs.getObject(YEAR),
                    (Integer) rs.getObject(MONTH),
                    (Integer) rs.getObject(DAY));
        }
    }

    class ZeroedDateValueMapper implements RowMapper<DateValue> {
        @Override
        public DateValue map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new DateValue(
                    Optional.ofNullable((Integer) rs.getObject(YEAR)).orElse(0),
                    Optional.ofNullable((Integer) rs.getObject(MONTH)).orElse(0),
                    Optional.ofNullable((Integer) rs.getObject(DAY)).orElse(0));
        }
    }
}
