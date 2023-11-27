package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiCountrySubnationalDivision extends SqlObject {

    @SqlQuery("select s.code from country_subnational_division as s"
            + " join country_address_info as c on c.country_address_info_id = s.country_address_info_id"
            + " where (s.code = upper(:state) or upper(s.name) = upper(:state))"
            + " and (c.code = upper(:country) or upper(c.name) = upper(:country))")
    String getStateCode(@Bind("state") String state,
                        @Bind("country") String country);


    @SqlQuery("select code from country_subnational_division where code = upper(:state) or upper(name) = upper(:state)")
    List<String> getStateCode(@Bind("state") String state);

}
