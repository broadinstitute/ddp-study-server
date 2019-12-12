package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.security.AesUtil;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ClientDto {

    private long id;
    private String name;
    private String auth0ClientId;
    private String auth0EncryptedSecret;
    private String webPasswordRedirectUrl;
    private boolean isRevoked;
    private long auth0TenantId;

    @JdbiConstructor
    public ClientDto(@ColumnName("client_id") long id,
                     @ColumnName("client_name") String name,
                     @ColumnName("auth0_client_id") String auth0ClientId,
                     @ColumnName("auth0_signing_secret") String auth0EncryptedSecret,
                     @ColumnName("web_password_redirect_url") String webPasswordRedirectUrl,
                     @ColumnName("is_revoked") boolean isRevoked,
                     @ColumnName("auth0_tenant_id") long auth0TenantId) {
        this.id = id;
        this.name = name;
        this.auth0ClientId = auth0ClientId;
        this.auth0EncryptedSecret = auth0EncryptedSecret;
        this.webPasswordRedirectUrl = webPasswordRedirectUrl;
        this.isRevoked = isRevoked;
        this.auth0TenantId = auth0TenantId;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }

    public String getAuth0EncryptedSecret() {
        return auth0EncryptedSecret;
    }

    public String getAuth0DecryptedSecret(String encryptionKey) {
        return AesUtil.decrypt(auth0EncryptedSecret, encryptionKey);
    }

    public String getWebPasswordRedirectUrl() {
        return webPasswordRedirectUrl;
    }

    public boolean isRevoked() {
        return isRevoked;
    }

    public long getAuth0TenantId() {
        return auth0TenantId;
    }
}
