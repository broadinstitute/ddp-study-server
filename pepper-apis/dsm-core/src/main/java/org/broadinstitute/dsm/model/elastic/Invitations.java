package org.broadinstitute.dsm.model.elastic;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Getter
public class Invitations {

    @SerializedName(ESObjectConstants.TYPE)
    private String type;
    @SerializedName(ESObjectConstants.GUID)
    private String guid;
    @SerializedName(ESObjectConstants.CREATED_AT)
    private Long createdAt;
    @SerializedName(ESObjectConstants.VOIDED_AT)
    private Long voidedAt;
    @SerializedName(ESObjectConstants.VERIFIED_AT)
    private Long verifiedAt;
    @SerializedName(ESObjectConstants.ACCEPTED_AT)
    private Long acceptedAt;
    @SerializedName(ESObjectConstants.CONTACT_EMAIL)
    private String contactEmail;
    @SerializedName(ESObjectConstants.NOTES)
    private String notes;

}
