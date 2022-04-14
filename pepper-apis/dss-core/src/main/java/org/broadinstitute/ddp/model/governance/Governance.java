package org.broadinstitute.ddp.model.governance;

import java.util.ArrayList;
import java.util.List;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents the governance relationship of a user and their proxy, along with the studies they have granted to the proxy.
 */
public class Governance {

    private long id;
    private String alias;
    private long proxyUserId;
    private String proxyUserGuid;
    private long governedUserId;
    private String governedUserGuid;
    private boolean isActive;

    private List<GrantedStudy> grantedStudies = new ArrayList<>();

    @JdbiConstructor
    public Governance(@ColumnName("user_governance_id") long id,
                      @ColumnName("alias") String alias,
                      @ColumnName("operator_user_id") long proxyUserId,
                      @ColumnName("operator_user_guid") String proxyUserGuid,
                      @ColumnName("participant_user_id") long governedUserId,
                      @ColumnName("participant_user_guid") String governedUserGuid,
                      @ColumnName("is_active") boolean isActive) {
        this.id = id;
        this.alias = alias;
        this.proxyUserId = proxyUserId;
        this.proxyUserGuid = proxyUserGuid;
        this.governedUserId = governedUserId;
        this.governedUserGuid = governedUserGuid;
        this.isActive = isActive;
    }

    public long getId() {
        return id;
    }

    public String getAlias() {
        return alias;
    }

    public long getProxyUserId() {
        return proxyUserId;
    }

    public String getProxyUserGuid() {
        return proxyUserGuid;
    }

    public long getGovernedUserId() {
        return governedUserId;
    }

    public String getGovernedUserGuid() {
        return governedUserGuid;
    }

    public boolean isActive() {
        return isActive;
    }

    public void addGrantedStudy(GrantedStudy study) {
        if (study != null) {
            grantedStudies.add(study);
        }
    }

    public List<GrantedStudy> getGrantedStudies() {
        return List.copyOf(grantedStudies);
    }
}
