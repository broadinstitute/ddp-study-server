package org.broadinstitute.ddp.db.dao.copyanswer;

import org.broadinstitute.ddp.db.dao.ActivityAnswerCopierSql;
import org.jdbi.v3.core.Handle;

public class BooleanAnswerCopier extends BaseAnswerCopier {

    @Override
    public AnswerCopyResult copyAnswer(Handle handle, long sourceInstanceId, String sourceQuestionStableId, long destinationInstanceId,
                                       String destinationQuestionStableId, long createdAtEpochMillis, long lastUpdatedAtEpochMillis,
                                       CompositeAnswerCopyConfiguration compositeCopyConfiguration) {
        AnswerCopyResult baseCopyResult = super.copyAnswer(handle, sourceInstanceId, sourceQuestionStableId, destinationInstanceId,
                destinationQuestionStableId, createdAtEpochMillis, lastUpdatedAtEpochMillis, compositeCopyConfiguration);

        handle.attach(ActivityAnswerCopierSql.class).copyBooleanAnswer(baseCopyResult.getSourceAnswerId(),
                baseCopyResult.getDestinationAnswerId());

        return baseCopyResult;
    }
}
