package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.validation.NumOptionsSelectedDto;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiNumOptionsSelectedValidation {

    @SqlUpdate("insert into num_options_selected_validation (validation_id, min_selections, max_selections)"
            + " values (:validationId, :min, :max)")
    int insert(long validationId, Integer min, Integer max);

    @SqlQuery("select * from num_options_selected_validation where validation_id = :id")
    @RegisterRowMapper(NumOptionsSelectedDto.NumOptionsSelectedMapper.class)
    Optional<NumOptionsSelectedDto> findById(@Bind("id") long id);
}
