package org.broadinstitute.ddp.db.dao;

import java.util.Collection;

import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.StudyClientConfiguration;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
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
     * @param clientName         the name of the client.  Could be anything, but let's
     *                           keep it consistent with the auth0 client name
     * @param auth0ClientId      auth0's internal client id
     * @param auth0ClientSecret  auth0's secret for the client
     * @param studyGuidsToAccess list of guids that the client should
     *                           have access to
     * @return client.client_id
     */
    default long registerClient(String clientName,
                                String auth0ClientId,
                                String auth0ClientSecret,
                                Collection<String> studyGuidsToAccess,
                                String encryptionKey,
                                Long auth0TenantId) {

        String encryptedClientSecret = AesUtil.encrypt(auth0ClientSecret, encryptionKey);

        long clientId = getClientDao().insertClient(clientName, auth0ClientId, encryptedClientSecret, auth0TenantId,
                                                    null);
        LOG.info("Inserted client {} for {}", clientId, clientName);

        for (String studyGuid : studyGuidsToAccess) {
            long aclId = getClientUmbrellaStudyDao().insert(clientId, getUmbrellaStudyDao().findByStudyGuid(studyGuid).getId());
            LOG.info("Inserted client__umbrella_study id {} for client {} and study {}", aclId, clientName, studyGuid);
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
     * @param clientName to delete
     * @return number of rows deleted. This should really be 1
     */
    default int deleteClientByAuth0ClientIdAndAuth0TenantId(String auth0ClientId, long auth0TenantId) {
        // First remove all client__umbrella_study entries for this client Id
        Long clientId = getClientDao().getClientIdByAuth0ClientIdAndAuth0TenantId(auth0ClientId, auth0TenantId).orElse(null);
        if (clientId != null) {
            getClientUmbrellaStudyDao().deleteByInternalClientId(clientId);
        }

        // now remove the client itself
        return getClientDao().deleteClientByAuth0ClientIdAndAuth0TenantId(auth0ClientId, auth0TenantId);
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
}
