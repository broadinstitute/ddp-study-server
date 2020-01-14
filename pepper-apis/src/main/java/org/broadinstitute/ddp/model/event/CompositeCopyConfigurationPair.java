package org.broadinstitute.ddp.model.event;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CompositeCopyConfigurationPair {

    private long id;
    private long copyConfigPairId;
    private long sourceChildQuestionStableCodeId;
    private String sourceChildQuestionStableId;
    private long targetChildQuestionStableCodeId;
    private String targetChildQuestionStableId;

    @JdbiConstructor
    public CompositeCopyConfigurationPair(
            @ColumnName("composite_copy_configuration_pair_id") long id,
            @ColumnName("copy_configuration_pair_id") long copyConfigPairId,
            @ColumnName("source_child_question_stable_code_id") long sourceChildQuestionStableCodeId,
            @ColumnName("source_child_question_stable_id") String sourceChildQuestionStableId,
            @ColumnName("target_child_question_stable_code_id") long targetChildQuestionStableCodeId,
            @ColumnName("target_child_question_stable_id") String targetChildQuestionStableId) {
        this.id = id;
        this.copyConfigPairId = copyConfigPairId;
        this.sourceChildQuestionStableCodeId = sourceChildQuestionStableCodeId;
        this.sourceChildQuestionStableId = sourceChildQuestionStableId;
        this.targetChildQuestionStableCodeId = targetChildQuestionStableCodeId;
        this.targetChildQuestionStableId = targetChildQuestionStableId;
    }

    public CompositeCopyConfigurationPair(String sourceChildQuestionStableId, String targetChildQuestionStableId) {
        this.sourceChildQuestionStableId = sourceChildQuestionStableId;
        this.targetChildQuestionStableId = targetChildQuestionStableId;
    }

    public long getId() {
        return id;
    }

    public long getCopyConfigPairId() {
        return copyConfigPairId;
    }

    public long getSourceChildQuestionStableCodeId() {
        return sourceChildQuestionStableCodeId;
    }

    public String getSourceChildQuestionStableId() {
        return sourceChildQuestionStableId;
    }

    public long getTargetChildQuestionStableCodeId() {
        return targetChildQuestionStableCodeId;
    }

    public String getTargetChildQuestionStableId() {
        return targetChildQuestionStableId;
    }
}
