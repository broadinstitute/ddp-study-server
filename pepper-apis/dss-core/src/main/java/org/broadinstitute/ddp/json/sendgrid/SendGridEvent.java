package org.broadinstitute.ddp.json.sendgrid;

import javax.validation.constraints.NotEmpty;
import java.time.Instant;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.transformers.UnixTimestampToInstantAdapter;

@Value
@AllArgsConstructor
public class SendGridEvent {
    @NotEmpty
    @SerializedName("email")
    String email;

    @NotEmpty
    @JsonAdapter(UnixTimestampToInstantAdapter.class)
    @SerializedName("timestamp")
    Instant timestamp;

    @NotEmpty
    @SerializedName("event")
    String eventType;

    @SerializedName("url")
    String url;

    @SerializedName("id")
    String ip;

    @NotEmpty
    @SerializedName("sg_event_id")
    String sgEventId;

    @SerializedName("sg_message_id")
    String sgMessageId;

    @SerializedName("response")
    String response;

    @SerializedName("reason")
    String reason;

    @SerializedName("status")
    String status;

    @SerializedName("attempt")
    int attempt;

    @SerializedName("smtp-id")
    String smtpId;
}
