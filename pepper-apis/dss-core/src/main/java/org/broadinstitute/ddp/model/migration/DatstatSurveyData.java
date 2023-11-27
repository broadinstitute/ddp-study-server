package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class DatstatSurveyData {
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
}
