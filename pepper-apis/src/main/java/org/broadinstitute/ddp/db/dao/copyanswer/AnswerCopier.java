package org.broadinstitute.ddp.db.dao.copyanswer;

import org.jdbi.v3.core.Handle;

public interface AnswerCopier {

    /**
     * Copies the answer to one question in an activity instance
     * to a potentially different question in a different activity instance.
     */
    AnswerCopyResult copyAnswer(Handle handle,
                                long sourceInstanceId, String sourceQuestionStableId,
                                long destinationInstanceId, String destinationQuestionStableId,
                                long createdAtEpochMillis,
                                long lastUpdatedAtEpochMillis,
                                CompositeAnswerCopyConfiguration compositeCopyConfiguration);
}
