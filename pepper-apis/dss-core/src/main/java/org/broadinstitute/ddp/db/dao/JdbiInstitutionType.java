package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiInstitutionType extends SqlObject {

    @SqlQuery(
            "select institution_type_id from institution_type where institution_type_code = :institutionType"
    )
    Optional<Long> getIdByType(@Bind("institutionType") InstitutionType institutionType);

    @SqlQuery("select institution_type_id from institution_type where institution_type_code = :instType")
    long findByType(@Bind("instType") InstitutionType institutionType);

    @SqlQuery("select institution_type_code from institution_type where institution_type_id = :institution_type_id")
    Optional<InstitutionType> findTypeById(@Bind("institution_type_id") long institutionTypeId);
}
