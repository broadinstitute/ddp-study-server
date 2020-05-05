package org.broadinstitute.ddp.copy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.dao.JdbiCompositeQuestion;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
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
    private final UserProfileDao profileDao;
    private final Map<Long, CompositeQuestionDto> parentDtosByChildId = new HashMap<>();

    public AnswerToProfileCopier(Handle handle, long operatorId) {
        this.handle = handle;
        this.operatorId = operatorId;
        this.jdbiCompositeQuestion = handle.attach(JdbiCompositeQuestion.class);
        this.profileDao = handle.attach(UserProfileDao.class);
    }

    public void copy(FormResponse sourceInstance, QuestionDto sourceQuestion, CopyLocationType target) {
        Answer sourceAnswer = sourceInstance.getAnswer(sourceQuestion.getStableId());
        if (sourceAnswer != null) {
            copySourceAnswer(sourceInstance, sourceAnswer, target);
        } else {
            sourceAnswer = extractSourceChildAnswer(sourceInstance, sourceQuestion);
            if (sourceAnswer != null) {
                copySourceAnswer(sourceInstance, sourceAnswer, target);
            } else {
                LOG.info("No source answer to copy from activity instance {} and question {} to target {}",
                        sourceInstance.getGuid(), sourceQuestion.getStableId(), target);
            }
        }
    }

    private void copySourceAnswer(FormResponse sourceInstance, Answer sourceAnswer, CopyLocationType target) {
        switch (target) {
            case OPERATOR_PROFILE_FIRST_NAME:
                profileDao.getUserProfileSql().upsertFirstName(operatorId, extractValue(sourceAnswer, String.class));
                break;
            case OPERATOR_PROFILE_LAST_NAME:
                profileDao.getUserProfileSql().upsertLastName(operatorId, extractValue(sourceAnswer, String.class));
                break;
            case PARTICIPANT_PROFILE_BIRTH_DATE:
                profileDao.getUserProfileSql().upsertBirthDate(
                        sourceInstance.getParticipantId(),
                        extractValue(sourceAnswer, DateValue.class)
                                .asLocalDate()
                                .orElseThrow(() -> new DDPException(
                                        "Could not copy invalid date from answer with id " + sourceAnswer.getAnswerId())));
                break;
            case PARTICIPANT_PROFILE_FIRST_NAME:
                profileDao.getUserProfileSql().upsertFirstName(sourceInstance.getParticipantId(), extractValue(sourceAnswer, String.class));
                break;
            case PARTICIPANT_PROFILE_LAST_NAME:
                profileDao.getUserProfileSql().upsertLastName(sourceInstance.getParticipantId(), extractValue(sourceAnswer, String.class));
                break;
            default:
                throw new DDPException("Unhandled copying of source answer to location type " + target);
        }
    }

    public Answer extractSourceChildAnswer(FormResponse sourceInstance, QuestionDto sourceQuestion) {
        CompositeQuestionDto parentDto = parentDtosByChildId.get(sourceQuestion.getId());
        if (parentDto == null) {
            parentDto = jdbiCompositeQuestion
                    .findParentDtoByChildQuestionId(sourceQuestion.getId())
                    .orElse(null);
            if (parentDto == null) {
                LOG.info("No parent composite question found for source question {}", sourceQuestion.getStableId());
                return null;
            }
            for (var childDto : parentDto.getChildQuestions()) {
                parentDtosByChildId.put(childDto.getId(), parentDto);
            }
        }

        var parentAnswer = (CompositeAnswer) sourceInstance.getAnswer(parentDto.getStableId());
        if (parentAnswer == null) {
            LOG.info("No parent composite answer found for source question {}", sourceQuestion.getStableId());
            return null;
        }

        List<Answer> answers = parentAnswer.getValue().stream()
                .flatMap(row -> row.getValues().stream())
                .filter(child -> sourceQuestion.getStableId().equals(child.getQuestionStableId()))
                .collect(Collectors.toList());
        if (answers.size() > 1) {
            throw new DDPException("Composite child question " + sourceQuestion.getStableId() + " has multiple answers");
        } else if (answers.size() == 1) {
            return answers.get(0);
        } else {
            LOG.info("No child composite answers found for source question {}", sourceQuestion.getStableId());
            return null;
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
