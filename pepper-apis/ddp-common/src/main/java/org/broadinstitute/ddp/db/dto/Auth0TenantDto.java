package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class Auth0TenantDto {
    @ColumnName("auth0_tenant_id")
    long id;

    @ColumnName("management_client_id")
    String managementClientId;
    
    @ColumnName("management_client_secret")
    String managementClientSecret;

    @ColumnName("auth0_domain")
    String domain;

    public String getManagementClientSecret() {
        return AesUtil.decrypt(managementClientSecret, EncryptionKey.getEncryptionKey());
    }
}


