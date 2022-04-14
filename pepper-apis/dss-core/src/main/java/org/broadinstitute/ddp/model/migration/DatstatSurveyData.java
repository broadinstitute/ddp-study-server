package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;

public class DatstatSurveyData {

    @SerializedName("releasesurvey")
    private ReleaseSurvey releaseSurvey;
    @SerializedName("consentsurvey")
    private ConsentSurvey consentSurvey;
    @SerializedName("aboutyousurvey")
    private AboutYouSurvey aboutYouSurvey;
    @SerializedName("followupconsentsurvey")
    private FollowupConsentSurvey followupConsentSurvey;
    @SerializedName("lovedonesurvey")
    private LovedOneSurvey lovedOneSurvey;

    public ReleaseSurvey getReleaseSurvey() {
        return releaseSurvey;
    }

    public void setReleaseSurvey(ReleaseSurvey releaseSurvey) {
        this.releaseSurvey = releaseSurvey;
    }

    public ConsentSurvey getConsentSurvey() {
        return consentSurvey;
    }

    public void setConsentSurvey(ConsentSurvey consentSurvey) {
        this.consentSurvey = consentSurvey;
    }

    public AboutYouSurvey getAboutYouSurvey() {
        return aboutYouSurvey;
    }

    public void setAboutYouSurvey(AboutYouSurvey aboutyousurvey) {
        this.aboutYouSurvey = aboutyousurvey;
    }

    public FollowupConsentSurvey getFollowupConsentSurvey() {
        return followupConsentSurvey;
    }

    public void setFollowupConsentSurvey(FollowupConsentSurvey followupConsentSurvey) {
        this.followupConsentSurvey = followupConsentSurvey;
    }

    public LovedOneSurvey getLovedOneSurvey() {
        return lovedOneSurvey;
    }

    public void setLovedOneSurvey(LovedOneSurvey lovedOneSurvey) {
        this.lovedOneSurvey = lovedOneSurvey;
    }
}
