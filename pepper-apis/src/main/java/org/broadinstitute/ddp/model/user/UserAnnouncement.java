package org.broadinstitute.ddp.model.user;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.content.Renderable;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class UserAnnouncement implements Renderable {

    private transient long id;
    private transient long participantUserId;
    private transient long studyId;
    private transient long msgTemplateId;
    private transient long createdAt;

    @SerializedName("guid")
    private String guid;

    @SerializedName("permanent")
    private boolean isPermanent;

    @SerializedName("message")
    private String message;

    @JdbiConstructor
    public UserAnnouncement(@ColumnName("user_announcement_id") long id,
                            @ColumnName("guid") String guid,
                            @ColumnName("participant_user_id") long participantUserId,
                            @ColumnName("study_id") long studyId,
                            @ColumnName("message_template_id") long msgTemplateId,
                            @ColumnName("is_permanent") boolean isPermanent,
                            @ColumnName("created_at") long createdAt) {
        this.id = id;
        this.guid = guid;
        this.participantUserId = participantUserId;
        this.studyId = studyId;
        this.msgTemplateId = msgTemplateId;
        this.isPermanent = isPermanent;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
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

    public boolean isPermanent() {
        return isPermanent;
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
