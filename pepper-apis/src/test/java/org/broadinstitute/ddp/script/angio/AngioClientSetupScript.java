package org.broadinstitute.ddp.script.angio;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class AngioClientSetupScript extends TxnAwareBaseTest {

    private static final String CLIENT_ID = "ddp.clientId";
    private static final String CLIENT_SECRET = "ddp.clientSecret";
    private static final String CLIENT_NAME = "ddp.clientName";
    private static final String DSM_CLIENT_ID = "ddp.dsmClientId";
    private static final String DSM_CLIENT_SECRET = "ddp.dsmClientSecret";
    private static final String POST_PASSWORD_REDIRECT_URL = "ddp.clientPasswordRedirectUrl";
    private static final String DSM_CLIENT_NAME = "dsm";


    private static final Logger LOG = LoggerFactory.getLogger(AngioClientSetupScript.class);

    public static final String TENANT_DOMAIN = System.getProperty(AngioStudyCreationScript.CMI_AUTH0_DOMAIN);

    @Test
    public void insertWebClient() {
        // read stuff from config file
        String auth0ClientId = System.getProperty(CLIENT_ID);
        String clientSecret = System.getProperty(CLIENT_SECRET);
        String clientName = System.getProperty(CLIENT_NAME);
        String redirectUrl = System.getProperty(POST_PASSWORD_REDIRECT_URL);
        String dsmAuth0ClientId = System.getProperty(DSM_CLIENT_ID);
        String dsmClientSecret = System.getProperty(DSM_CLIENT_SECRET);


        // encrypt secret
        String encryptedSecret = AesUtil.encrypt(clientSecret, EncryptionKey.getEncryptionKey());
        String encryptedDsmSecret = AesUtil.encrypt(dsmClientSecret, EncryptionKey.getEncryptionKey());

        TransactionWrapper.useTxn(handle -> {
            JdbiClient jdbiClient = handle.attach(JdbiClient.class);
            JdbiAuth0Tenant jdbiTenant = handle.attach(JdbiAuth0Tenant.class);
            JdbiClientUmbrellaStudy jdbiClientAcl = handle.attach(JdbiClientUmbrellaStudy.class);
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(AngioStudyCreationScript
                    .ANGIO_STUDY_GUID);
            Auth0TenantDto tenantDto = jdbiTenant.findByDomain(TENANT_DOMAIN);
            long clientId = jdbiClient.insertClient(clientName, auth0ClientId, encryptedSecret, tenantDto.getId(), redirectUrl);

            long clientStudyAclId = jdbiClientAcl.insert(clientId, studyDto.getId());

            LOG.info("Inserted client {} and gave it access to study {}", clientId, studyDto.getId());

            long dsmClientId = jdbiClient.insertClient("dsm", dsmAuth0ClientId, encryptedDsmSecret, tenantDto.getId(), null);

            LOG.info("Inserted dsm client and gave it access to study {}", studyDto.getId());
        });

    }
}
