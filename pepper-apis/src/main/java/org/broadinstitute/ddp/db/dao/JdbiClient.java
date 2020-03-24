package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.security.StudyClientConfiguration;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiClient extends SqlObject {
    /**
     * It is expected that the auth0Secret here is encrypted. Don't call this directly, but rather call ClientDao
     * @param clientName clientName to save
     * @param auth0ClientId client id
     * @param auth0TenantId the id of the tenant
     */
    @SqlUpdate("insert into client(client_name,is_revoked,auth0_signing_secret,"
            + "auth0_client_id,auth0_tenant_id,web_password_redirect_url)"
            + " values(:clientName,false,:auth0Secret,:auth0ClientId,:auth0TenantId,:redirectUrl)")
    @GetGeneratedKeys
    long insertClient(@Bind("clientName")String clientName,
                      @Bind("auth0ClientId") String auth0ClientId,
                      @Bind("auth0Secret") String auth0Secret,
                      @Bind("auth0TenantId") long auth0TenantId,
                      @Bind("redirectUrl") String redirectUrl);

    @SqlQuery("SELECT "
            + "     client.client_id, "
            + "     t.auth0_domain, "
            + "     client.auth0_client_id, "
            + "     client.auth0_signing_secret "
            + "FROM "
            + "     client, auth0_tenant as t "
            + "WHERE "
            + "     client.is_revoked != 1 and client.auth0_client_id = :auth0ClientId"
            + "     AND client.auth0_tenant_id = t.auth0_tenant_id"
            + "     AND t.auth0_domain = :auth0Domain"
    )
    @RegisterConstructorMapper(StudyClientConfiguration.class)
    Optional<StudyClientConfiguration> getStudyClientConfigurationByClientAndDomain(@Bind("auth0ClientId") String
                                                                                     auth0ClientId,
                                                                                    @Bind("auth0Domain") String
                                                                                     auth0Domain);

    @SqlQuery("SELECT "
            + "     c.client_id "
            + "FROM "
            + "     client c, auth0_tenant t "
            + "WHERE "
            + "     c.auth0_client_id = :auth0ClientId and c.auth0_tenant_id = t.auth0_tenant_id"
            + "     AND t.auth0_domain = :auth0Domain"
    )
    Optional<Long> getClientIdByAuth0ClientAndDomain(@Bind("auth0ClientId") String auth0ClientId,
                                                     @Bind("auth0Domain") String auth0Domain);

    @SqlQuery("SELECT "
            + "     c.is_revoked "
            + "FROM "
            + "     client c, auth0_tenant t "
            + "WHERE "
            + "     c.auth0_client_id = :auth0ClientId"
            + "     AND c.auth0_tenant_id = t.auth0_tenant_id "
            + "     AND t.auth0_domain = :auth0Domain"
    )
    Optional<Integer> isAuth0ClientIdRevoked(@Bind("auth0ClientId") String auth0ClientId,
                                             @Bind("auth0Domain") String auth0Domain);

    @SqlUpdate("DELETE FROM "
            + "     client "
            + "WHERE "
            + "     client.client_name = :clientName"
    )
    int deleteClientByName(@Bind("clientName") String clientName);

    @SqlQuery("SELECT "
            + "     client_id "
            + "FROM "
            + "     client "
            + "WHERE "
            + "     client.client_name = :clientName"
    )
    Optional<Long> getClientIdByName(@Bind("clientName") String clientName);

    @SqlQuery("SELECT client_id,client_name,auth0_client_id,auth0_signing_secret,web_password_redirect_url,is_revoked,auth0_tenant_id"
            + "  FROM client WHERE auth0_client_id = :auth0ClientId")
    @RegisterConstructorMapper(ClientDto.class)
    Optional<ClientDto> findByAuth0ClientId(@Bind("auth0ClientId") String auth0ClientId);

    @SqlQuery("SELECT client_id,client_name,auth0_client_id,auth0_signing_secret,web_password_redirect_url,is_revoked,auth0_tenant_id"
            + "  FROM client WHERE client_name= :clientName")
    @RegisterConstructorMapper(ClientDto.class)
    Optional<ClientDto> findByClientName(@Bind("clientName") String clientName);

    @SqlUpdate("UPDATE client SET web_password_redirect_url = :url WHERE auth0_client_id = :auth0ClientId")
    int updateWebPasswordRedirectUrlByAuth0ClientId(
            @Bind("url") String url,
            @Bind("auth0ClientId") String auth0ClientId
    );

    @SqlUpdate("UPDATE client SET is_revoked = :isRevoked WHERE auth0_client_id = :auth0ClientId")
    int updateIsRevokedByAuth0ClientId(
            @Bind("isRevoked") boolean isRevoked,
            @Bind("auth0ClientId") String auth0ClientId
    );
}
