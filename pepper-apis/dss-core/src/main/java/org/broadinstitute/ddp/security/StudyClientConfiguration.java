package org.broadinstitute.ddp.security;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyClientConfiguration {

    private long clientId;
    private String auth0Domain;
    private String auth0ClientId;
    private String auth0SigningSecret;

    @JdbiConstructor
    public StudyClientConfiguration(@ColumnName("client_id") long clientId,
                                    @ColumnName("auth0_domain") String auth0Domain,
                                    @ColumnName("auth0_client_id") String auth0ClientId,
                                    @ColumnName("auth0_signing_secret") String auth0SigningSecret) {
        this.clientId = clientId;
        this.auth0Domain = auth0Domain;
        this.auth0ClientId = auth0ClientId;
        this.auth0SigningSecret = AesUtil.decrypt(auth0SigningSecret, EncryptionKey.getEncryptionKey());
    }

    public StudyClientConfiguration() {
    }

    public String getAuth0Domain() {
        return auth0Domain;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }

    public String getAuth0SigningSecret() {
        return auth0SigningSecret;
    }

    public long getClientId() {
        return clientId;
    }
}
