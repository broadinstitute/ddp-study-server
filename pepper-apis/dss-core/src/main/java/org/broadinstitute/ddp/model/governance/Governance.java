package org.broadinstitute.ddp.model.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents the governance relationship of a user and their proxy, along with the studies they have granted to the proxy.
 */
@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class Governance {
    @ColumnName("user_governance_id")
    long id;

    @ColumnName("alias")
    String alias;

    @ColumnName("operator_user_id")
    long proxyUserId;

    @ColumnName("operator_user_guid")
    String proxyUserGuid;

    @ColumnName("participant_user_id")
    long governedUserId;

    @ColumnName("participant_user_guid")
    String governedUserGuid;

    @ColumnName("is_active")
    @Accessors(fluent = true)
    boolean isActive;

    List<GrantedStudy> grantedStudies = new ArrayList<>();

    public void addGrantedStudy(final GrantedStudy study) {
        Optional.ofNullable(study).ifPresent(grantedStudies::add);
    }

    public List<GrantedStudy> getGrantedStudies() {
        return List.copyOf(grantedStudies);
    }
}
