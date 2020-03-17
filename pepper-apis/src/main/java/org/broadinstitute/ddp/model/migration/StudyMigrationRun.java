package org.broadinstitute.ddp.model.migration;

public class StudyMigrationRun {

    private String altPid;
    private String pepperUserGuid;
    private Boolean hasAboutYou;
    private Boolean hasConsent;
    private Boolean hasBloodConsent;
    private Boolean hasTissueConsent;
    private Boolean hasRelease;
    private Boolean hasBloodRelease;
    private Boolean hasLovedOne;
    private Boolean hasFollowup;
    private Boolean hasPrionConsent;
    private Boolean hasMedical;
    private Boolean isSuccess;
    private Boolean previousRun;
    private String emailAddress;
    private Boolean auth0Collision;
    private Boolean isPrion;

    public StudyMigrationRun(String altPid, String pepperUserGuid, Boolean previousRun, String emailAddress) {
        this.altPid = altPid;
        this.pepperUserGuid = pepperUserGuid;
        this.previousRun = previousRun;
        this.emailAddress = emailAddress;
    }

    public StudyMigrationRun(String altPid, String pepperUserGuid, Boolean hasAboutYou, Boolean hasConsent, Boolean hasBloodConsent,
                             Boolean hasTissueConsent, Boolean hasRelease, Boolean hasBloodRelease, Boolean hasLovedOne,
                             Boolean hasFollowup, Boolean isSuccess, Boolean previousRun, String emailAddress, Boolean auth0Collision) {
        this.altPid = altPid;
        this.pepperUserGuid = pepperUserGuid;
        this.hasAboutYou = hasAboutYou;
        this.hasConsent = hasConsent;
        this.hasBloodConsent = hasBloodConsent;
        this.hasTissueConsent = hasTissueConsent;
        this.hasRelease = hasRelease;
        this.hasBloodRelease = hasBloodRelease;
        this.hasLovedOne = hasLovedOne;
        this.hasFollowup = hasFollowup;
        this.isSuccess = isSuccess;
        this.previousRun = previousRun;
        this.emailAddress = emailAddress;
        this.auth0Collision = auth0Collision;
    }

    public StudyMigrationRun(String altPid, String pepperUserGuid, Boolean hasPrionConsent, Boolean hasMedical,
                             Boolean isSuccess, Boolean previousRun,
                             String emailAddress, Boolean auth0Collision) {
        this.altPid = altPid;
        this.pepperUserGuid = pepperUserGuid;
        this.hasPrionConsent = hasPrionConsent;
        this.hasMedical = hasMedical;
        this.isSuccess = isSuccess;
        this.previousRun = previousRun;
        this.emailAddress = emailAddress;
        this.auth0Collision = auth0Collision;
        this.isPrion = true;
    }

    public String getAltPid() {
        return altPid;
    }

    public String getPepperUserGuid() {
        return pepperUserGuid;
    }

    public Boolean getIsPrion() {
        return isPrion;
    }

    public Boolean getHasPrionConsent() {
        return hasPrionConsent;
    }

    public Boolean getHasMedical() {
        return hasMedical;
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

    public Boolean getHasBloodConsent() {
        return hasBloodConsent;
    }

    public Boolean getHasTissueConsent() {
        return hasTissueConsent;
    }

    public Boolean getHasBloodRelease() {
        return hasBloodRelease;
    }
}
