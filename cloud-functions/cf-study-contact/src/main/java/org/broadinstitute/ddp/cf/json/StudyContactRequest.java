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

    public StudyContactRequest(String captchaToken, Map<String, String> data, Attachment attachment) {
        this.captchaToken = captchaToken;
        this.data = data;
        this.attachment = attachment;
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
}