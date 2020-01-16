package org.broadinstitute.ddp.copy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.copy.CopyConfigurationPair;
import org.broadinstitute.ddp.model.copy.CopyLocation;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.jdbi.v3.core.Handle;

public class CopyExecutor {

    public void execute(Handle handle, long operatorId, long participantId, CopyConfiguration config) {
        long studyId = config.getStudyId();
        List<CopyConfigurationPair> pairs = config.getPairs();

        Set<String> allQuestionStableIds = new HashSet<>();
        for (var pair : pairs) {
            CopyLocation source = pair.getSource();
            if (source.getType() == CopyLocationType.ANSWER) {
                allQuestionStableIds.add(((CopyAnswerLocation) source).getQuestionStableId());
            }
            CopyLocation target = pair.getTarget();
            if (target.getType() == CopyLocationType.ANSWER) {
                allQuestionStableIds.add(((CopyAnswerLocation) target).getQuestionStableId());
            }
        }
        Map<String, QuestionDto> questionDtosByStableId = handle.attach(JdbiQuestion.class)
                .findLatestDtosByStudyIdAndQuestionStableIds(studyId, allQuestionStableIds)
                .collect(Collectors.toMap(QuestionDto::getStableId, Function.identity()));

        Set<Long> allActivityIds = new HashSet<>();
        for (var questionDto : questionDtosByStableId.values()) {
            allActivityIds.add(questionDto.getActivityId());
        }

        Map<Long, FormResponse> responsesById = handle.attach(ActivityInstanceDao.class)
                .findFormResponsesSubsetWithAnswersByUserId(participantId, allActivityIds)
                .collect(Collectors.toMap(ActivityResponse::getActivityId, Function.identity()));

        AnswerToAnswerCopier ansToAnsCopier = new AnswerToAnswerCopier(handle, operatorId);
        AnswerToProfileCopier ansToProfileCopier = new AnswerToProfileCopier(handle, operatorId);

        for (var pair : pairs) {
            CopyLocation source = pair.getSource();
            CopyLocation target = pair.getTarget();

            if (source.getType() == CopyLocationType.ANSWER && target.getType() == CopyLocationType.ANSWER) {
                String sourceStableId = ((CopyAnswerLocation) source).getQuestionStableId();
                QuestionDto sourceQuestion = questionDtosByStableId.get(sourceStableId);
                FormResponse sourceInstance = responsesById.get(sourceQuestion.getActivityId());

                String targetStableId = ((CopyAnswerLocation) target).getQuestionStableId();
                QuestionDto targetQuestion = questionDtosByStableId.get(targetStableId);
                FormResponse targetInstance = responsesById.get(targetQuestion.getActivityId());

                ansToAnsCopier.copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            } else if (source.getType() == CopyLocationType.ANSWER) {
                String sourceStableId = ((CopyAnswerLocation) source).getQuestionStableId();
                QuestionDto sourceQuestion = questionDtosByStableId.get(sourceStableId);
                FormResponse sourceInstance = responsesById.get(sourceQuestion.getActivityId());

                ansToProfileCopier.copy(sourceInstance, sourceQuestion, target.getType());
            } else {
                throw new DDPException(String.format(
                        "Copying from locations (%s to %s) in copy configuration %d is not supported",
                        source.getType(), target.getType(), config.getId()));
            }
        }
    }
}
