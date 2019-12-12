package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.PicklistOptionAnswerTable;
import static org.broadinstitute.ddp.constants.SqlConstants.PicklistOptionTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiPicklistOptionAnswer extends SqlObject {

    @SqlUpdate("insert into picklist_option__answer(picklist_option_id, answer_id) values(:picklistOptionId,:answerId)")
    @GetGeneratedKeys
    long insert(long picklistOptionId, long answerId);

    @SqlBatch("insert into picklist_option__answer (answer_id, picklist_option_id, detail_text)"
            + " values (:answerId, :optionId, :detailText)")
    @GetGeneratedKeys
    long[] bulkInsert(@Bind("answerId") long answerId,
                      @Bind("optionId") List<Long> picklistOptionIds,
                      @Bind("detailText") List<String> detailTexts);


    @SqlUpdate("delete from picklist_option__answer where answer_id = :answerId")
    int deleteAllByAnswerId(@Bind("answerId") long answerId);

    @SqlUpdate("delete from picklist_option__answer where answer_id in (select answer_id from answer where"
            + " question_id = :questionId and activity_instance_id = :activityInstanceId)")
    int deleteAllForQuestion(long activityInstanceId, long questionId);


    @SqlQuery("select po.picklist_option_stable_id, poa.detail_text"
            + " from picklist_option__answer as poa"
            + " join answer as a on a.answer_id = poa.answer_id"
            + " join picklist_option as po on po.picklist_option_id = poa.picklist_option_id"
            + " where a.answer_id = :answerId")
    @RegisterRowMapper(SelectedPicklistOptionMapper.class)
    List<SelectedPicklistOption> findAllSelectedOptionsByAnswerId(@Bind("answerId") long answerId);

    class SelectedPicklistOptionMapper implements RowMapper<SelectedPicklistOption> {
        @Override
        public SelectedPicklistOption map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new SelectedPicklistOption(
                    rs.getString(PicklistOptionTable.STABLE_ID),
                    rs.getString(PicklistOptionAnswerTable.DETAIL_TEXT));
        }
    }
}
