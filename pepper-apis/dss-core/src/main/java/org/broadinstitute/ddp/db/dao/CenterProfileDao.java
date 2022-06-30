package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.CenterProfileDto;
import org.broadinstitute.ddp.db.dto.CenterUserDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

import java.util.Optional;

public interface CenterProfileDao extends SqlObject {
    @UseStringTemplateSqlLocator
    @SqlQuery("SELECT * FROM `center_profile` WHERE center_id = :id")
    @RegisterConstructorMapper(CenterProfileDto.class)
    Optional<CenterProfileDto> findById(@Bind("id") final Long id);

    @UseStringTemplateSqlLocator
    @SqlUpdate("INSERT INTO `center_profile` "
            + "         SET name               = :name, "
            + "             address1           = :address1, "
            + "             address2           = :address2, "
            + "             city_id            = :cityId, "
            + "             primary_contact_id = :primaryContactId")
    int insert(@BindBean final CenterProfileDto centerProfile);
}
