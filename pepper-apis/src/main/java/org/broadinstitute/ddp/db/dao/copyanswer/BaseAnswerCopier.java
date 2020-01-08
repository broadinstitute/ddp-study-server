package org.broadinstitute.ddp.db.dao.copyanswer;

import org.broadinstitute.ddp.db.dao.ActivityAnswerCopierSql;
import org.jdbi.v3.core.Handle;

/**
 * Copies top-level answer and composite_answer_item (when directed by compositeCopyConfig)
 */
public abstract class BaseAnswerCopier implements AnswerCopier {


    public AnswerCopyResult copyAnswer(Handle handle, long sourceInstanceId, String sourceQuestionStableId, long destinationInstanceId,
                                       String destinationQuestionStableId, long createdAtEpochMillis, long lastUpdatedAtEpochMillis,
                                       CompositeAnswerCopyConfiguration compositeCopyConfig) {
        return handle.attach(ActivityAnswerCopierSql.class).copyBaseAnswer(
                sourceQuestionStableId,
                destinationQuestionStableId,
                sourceInstanceId,
                destinationInstanceId,
                createdAtEpochMillis,
                lastUpdatedAtEpochMillis);
    }
}
