package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.validation.LengthDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiLengthValidation extends SqlObject {

    @SqlUpdate("insert into length_validation (validation_id, min_length, max_length)"
            + " values (:validationId, :min, :max)")
    int insert(long validationId, Integer min, Integer max);

    @SqlQuery("select * from length_validation where validation_id = :id")
    @RegisterRowMapper(LengthDto.LengthMapper.class)
    Optional<LengthDto> findById(@Bind("id") long id);
}
