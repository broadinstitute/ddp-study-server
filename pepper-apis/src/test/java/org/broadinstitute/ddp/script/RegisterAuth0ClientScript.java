package org.broadinstitute.ddp.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Script for registering auth0 clients and setting which studies
 * they have access to.
 */
@Ignore
public class RegisterAuth0ClientScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(RegisterAuth0ClientScript.class);

    public static final String CLIENT_PREFIX = "ddp.auth0ClientLoader.";

    /**
     * Auth0 secret for the client
     */
    public static final String AUTH0_CLIENT_SECRET_PARAM = CLIENT_PREFIX + "clientSecret";

    /**
     * Name of the client.  Could be anything you want, but let's just use whatever the auth0 client name is
     */
    public static final String CLIENT_NAME_PARAM = CLIENT_PREFIX + "clientName";

    /**
     * Auth0 client id of the client
     */
    public static final String AUTH0_CLIENT_ID_PARAM = CLIENT_PREFIX + "auth0ClientId";

    /**
     * Comma separated list of studies that the client should have access to
     */
    public static final String STUDY_GUIDS_PARAM = CLIENT_PREFIX + "studyGuids";

    /**
     * Encryption secret used to encrypt this client's Auth0 secret
     */
    public static final String ENCRYPTION_SECRET = CLIENT_PREFIX + "encryptionSecret";

    /**
     * Domain for this client to authenticate to
     */
    public static final String AUTH0_DOMAIN = CLIENT_PREFIX + "auth0domain";

    public static final String STUDY_GUIDS_DELIMITER = ",";

    /**
     * Registers a new client and sets up ACL so it can access the given list
     * of studies.  Parameters are set by -D vars.
     */
    @Test
    public void registerClient() {
        String clientName = System.getProperty(CLIENT_NAME_PARAM);
        String auth0ClientId = System.getProperty(AUTH0_CLIENT_ID_PARAM);
        String auth0ClientSecret = System.getProperty(AUTH0_CLIENT_SECRET_PARAM);
        String rawStudyGuids = System.getProperty(STUDY_GUIDS_PARAM);
        String encryptionSecret = System.getProperty(ENCRYPTION_SECRET);
        String auth0domain = System.getProperty(AUTH0_DOMAIN);
        final Collection<String> studyGuids = new ArrayList<>();

        if (rawStudyGuids.contains(STUDY_GUIDS_DELIMITER)) {
            studyGuids.addAll(Arrays.asList(rawStudyGuids.split(STUDY_GUIDS_DELIMITER)));
        } else {
            studyGuids.add(rawStudyGuids);
        }

        LOG.info("Registering client {} with id {}.  Omitting logging of secret.  Will have access to {}", clientName,
                auth0ClientId, studyGuids);

        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            long auth0TenantId = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0domain).getId();
            long clientId = handle.attach(ClientDao.class).registerClient(clientName, auth0ClientId, auth0ClientSecret,
                    studyGuids, encryptionSecret, auth0TenantId);
        });
    }
}
