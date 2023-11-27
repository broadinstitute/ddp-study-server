package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public final class ParticipantUser {
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

    public String getOriginalSourceEmailHash() {
        if (StringUtils.isNotBlank(originalSourceEmail)) {
            return String.valueOf(originalSourceEmail.hashCode());
        } else {
            return String.valueOf(sourceEmail.hashCode());
        }
    }
}
