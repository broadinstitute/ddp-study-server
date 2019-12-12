package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiClientUmbrellaStudy extends SqlObject {

    @SqlUpdate("insert into client__umbrella_study(client_id,umbrella_study_id) values(:clientId,:studyId)")
    @GetGeneratedKeys
    long insert(@Bind("clientId") long clientId, @Bind("studyId") long studyId);

    @SqlQuery("select us.guid from client__umbrella_study as cus "
            + "join client as c on c.client_id = cus.client_id "
            + "join umbrella_study as us on us.umbrella_study_id = cus.umbrella_study_id "
            + "join auth0_tenant t on t.auth0_tenant_id = us.auth0_tenant_id "
            + "where c.auth0_client_id = :auth0ClientId and not c.is_revoked "
            + "and c.auth0_tenant_id = t.auth0_tenant_id")
    List<String> findPermittedStudyGuidsByAuth0ClientId(@Bind("auth0ClientId") String auth0ClientId);

    /**
     * This is delete function works off client tables client_id NOT auth0ClientId
     * @return number of rows deleted, which could be more than one
     */
    @SqlUpdate("DELETE FROM "
            + "     client__umbrella_study "
            + "WHERE "
            + "     client_id = :clientId"
    )
    int deleteByInternalClientId(@Bind("clientId") Long clientId);
}
