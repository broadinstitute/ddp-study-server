package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.AgreementQuestionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiAgreementQuestion extends SqlObject {

    @SqlUpdate("insert into agreement_question values(:questionId)")
    int insert(@Bind("questionId") long questionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoByQuestionId")
    @RegisterConstructorMapper(AgreementQuestionDto.class)
    Optional<AgreementQuestionDto> findDtoByQuestionId(@Bind("questionId") long questionId);
}
