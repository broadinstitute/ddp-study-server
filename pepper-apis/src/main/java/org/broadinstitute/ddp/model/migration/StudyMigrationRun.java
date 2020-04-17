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
    private Boolean isSuccess;
    private Boolean previousRun;
    private String emailAddress;
    private Boolean auth0Collision;
    private Boolean foundInAuth0;
    private Boolean isPrion;
    private Boolean hasPrionConsent;
    private Boolean hasPrionMedical;

    public StudyMigrationRun(String altPid, String pepperUserGuid, Boolean previousRun, String emailAddress,
                             String studyGuid) {
        this.altPid = altPid;
        this.pepperUserGuid = pepperUserGuid;
        this.previousRun = previousRun;
        this.emailAddress = emailAddress;
        isPrion = "PRION".equals(studyGuid);
    }

    public StudyMigrationRun(String altpid, String pepperUserGuid, Boolean hasAboutYou, Boolean hasConsent, Boolean hasBloodConsent,
                             Boolean hasTissueConsent, Boolean hasRelease, Boolean hasBloodRelease, Boolean hasLovedOne,
                             Boolean hasFollowup, Boolean isSuccess, Boolean previousRun, String emailAddress,
                             Boolean auth0Collision, Boolean hasPrionConsent, Boolean hasPrionMedical, Boolean foundInAuth0,
                             String studyGuid) {
        this.altPid = altpid;
        this.pepperUserGuid = pepperUserGuid;
        this.isSuccess = isSuccess;
        this.previousRun = previousRun;
        this.emailAddress = emailAddress;

        if ("PRION".equals(studyGuid)) {
            this.isPrion = true;
            this.hasAboutYou = false;
            this.hasConsent = false;
            this.hasBloodConsent = false;
            this.hasTissueConsent = false;
            this.hasRelease = false;
            this.hasBloodRelease = false;
            this.hasLovedOne = false;
            this.hasFollowup = false;
            this.hasPrionConsent = hasPrionConsent;
            this.hasPrionMedical = hasPrionMedical;
            this.auth0Collision = false;
            this.foundInAuth0 = foundInAuth0;
        } else {
            this.isPrion = false;
            this.hasAboutYou = hasAboutYou;
            this.hasConsent = hasConsent;
            this.hasBloodConsent = hasBloodConsent;
            this.hasTissueConsent = hasTissueConsent;
            this.hasRelease = hasRelease;
            this.hasBloodRelease = hasBloodRelease;
            this.hasLovedOne = hasLovedOne;
            this.hasFollowup = hasFollowup;
            this.hasPrionConsent = false;
            this.hasPrionMedical = false;
            this.auth0Collision = auth0Collision;
            this.foundInAuth0 = false;
        }
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

    public Boolean getHasPrionMedical() {
        return hasPrionMedical;
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

    public Boolean getFoundInAuth0() {
        return foundInAuth0;
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
