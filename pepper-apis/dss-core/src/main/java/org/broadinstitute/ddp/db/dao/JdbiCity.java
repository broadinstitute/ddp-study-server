package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiCity extends SqlObject {

    @SqlQuery(
            "select c.city_id from city c, country_subnational_division csd"
            + " where c.state_id = csd.country_subnational_division_id"
            + " and c.name = :name and csd.name = :stateName"
    )
    Optional<Long> getIdByNameAndStateName(@Bind("name") String name, @Bind("stateName") String stateName);

    @SqlUpdate(
            "   insert into city (state_id, name) values ("
            + "     ("
            + "         select country_subnational_division_id from country_subnational_division"
            + "         where name = :stateName"
            + "     ),"
            + "     :cityName"
            + " )"
    )
    @GetGeneratedKeys
    long insert(@Bind("stateName") String stateName, @Bind("cityName") String cityName);

    @SqlUpdate("delete from city where city_id = :cityId")
    int deleteById(@Bind("cityId") long cityId);

}
