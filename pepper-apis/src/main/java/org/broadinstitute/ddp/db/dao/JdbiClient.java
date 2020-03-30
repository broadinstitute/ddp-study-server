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
     * @param auth0ClientId client id
     * @param auth0TenantId the id of the tenant
     */
    @SqlUpdate("insert into client(is_revoked,auth0_signing_secret,"
            + "auth0_client_id,auth0_tenant_id,web_password_redirect_url)"
            + " values(false,:auth0Secret,:auth0ClientId,:auth0TenantId,:redirectUrl)")
    @GetGeneratedKeys
    long insertClient(@Bind("auth0ClientId") String auth0ClientId,
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
            + "     c.client_id, c.auth0_client_id, c.auth0_signing_secret, "
            + "     c.web_password_redirect_url, c.is_revoked, c.auth0_tenant_id "
            + "FROM "
            + "     client c, auth0_tenant t "
            + "WHERE "
            + "     c.auth0_client_id = :auth0ClientId AND t.auth0_domain = :auth0Domain"
    )
    @RegisterConstructorMapper(ClientDto.class)
    Optional<ClientDto> getClientByAuth0ClientAndDomain(
            @Bind("auth0ClientId") String auth0ClientId,
            @Bind("auth0Domain") String auth0Domain
    );

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
            + "     auth0_client_id = :auth0ClientId AND auth0_tenant_id = :auth0TenantId"
    )
    int deleteByAuth0ClientIdAndAuth0TenantId(
            @Bind("auth0ClientId") String auth0ClientId,
            @Bind("auth0TenantId") long auth0TenantId
    );

    @SqlUpdate("DELETE FROM client WHERE client_id = :id")
    int deleteByClientId(@Bind("id") long id);

    @SqlQuery("SELECT "
            + "     client_id "
            + "FROM "
            + "     client "
            + "WHERE "
            + "     auth0_client_id = :auth0ClientId AND auth0_tenant_id = :auth0TenantId"
    )
    Optional<Long> getClientIdByAuth0ClientIdAndAuth0TenantId(
            @Bind("auth0ClientId") String auth0ClientId,
            @Bind("auth0TenantId") long auth0TenantId
    );

    @SqlQuery("SELECT client_id,auth0_client_id,auth0_signing_secret,web_password_redirect_url,is_revoked,auth0_tenant_id"
            + "  FROM client WHERE auth0_client_id = :auth0ClientId AND auth0_tenant_id = :auth0TenantId")
    @RegisterConstructorMapper(ClientDto.class)
    Optional<ClientDto> findByAuth0ClientIdAndAuth0TenantId(
            @Bind("auth0ClientId") String auth0ClientId,
            @Bind("auth0TenantId") long auth0TenantId
    );

    @SqlUpdate(
            "UPDATE client c JOIN auth0_tenant t ON c.auth0_tenant_id = t.auth0_tenant_id "
            + " SET c.web_password_redirect_url = :url WHERE c.auth0_client_id = :auth0ClientId "
            + "AND t.auth0_domain = :auth0Domain"
    )
    int updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
            @Bind("url") String url,
            @Bind("auth0ClientId") String auth0ClientId,
            @Bind("auth0Domain") String auth0Domain
    );

    @SqlUpdate(
            "UPDATE client c JOIN auth0_tenant t ON c.auth0_tenant_id = t.auth0_tenant_id "
            + " SET c.is_revoked = :isRevoked WHERE c.auth0_client_id = :auth0ClientId "
            + "AND t.auth0_domain = :auth0Domain"
    )
    int updateIsRevokedByAuth0ClientIdAndAuth0Domain(
            @Bind("isRevoked") boolean isRevoked,
            @Bind("auth0ClientId") String auth0ClientId,
            @Bind("auth0Domain") String auth0Domain
    );

    @SqlQuery(
            "SELECT COUNT(*) FROM client WHERE auth0_client_id = :auth0ClientId"
    )
    int countClientsWithSameAuth0ClientId(@Bind("auth0ClientId") String auth0ClientId);
}
