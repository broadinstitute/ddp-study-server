package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.OLCPrecisionDto;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiOLCPrecision extends SqlObject {
    @SqlQuery("select * from olc_precision where olc_precision_code = :code")
    @RegisterConstructorMapper(OLCPrecisionDto.class)
    OLCPrecisionDto findDtoForCode(@Bind("code") OLCPrecision code);
}
