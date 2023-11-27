package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.CenterProfileDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

import java.util.Optional;

public interface CenterProfileDao extends SqlObject {
    @UseStringTemplateSqlLocator
    @SqlQuery("SELECT * FROM `center_profile` WHERE center_id = :id")
    @RegisterConstructorMapper(CenterProfileDto.class)
    Optional<CenterProfileDto> findById(@Bind("id") final Long id);
}
