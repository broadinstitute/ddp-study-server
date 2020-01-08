package org.broadinstitute.ddp.db.dao.copyanswer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityAnswerCopierSql;
import org.broadinstitute.ddp.db.dao.JdbiCompositeAnswer;
import org.broadinstitute.ddp.db.dto.CompositeAnswerSummaryDto;
import org.jdbi.v3.core.Handle;

public class CompositeAnswerCopier extends BaseAnswerCopier {

    @Override
    public AnswerCopyResult copyAnswer(Handle handle,
                                       long sourceInstanceId, String sourceQuestionStableId,
                                       long destinationInstanceId, String destinationQuestionStableId,
                                       long createdAtEpochMillis,
                                       long lastUpdatedAtEpochMillis,
                                       CompositeAnswerCopyConfiguration compositeCopyConfiguration) {

        AnswerCopyResult parentCopyResult = super.copyAnswer(handle,
                sourceInstanceId, sourceQuestionStableId,
                destinationInstanceId, destinationQuestionStableId,
                createdAtEpochMillis,
                lastUpdatedAtEpochMillis,
                compositeCopyConfiguration);

        long sourceParentAnswerId = parentCopyResult.getSourceAnswerId();
        // copy child answers
        ActivityAnswerCopierSql answerCopier = handle.attach(ActivityAnswerCopierSql.class);
        JdbiCompositeAnswer jdbiCompositeAnswer = handle.attach(JdbiCompositeAnswer.class);
        List<AnswerCopyResult> childAnswerCopyResults = new ArrayList<>();

        // cache the response_order for subsequent copying
        JdbiCompositeAnswer jdbiCompAnswer = handle.attach(JdbiCompositeAnswer.class);
        Optional<CompositeAnswerSummaryDto> sourceChildAnswers = jdbiCompAnswer.findCompositeAnswerSummary(sourceParentAnswerId);

        Map<Long, Integer> answerIdToOrderIndex = new HashMap<>();
        sourceChildAnswers.ifPresent(child -> {
            child.getChildAnswers().stream().forEach(row -> {
                row.stream().forEach(childAnswer -> {
                    answerIdToOrderIndex.put(childAnswer.getId(), childAnswer.getOrderIndex());
                });
            });
        });

        for (Map.Entry<String, String> childAnswerConfig : compositeCopyConfiguration.getChildCopyConfiguration().entrySet()) {
            String sourceChildStableId = childAnswerConfig.getKey();
            String destinationChildStableId = childAnswerConfig.getValue();

            AnswerCopyResult childAnswerCopyResult = answerCopier.copyAnswer(sourceChildStableId, destinationChildStableId,
                    sourceInstanceId, destinationInstanceId, createdAtEpochMillis, lastUpdatedAtEpochMillis,
                    compositeCopyConfiguration);

            long[] numRowsInserted = jdbiCompositeAnswer.insertChildAnswerItems(parentCopyResult.getDestinationAnswerId(),
                    Collections.singletonList(childAnswerCopyResult.getDestinationAnswerId()),
                    Collections.singletonList(answerIdToOrderIndex.get(childAnswerCopyResult.getSourceAnswerId())));


            if (numRowsInserted.length != 1 && numRowsInserted[0] != 1) {
                throw new DaoException("Inserted " + numRowsInserted.length + " batches, first one of size " + numRowsInserted[0]
                        + " while copying answer from "
                        + childAnswerCopyResult.getSourceAnswerId() + " to " + childAnswerCopyResult.getDestinationAnswerId());
            }
        }
        return new AnswerCopyResult(parentCopyResult.getSourceAnswerId(), parentCopyResult.getDestinationAnswerId());

    }

}
