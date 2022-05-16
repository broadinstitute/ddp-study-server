package org.broadinstitute.ddp.db.dao;

import java.util.Collection;
import java.util.List;

import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.StudyClientConfiguration;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ClientDao extends SqlObject {
    static final Logger LOG = LoggerFactory.getLogger(ClientDao.class);

    @CreateSqlObject
    JdbiClient getClientDao();

    @CreateSqlObject
    JdbiUmbrellaStudy getUmbrellaStudyDao();

    @CreateSqlObject
    JdbiClientUmbrellaStudy getClientUmbrellaStudyDao();

    /**
     * Saves a new client and gives it access to the given list of studies
     *
     * @param auth0ClientId      auth0's internal client id
     * @param auth0ClientSecret  auth0's secret for the client
     * @param studyGuidsToAccess list of guids that the client should
     *                           have access to
     * @return client.client_id
     */
    default long registerClient(String auth0ClientId,
                                String auth0ClientSecret,
                                Collection<String> studyGuidsToAccess,
                                String encryptionKey,
                                Long auth0TenantId) {

        String encryptedClientSecret = AesUtil.encrypt(auth0ClientSecret, encryptionKey);

        long clientId = getClientDao().insertClient(auth0ClientId, encryptedClientSecret, auth0TenantId,
                                                    null);
        LOG.info("Inserted client {}", clientId);

        for (String studyGuid : studyGuidsToAccess) {
            long aclId = getClientUmbrellaStudyDao().insert(clientId, getUmbrellaStudyDao().findByStudyGuid(studyGuid).getId());
            LOG.info(
                    "Inserted client__umbrella_study id {} for client {}, tenant {} and study {}",
                    aclId, auth0ClientId, auth0TenantId, studyGuid
            );
        }
        return clientId;
    }

    /**
     * Returns the client configuration for the given auth0 client id.
     * If no configuration is found or if the client has been revoked,
     * null is returned.
     *
     * @param auth0ClientId A auth0Client to get an id for
     * @return the study client configuration
     */
    default StudyClientConfiguration getConfiguration(final String auth0ClientId, String auth0Domain) {
        StudyClientConfiguration clientConfiguration = getClientDao().getStudyClientConfigurationByClientAndDomain(
                auth0ClientId, auth0Domain).orElse(null);

        return clientConfiguration;
    }

    /**
     * Returns a clientId by auth0ClientId. clientId is an internal id of the client in DDP.
     *
     * @param auth0ClientId A auth0Client to get an id for
     * @return the database client id
     */
    default Long getClientIdByAuth0ClientAndDomain(String auth0ClientId, String auth0Domain) {
        return getClientDao().getClientIdByAuth0ClientAndDomain(auth0ClientId, auth0Domain).orElse(null);
    }


    /**
     * Passthrough to JdbiClient that deletes a client
     * @return number of rows deleted. This should really be 1
     */
    default int deleteByAuth0ClientIdAndAuth0TenantId(String auth0ClientId, long auth0TenantId) {
        Long clientId = getClientDao().getClientIdByAuth0ClientIdAndAuth0TenantId(auth0ClientId, auth0TenantId).orElse(null);
        if (clientId == null) {
            return 0;
        }
        // First remove all client__umbrella_study entries for this client Id
        getClientUmbrellaStudyDao().deleteByInternalClientId(clientId);
        // now remove the client itself
        return getClientDao().deleteByClientId(clientId);
    }


    /**
     * Indicates that the Auth0 Client exists and is not revoked
     * @param auth0ClientId auth0client Id to consider
     * @param auth0Domain the auth0 tenant/domain
     * @return true if the Auth0ClientId exists and is not revoked
     */
    default boolean isAuth0ClientActive(String auth0ClientId, String auth0Domain) {
        return getClientDao().isAuth0ClientIdRevoked(auth0ClientId, auth0Domain).orElse(1) != 1;
    }

    @SqlQuery("select "
            + "     c.client_id, "
            + "     c.auth0_client_id, "
            + "     c.auth0_signing_secret, "
            + "     c.web_password_redirect_url, "
            + "     c.is_revoked, "
            + "     c.auth0_tenant_id, "
            + "     t.auth0_domain "
            + "  from client__umbrella_study as cus"
            + "  join client as c on c.client_id = cus.client_id"
            + "  join umbrella_study as us on us.umbrella_study_id = cus.umbrella_study_id"
            + "  join auth0_tenant as t on t.auth0_tenant_id = us.auth0_tenant_id and t.auth0_tenant_id = c.auth0_tenant_id"
            + " where us.umbrella_study_id = :studyId"
            + "   and not c.is_revoked")
    @RegisterConstructorMapper(ClientDto.class)
    List<ClientDto> findAllPermittedClientsForStudy(@Bind("studyId") long studyId);
}
