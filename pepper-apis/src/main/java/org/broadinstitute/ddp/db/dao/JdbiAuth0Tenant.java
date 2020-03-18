package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiAuth0Tenant extends SqlObject {

    @SqlQuery("select * from auth0_tenant where auth0_tenant_id = :id")
    @RegisterConstructorMapper(Auth0TenantDto.class)
    Optional<Auth0TenantDto> findById(@Bind("id") long auth0TenantId);

    @SqlQuery("select t.* from auth0_tenant t,umbrella_study s where t.auth0_tenant_id = s.auth0_tenant_id "
            + "and s.guid = :studyGuid")
    @RegisterConstructorMapper(Auth0TenantDto.class)
    Auth0TenantDto findByStudyGuid(@Bind("studyGuid") String studyGuid);

    @SqlQuery("select * from auth0_tenant t where t.auth0_domain = :auth0Domain")
    @RegisterConstructorMapper(Auth0TenantDto.class)
    Auth0TenantDto findByDomain(@Bind("auth0Domain") String domain);

    @SqlQuery(
            "select t.auth0_tenant_id, t.management_client_id, t.management_client_secret, t.auth0_domain"
            + " from auth0_tenant t, user u where t.auth0_tenant_id = u.auth0_tenant_id and u.guid = :userGuid"
    )
    @RegisterConstructorMapper(Auth0TenantDto.class)
    Auth0TenantDto findByUserGuid(@Bind("userGuid") String userGuid);

    @SqlUpdate("insert into auth0_tenant(auth0_domain,management_client_id,management_client_secret) "
            + " values(:domain,:clientId,:secret)")
    @GetGeneratedKeys
    long insert(@Bind("domain") String domain,
                @Bind("clientId") String mgmtApiClient,
                @Bind("secret") String encryptedSecret);

    /**
     * Inserts a row for the given domain if it doesn't exist already.
     * Otherwise, no insert is done.
     */
    default Auth0TenantDto insertIfNotExists(String domain, String mgmtApiClient, String encryptedSecret) {
        Auth0TenantDto tenantDto = findByDomain(domain);
        if (tenantDto == null) {
            long auth0TenantId = insert(domain, mgmtApiClient, encryptedSecret);
            tenantDto = new Auth0TenantDto(auth0TenantId, mgmtApiClient, encryptedSecret, domain);
        }
        return tenantDto;
    }
}
