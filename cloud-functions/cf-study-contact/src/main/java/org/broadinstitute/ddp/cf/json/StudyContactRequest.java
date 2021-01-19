package org.broadinstitute.ddp.cf.json;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class StudyContactRequest {

    @SerializedName("g-recaptcha-response")
    private final String captchaToken;

    @SerializedName("data")
    private final Map<String, String> data;

    @SerializedName("attachment")
    private final Attachment attachment;

    @SerializedName("clientId")
    private final String clientId;

    @SerializedName("domain")
    private final String domain;

    public StudyContactRequest(String captchaToken, Map<String, String> data, Attachment attachment, String clientId, String domain) {
        this.captchaToken = captchaToken;
        this.data = data;
        this.attachment = attachment;
        this.clientId = clientId;
        this.domain = domain;
    }

    public String getCaptchaToken() {
        return captchaToken;
    }

    public Map<String, String> getData() {
        return data;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public String getClientId() {
        return clientId;
    }

    public String getDomain() {
        return domain;
    }
}