package org.broadinstitute.ddp.studybuilder.task.ddp3934.util.model;

import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class QuestionMetadata {
    public long id;
    public String stableIdentifier;
    public String activityCode;
    public QuestionType type;
    public long revision;
    public Long placeholderTemplateId;

    @JdbiConstructor
    public QuestionMetadata(@ColumnName("question_id") long id,
                    @ColumnName("stable_id") String identifier,
                    @ColumnName("study_activity_code") String activityCode,
                    @ColumnName("question_type_code") String type,
                    @ColumnName("revision_id") long revision,
                    @ColumnName("placeholder_template_id") Long placeholderTemplateId) {
        this.id = id;
        this.stableIdentifier = identifier;
        this.activityCode = activityCode;
        this.type = QuestionType.valueOf(type);
        this.revision = revision;
        this.placeholderTemplateId = placeholderTemplateId;
    }
}
