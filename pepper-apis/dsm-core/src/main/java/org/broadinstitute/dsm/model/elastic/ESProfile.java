package org.broadinstitute.dsm.model.elastic;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Data
public class ESProfile {
    @SerializedName(ESObjectConstants.GUID)
    private String participantGuid;

    @SerializedName(ESObjectConstants.HRUID)
    private String hruid;

    @SerializedName(ESObjectConstants.LEGACY_ALTPID)
    private String participantLegacyAltPid;

    @SerializedName(ESObjectConstants.LEGACY_SHORTID)
    private String participantLegacyShortId;

    @SerializedName(ESObjectConstants.FIRST_NAME)
    private String firstName;

    @SerializedName(ESObjectConstants.LAST_NAME)
    private String lastName;

    @SerializedName(ESObjectConstants.EMAIL)
    private String email;

    @SerializedName(ESObjectConstants.PREFERED_LANGUAGE)
    private String preferredLanguage;

    @SerializedName(ESObjectConstants.DO_NOT_CONTACT)
    private boolean doNotContact;

    @SerializedName(ESObjectConstants.CREATED_AT)
    private long createdAt;
}
