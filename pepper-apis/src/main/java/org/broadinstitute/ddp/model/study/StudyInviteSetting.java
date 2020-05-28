package org.broadinstitute.ddp.model.study;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyInviteSetting {

    private long studyId;
    private Long inviteErrorTemplateId;

    @JdbiConstructor
    public StudyInviteSetting(
            @ColumnName("umbrella_study_id") long studyId,
            @ColumnName("invite_error_template_id") Long inviteErrorTemplateId) {
        this.studyId = studyId;
        this.inviteErrorTemplateId = inviteErrorTemplateId;
    }

    public long getStudyId() {
        return studyId;
    }

    public Long getInviteErrorTemplateId() {
        return inviteErrorTemplateId;
    }
}
