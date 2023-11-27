package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.security.AesUtil;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class ClientDto {
    @ColumnName("client_id")
    long id;

    @ColumnName("auth0_client_id")
    String auth0ClientId;

    @ColumnName("auth0_signing_secret")
    String auth0EncryptedSecret;

    @ColumnName("web_password_redirect_url")
    String webPasswordRedirectUrl;

    @ColumnName("is_revoked")
    boolean revoked;

    @ColumnName("auth0_tenant_id")
    long auth0TenantId;

    @ColumnName("auth0_domain")
    String auth0Domain;

    public String getAuth0DecryptedSecret(final String encryptionKey) {
        return AesUtil.decrypt(auth0EncryptedSecret, encryptionKey);
    }
}
