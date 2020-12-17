package org.broadinstitute.ddp.json.studyemail;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class SendStudyEmailPayload {

    @NotEmpty
    @SerializedName("data")
    private Map<String, String> data;

    @SerializedName("attachments")
    private List<Attachment> attachments;

    public SendStudyEmailPayload(Map<String, String> data, List<Attachment> attachments) {
        this.data = data;
        this.attachments = attachments;
    }

    public Map<String, String> getData() {
        return data;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }
}
