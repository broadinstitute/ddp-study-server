package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

public class Auth0TenantDto {

    private String managementClientId;

    private String managementClientSecret;

    private String domain;

    private long tenantId;

    public Auth0TenantDto(@ColumnName("auth0_tenant_id") long tenantId,
                          @ColumnName("management_client_id") String managementClientId,
                          @ColumnName("management_client_secret") String managementClientSecret,
                          @ColumnName("auth0_domain") String domain) {
        this.managementClientId = managementClientId;
        this.managementClientSecret = AesUtil.decrypt(managementClientSecret, EncryptionKey.getEncryptionKey());
        this.domain = domain;
        this.tenantId = tenantId;
    }

    public long getId() {
        return tenantId;
    }

    public String getManagementClientId() {
        return managementClientId;
    }

    public String getManagementClientSecret() {
        return managementClientSecret;
    }

    public String getDomain() {
        return domain;
    }
}


