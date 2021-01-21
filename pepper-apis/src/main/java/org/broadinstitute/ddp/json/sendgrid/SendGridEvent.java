package org.broadinstitute.ddp.json.sendgrid;

import javax.validation.constraints.NotEmpty;
import java.time.Instant;


import com.google.gson.annotations.SerializedName;

public class SendGridEvent {

    @NotEmpty
    @SerializedName("email")
    private final String email;

    @NotEmpty
    @SerializedName("timestamp")
    private final Instant timestamp;

    @NotEmpty
    @SerializedName("event")
    private final String eventType;

    @SerializedName("url")
    private final String url;

    @SerializedName("id")
    private final String ip;

    @NotEmpty
    @SerializedName("sg_event_id")
    private final String sgEventId;

    @SerializedName("sg_message_id")
    private final String sgMessageId;

    @SerializedName("response")
    private final String response;

    @SerializedName("reason")
    private final String reason;

    @SerializedName("status")
    private final String status;

    @SerializedName("attempt")
    private final int attempt;

    @SerializedName("smtp-id")
    private final String smtpId;


    public SendGridEvent(String email, Instant timestamp, String eventType, String url, String ip, String sgEventId,
                         String sgMessageId, String response, String reason, String status, int attempt, String smtpId) {
        this.email = email;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.url = url;
        this.ip = ip;
        this.sgEventId = sgEventId;
        this.sgMessageId = sgMessageId;
        this.response = response;
        this.reason = reason;
        this.status = status;
        this.attempt = attempt;
        this.smtpId = smtpId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public String getUrl() {
        return url;
    }

    public String getIp() {
        return ip;
    }

    public String getSgEventId() {
        return sgEventId;
    }

    public String getSgMessageId() {
        return sgMessageId;
    }

    public String getResponse() {
        return response;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public String getSmtpId() {
        return smtpId;
    }
}
