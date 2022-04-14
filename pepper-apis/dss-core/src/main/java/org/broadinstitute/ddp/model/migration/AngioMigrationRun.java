package org.broadinstitute.ddp.model.migration;

public class AngioMigrationRun {

    private String altPid;
    private String pepperUserGuid;
    private Boolean hasAboutYou;
    private Boolean hasConsent;
    private Boolean hasRelease;
    private Boolean hasLovedOne;
    private Boolean hasFollowup;
    private Boolean isSuccess;
    private Boolean previousRun;
    private String emailAddress;
    private Boolean auth0Collision;

    public AngioMigrationRun(String altPid, String pepperUserGuid, Boolean previousRun, String emailAddress) {
        this.altPid = altPid;
        this.pepperUserGuid = pepperUserGuid;
        this.previousRun = previousRun;
        this.emailAddress = emailAddress;
    }

    public AngioMigrationRun(String altPid, String pepperUserGuid, Boolean hasAboutYou,
                             Boolean hasConsent, Boolean hasRelease, Boolean hasLovedOne, Boolean hasFollowup,
                             Boolean isSuccess, Boolean previousRun, String emailAddress, Boolean auth0Collision) {
        this.altPid = altPid;
        this.pepperUserGuid = pepperUserGuid;
        this.hasAboutYou = hasAboutYou;
        this.hasConsent = hasConsent;
        this.hasRelease = hasRelease;
        this.hasLovedOne = hasLovedOne;
        this.hasFollowup = hasFollowup;
        this.isSuccess = isSuccess;
        this.previousRun = previousRun;
        this.emailAddress = emailAddress;
        this.auth0Collision = auth0Collision;
    }


    public String getAltPid() {
        return altPid;
    }

    public String getPepperUserGuid() {
        return pepperUserGuid;
    }

    public Boolean getHasAboutYou() {
        return hasAboutYou;
    }

    public Boolean getHasConsent() {
        return hasConsent;
    }

    public Boolean getHasRelease() {
        return hasRelease;
    }

    public Boolean getHasLovedOne() {
        return hasLovedOne;
    }

    public Boolean getHasFollowup() {
        return hasFollowup;
    }

    public Boolean getSuccess() {
        return isSuccess;
    }

    public Boolean getPreviousRun() {
        return previousRun;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public Boolean getAuth0Collision() {
        return auth0Collision;
    }
}
