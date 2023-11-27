package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public final class ParticipantData {
    @SerializedName("user")
    private ParticipantUser participantUser;
}
