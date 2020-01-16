package org.broadinstitute.ddp.copy;

import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dao.JdbiCompositeQuestion;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnswerToProfileCopier {

    private static final Logger LOG = LoggerFactory.getLogger(AnswerToProfileCopier.class);

    private final Handle handle;
    private final long operatorId;
    private final JdbiCompositeQuestion jdbiCompositeQuestion;
    private final JdbiProfile jdbiProfile;

    public AnswerToProfileCopier(Handle handle, long operatorId) {
        this.handle = handle;
        this.operatorId = operatorId;
        this.jdbiCompositeQuestion = handle.attach(JdbiCompositeQuestion.class);
        this.jdbiProfile = handle.attach(JdbiProfile.class);
    }

    public void copy(FormResponse sourceInstance, QuestionDto sourceQuestion, CopyLocationType target) {
        Answer sourceAnswer = sourceInstance.getAnswer(sourceQuestion.getStableId());
        if (sourceAnswer == null) {
            sourceAnswer = jdbiCompositeQuestion
                    .findParentDtoByChildQuestionId(sourceQuestion.getId())
                    .map(parentDto -> (CompositeAnswer) sourceInstance.getAnswer(parentDto.getStableId()))
                    .map(parentAnswer -> {
                        List<Answer> answers = parentAnswer.getValue().stream()
                                .flatMap(row -> row.getValues().stream())
                                .filter(child -> sourceQuestion.getStableId().equals(child.getQuestionStableId()))
                                .collect(Collectors.toList());
                        if (answers.size() > 1) {
                            throw new DDPException("Composite child question " + sourceQuestion.getStableId() + " has multiple answers");
                        } else if (answers.size() == 1) {
                            return answers.get(0);
                        } else {
                            return null;
                        }
                    })
                    .orElse(null);
            if (sourceAnswer == null) {
                LOG.info("No source answer to copy from activity instance {} and question {}",
                        sourceInstance.getGuid(), sourceQuestion.getStableId());
            } else {
                copySourceAnswer(sourceInstance, sourceAnswer, target);
            }
        } else {
            copySourceAnswer(sourceInstance, sourceAnswer, target);
        }
    }

    private void copySourceAnswer(FormResponse sourceInstance, Answer sourceAnswer, CopyLocationType target) {
        switch (target) {
            case OPERATOR_PROFILE_FIRST_NAME:
                jdbiProfile.upsertFirstName(operatorId, extractValue(sourceAnswer, String.class));
                break;
            case OPERATOR_PROFILE_LAST_NAME:
                jdbiProfile.upsertLastName(operatorId, extractValue(sourceAnswer, String.class));
                break;
            case PARTICIPANT_PROFILE_BIRTH_DATE:
                long answerId = sourceAnswer.getAnswerId();
                jdbiProfile.upsertBirthDate(
                        sourceInstance.getParticipantId(),
                        extractValue(sourceAnswer, DateValue.class).asLocalDate().orElseThrow(() ->
                                new DDPException("Could not copy invalid date from answer with id " + answerId)));
                break;
            case PARTICIPANT_PROFILE_FIRST_NAME:
                jdbiProfile.upsertFirstName(sourceInstance.getParticipantId(), extractValue(sourceAnswer, String.class));
                break;
            case PARTICIPANT_PROFILE_LAST_NAME:
                jdbiProfile.upsertLastName(sourceInstance.getParticipantId(), extractValue(sourceAnswer, String.class));
                break;
            default:
                throw new DDPException("Unhandled copying of source answer to location type " + target);
        }
    }

    private <T> T extractValue(Answer answer, Class<T> type) {
        if (type == String.class) {
            switch (answer.getQuestionType()) {
                case BOOLEAN: // fall-through
                case TEXT:
                    return (T) answer.getValue().toString();
                default: // ignored
            }
        } else if (type == DateValue.class) {
            if (answer.getQuestionType() == QuestionType.DATE) {
                return (T) answer.getValue();
            }
        }
        throw new DDPException(String.format(
                "Currently unable to convert answer of type %s to value of type %s",
                answer.getQuestionType(), type.getSimpleName()));
    }
}
