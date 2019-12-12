package org.broadinstitute.ddp.model.activity.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents a form activity instance with only user answers, without indication of form structure or question/answer ordering.
 *
 * <p>Note: this assumes there is only one answer per question.
 */
public final class FormResponse extends ActivityResponse {

    private Map<String, Answer> answers = new HashMap<>();

    @JdbiConstructor
    public FormResponse(@ColumnName("instance_id") long id,
                        @ColumnName("instance_guid") String guid,
                        @ColumnName("participant_id") long participantId,
                        @ColumnName("is_readonly") boolean isReadonly,
                        @ColumnName("created_at") long createdAt,
                        @ColumnName("first_completed_at") Long firstCompletedAt,
                        @ColumnName("activity_id") long activityId,
                        @ColumnName("activity_code") String activityCode,
                        @ColumnName("activity_version_tag") String activityVersionTag,
                        @Nested ActivityInstanceStatusDto latestStatus) {
        super(ActivityType.FORMS, id, guid, participantId, isReadonly, createdAt, firstCompletedAt,
                activityId, activityCode, activityVersionTag, latestStatus);
    }

    public List<Answer> getAnswers() {
        return new ArrayList<>(answers.values());
    }

    public Answer getAnswer(String questionStableId) {
        return answers.get(questionStableId);
    }

    public Answer getAnswerOrCompute(String questionStableId, Supplier<Answer> supplier) {
        Answer answer = answers.get(questionStableId);
        if (answer == null) {
            answer = supplier.get();
            answers.put(questionStableId, answer);
        }
        return answer;
    }

    public boolean hasAnswer(String questionStableId) {
        return answers.containsKey(questionStableId);
    }

    public void putAnswer(Answer answer) {
        answers.put(answer.getQuestionStableId(), answer);
    }

    public void unwrapComposites() {
        for (Answer answer : new ArrayList<>(answers.values())) {
            if (answer.getQuestionType() == QuestionType.COMPOSITE) {
                CompositeAnswer ans = (CompositeAnswer) answer;
                if (ans.shouldUnwrapChildAnswers()) {
                    ans.getValue().stream().flatMap(row -> row.getValues().stream()).forEach(this::putAnswer);
                }
            }
        }
    }
}
