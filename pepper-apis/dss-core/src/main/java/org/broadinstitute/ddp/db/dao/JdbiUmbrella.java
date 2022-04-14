package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.UmbrellaDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUmbrella extends SqlObject {

    @SqlUpdate("insert into umbrella (umbrella_name, umbrella_guid) values (:name, :guid)")
    @GetGeneratedKeys
    long insert(@Bind("name") String umbrellaName, @Bind("guid") String umbrellaGuid);

    @SqlQuery("select umbrella_id from umbrella where umbrella_name = :name")
    Optional<Long> findIdByName(@Bind("name") String umbrellaName);

    @SqlQuery("select umbrella_id, umbrella_name, umbrella_guid from umbrella where umbrella_guid = :guid")
    @RegisterConstructorMapper(UmbrellaDto.class)
    Optional<UmbrellaDto> findByGuid(@Bind("guid") String umbrellaGuid);

    @SqlQuery("select umbrella_id, umbrella_name, umbrella_guid from umbrella where umbrella_id = :id")
    @RegisterConstructorMapper(UmbrellaDto.class)
    Optional<UmbrellaDto> findById(@Bind("id") long umbrellaId);

    @SqlQuery("select umbrella_id, umbrella_name, umbrella_guid from umbrella")
    @RegisterConstructorMapper(UmbrellaDto.class)
    List<UmbrellaDto> findAll();

    @SqlUpdate("delete from umbrella where umbrella_id = :id")
    int deleteById(@Bind("id") long id);
}
