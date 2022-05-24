package org.broadinstitute.ddp.model.migration;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@AllArgsConstructor
public class StudyMigrationRun {
    String altPid;
    String pepperUserGuid;
    Boolean hasAboutYou;
    Boolean hasConsent;
    Boolean hasBloodConsent;
    Boolean hasTissueConsent;
    Boolean hasRelease;
    Boolean hasBloodRelease;
    Boolean hasLovedOne;
    Boolean hasFollowup;
    Boolean hasMedical;
    
    @Accessors(fluent = true)
    Boolean isSuccess;
    Boolean previousRun;
    String emailAddress;
    Boolean auth0Collision;
    Boolean foundInAuth0;

    public StudyMigrationRun(String altPid, String pepperUserGuid, Boolean previousRun, String emailAddress) {
        this(altPid, pepperUserGuid, null, null, null, null, 
                null, null, null, null, null, null, 
                previousRun, emailAddress, null, null);
    }
}
