package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.AgreementAnswerDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiAgreementAnswer extends SqlObject {

    @SqlUpdate("insert into agreement_answer(answer_id, answer) values (:answerId, :answer)")
    boolean insert(@Bind("answerId") long answerId, @Bind("answer") boolean answer);

    @SqlUpdate("update agreement_answer set answer = :answer where answer_id = :answerId")
    int updateById(@Bind("answerId") long answerId, @Bind("answer") boolean answer);

    @SqlUpdate("delete from agreement_answer where answer_id = :answerId")
    int deleteById(@Bind("answerId") long answerId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoById")
    @RegisterRowMapper(AgreementAnswerDto.FieldMapper.class)
    Optional<AgreementAnswerDto> findDtoById(@Bind("answerId") long answerId);

}
