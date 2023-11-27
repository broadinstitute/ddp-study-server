package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class DatstatParticipantData {
    @SerializedName("ddp_city")
    private String ddpCity;

    @SerializedName("ddp_exited")
    private String ddpExited;

    @SerializedName("ddp_spit_kit_request_id")
    private String ddpSpitKitRequestId;

    @SerializedName("ddp_street2")
    private String ddpStreet2;

    @SerializedName("ddp_address_valid")
    private String ddpAddressValid;

    @SerializedName("ddp_postal_code")
    private String ddpPostalCode;

    @SerializedName("ddp_current_status")
    private String ddpCurrentStatus;

    @SerializedName("ddp_participant_shortid")
    private String ddpParticipantShortid;

    @SerializedName("ddp_address_checked")
    private String ddpAddressChecked;

    @SerializedName("ddp_street1")
    private String ddpStreet1;

    @SerializedName("ddp_portal_url")
    private String ddpPortalUrl;

    @SerializedName("ddp_created")
    private String ddpCreated;

    @SerializedName("ddp_state")
    private String ddpState;

    @SerializedName("ddp_country")
    private String ddpCountry;

    @SerializedName("ddp_do_not_contact")
    private Integer ddpDoNotContact;

    @SerializedName("datstat_altpid")
    private String datstatAltpid;

    @SerializedName("datstat_lastmodified")
    private String datstatLastmodified;

    @SerializedName("datstat_firstname")
    private String datstatFirstname;

    @SerializedName("datstat_lastname")
    private String datstatLastname;

    @SerializedName("datstat_email")
    private String datstatEmail;
}
