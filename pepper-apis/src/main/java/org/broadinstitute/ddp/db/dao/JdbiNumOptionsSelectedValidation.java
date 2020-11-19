package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiNumOptionsSelectedValidation {

    @SqlUpdate("insert into num_options_selected_validation (validation_id, min_selections, max_selections)"
            + " values (:validationId, :min, :max)")
    int insert(long validationId, Integer min, Integer max);

}
