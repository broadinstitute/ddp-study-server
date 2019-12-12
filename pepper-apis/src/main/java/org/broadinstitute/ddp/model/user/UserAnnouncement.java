package org.broadinstitute.ddp.model.user;

import java.beans.ConstructorProperties;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;

public class UserAnnouncement implements Renderable {

    private transient long id;
    private transient long participantUserId;
    private transient long studyId;
    private transient long msgTemplateId;
    private transient long createdAt;

    @SerializedName("message")
    private String message;

    @ConstructorProperties({"id", "participant_user_id", "study_id", "message_template_id", "created_at"})
    public UserAnnouncement(long id, long participantUserId, long studyId, long msgTemplateId, long createdAt) {
        this.id = id;
        this.participantUserId = participantUserId;
        this.studyId = studyId;
        this.msgTemplateId = msgTemplateId;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getParticipantUserId() {
        return participantUserId;
    }

    public long getStudyId() {
        return studyId;
    }

    public long getMsgTemplateId() {
        return msgTemplateId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        registry.accept(msgTemplateId);
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        message = rendered.get(msgTemplateId);
        if (message == null) {
            throw new NoSuchElementException("No rendered template found for message template with id " + msgTemplateId);
        }

        if (style == ContentStyle.BASIC) {
            message = HtmlConverter.getPlainText(message);
        }
    }
}
