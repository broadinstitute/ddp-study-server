package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.validation.RegexDto;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiRegexValidation {

    @SqlUpdate("insert into regex_validation (validation_id, regex_pattern) values (:validationId, :pattern)")
    int insert(long validationId, String pattern);

    @SqlQuery("select * from regex_validation where validation_id = :id")
    @RegisterRowMapper(RegexDto.RegexDtoMapper.class)
    Optional<RegexDto> findById(@Bind("id") long id);
}
