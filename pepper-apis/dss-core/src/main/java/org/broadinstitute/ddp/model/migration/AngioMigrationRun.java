package org.broadinstitute.ddp.model.migration;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@AllArgsConstructor
public class AngioMigrationRun {
    String altPid;
    String pepperUserGuid;
    Boolean hasAboutYou;
    Boolean hasConsent;
    Boolean hasRelease;
    Boolean hasLovedOne;
    Boolean hasFollowup;

    @Accessors(fluent = true)
    Boolean isSuccess;
    Boolean previousRun;
    String emailAddress;
    Boolean auth0Collision;

    public AngioMigrationRun(String altPid, String pepperUserGuid, Boolean previousRun, String emailAddress) {
        this(altPid, pepperUserGuid, null, null, null, null, null, null, previousRun, emailAddress, null);
    }
}
