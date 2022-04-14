package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;

public class ParticipantUser {

    @SerializedName("datstatparticipantdata")
    private DatstatParticipantData datstatparticipantdata;
    @SerializedName("datstatsurveydata")
    private DatstatSurveyData datstatsurveydata;
    @SerializedName("source_email")
    private String sourceEmail;
    @SerializedName("source_first_name")
    private String sourceFirstName;
    @SerializedName("source_last_name")
    private String sourceLastName;

    private String originalSourceEmail; //place holder to save original source_email value for dryrun generated Altpid

    public DatstatParticipantData getDatstatparticipantdata() {
        return datstatparticipantdata;
    }

    public void setDatstatparticipantdata(DatstatParticipantData datstatparticipantdata) {
        this.datstatparticipantdata = datstatparticipantdata;
    }

    public DatstatSurveyData getDatstatsurveydata() {
        return datstatsurveydata;
    }

    public void setDatstatsurveydata(DatstatSurveyData datstatsurveydata) {
        this.datstatsurveydata = datstatsurveydata;
    }

    public String getSourceEmail() {
        return sourceEmail;
    }

    public void setSourceEmail(String sourceEmail) {
        this.sourceEmail = sourceEmail;
    }

    public String getSourceFirstName() {
        return sourceFirstName;
    }

    public void setSourceFirstName(String sourceFirstName) {
        this.sourceFirstName = sourceFirstName;
    }

    public String getSourceLastName() {
        return sourceLastName;
    }

    public void setSourceLastName(String sourceLastName) {
        this.sourceLastName = sourceLastName;
    }

    public String getOriginalSourceEmailHash() {
        if (StringUtils.isNotBlank(originalSourceEmail)) {
            return String.valueOf(originalSourceEmail.hashCode());
        } else {
            return String.valueOf(sourceEmail.hashCode());
        }
    }

    public void setOriginalSourceEmail(String originalSourceEmail) {
        this.originalSourceEmail = originalSourceEmail;
    }
}
