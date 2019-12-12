package org.broadinstitute.ddp.db;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.broadinstitute.ddp.util.MiscUtil.fmt;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile.SqlQuery;
import org.broadinstitute.ddp.constants.ConfigFile.SqlStmt;
import org.broadinstitute.ddp.constants.ErrorMessages;
import org.broadinstitute.ddp.constants.SqlConstants.AnswerTable;
import org.broadinstitute.ddp.constants.SqlConstants.QuestionTable;
import org.broadinstitute.ddp.constants.SqlConstants.QuestionTypeTable;
import org.broadinstitute.ddp.db.dao.JdbiAgreementAnswer;
import org.broadinstitute.ddp.db.dao.JdbiCompositeAnswer;
import org.broadinstitute.ddp.db.dao.JdbiDateAnswer;
import org.broadinstitute.ddp.db.dao.JdbiNumericAnswer;
import org.broadinstitute.ddp.db.dao.PicklistAnswerDao;
import org.broadinstitute.ddp.db.dto.AgreementAnswerDto;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.CompositeAnswerSummaryDto;
import org.broadinstitute.ddp.exception.UnexpectedNumberOfElementsException;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnswerDao {

    private static final Logger LOG = LoggerFactory.getLogger(AnswerDao.class);

    private String answerIdByGuidsQuery;
    private String answersForQuestionQuery;
    private String boolAnswerByIdQuery;
    private String textAnswerByIdQuery;
    private String createAnswerStmt;
    private String createBoolAnswerStmt;
    private String createTextAnswerStmt;
    private String updateAnswerByIdStmt;
    private String updateBoolAnswerByIdStmt;
    private String updateTextAnswerByIdStmt;
    private String answerGuidsForQuestionQuery;
    private String deleteAnswerByIdStmt;
    private String deleteBoolAnswerByIdStmt;
    private String deleteTextAnswerByIdStmt;

    /**
     * Instantiate AnswerDao object with relevant SQL queries.
     */
    public AnswerDao(
            String answerIdByGuidsQuery,
            String answersForQuestionQuery,
            String boolAnswerByIdQuery,
            String textAnswerByIdQuery,
            String createAnswerStmt,
            String createBoolAnswerStmt,
            String createTextAnswerStmt,
            String updateAnswerByIdStmt,
            String updateBoolAnswerByIdStmt,
            String updateTextAnswerByIdStmt,
            String answerGuidsForQuestionQuery,
            String deleteAnswerByIdStmt,
            String deleteBoolAnswerByIdStmt,
            String deleteTextAnswerByIdStmt) {
        this.answerIdByGuidsQuery = answerIdByGuidsQuery;
        this.answersForQuestionQuery = answersForQuestionQuery;
        this.boolAnswerByIdQuery = boolAnswerByIdQuery;
        this.textAnswerByIdQuery = textAnswerByIdQuery;
        this.createAnswerStmt = createAnswerStmt;
        this.createBoolAnswerStmt = createBoolAnswerStmt;
        this.createTextAnswerStmt = createTextAnswerStmt;
        this.updateAnswerByIdStmt = updateAnswerByIdStmt;
        this.updateBoolAnswerByIdStmt = updateBoolAnswerByIdStmt;
        this.updateTextAnswerByIdStmt = updateTextAnswerByIdStmt;
        this.answerGuidsForQuestionQuery = answerGuidsForQuestionQuery;
        this.deleteAnswerByIdStmt = deleteAnswerByIdStmt;
        this.deleteBoolAnswerByIdStmt = deleteBoolAnswerByIdStmt;
        this.deleteTextAnswerByIdStmt = deleteTextAnswerByIdStmt;
    }

    /**
     * Build an answer dao using sql from given config.
     *
     * @param sqlConfig the config with sql queries and statements
     * @return answer dao object
     */
    public static AnswerDao fromSqlConfig(Config sqlConfig) {
        return new AnswerDao(
                sqlConfig.getString(SqlQuery.ANSWER_ID_BY_GUIDS),
                sqlConfig.getString(SqlQuery.ANSWERS_FOR_QUESTION),
                sqlConfig.getString(SqlQuery.BOOL_ANSWER_BY_ID),
                sqlConfig.getString(SqlQuery.TEXT_ANSWER_BY_ID),
                sqlConfig.getString(SqlStmt.CREATE_ANSWER),
                sqlConfig.getString(SqlStmt.CREATE_BOOL_ANSWER),
                sqlConfig.getString(SqlStmt.CREATE_TEXT_ANSWER),
                sqlConfig.getString(SqlStmt.UPDATE_ANSWER_BY_ID),
                sqlConfig.getString(SqlStmt.UPDATE_BOOL_ANSWER_BY_ID),
                sqlConfig.getString(SqlStmt.UPDATE_TEXT_ANSWER_BY_ID),
                sqlConfig.getString(SqlQuery.ANSWER_GUIDS_FOR_QUESTION),
                sqlConfig.getString(SqlStmt.DELETE_ANSWER_BY_ID),
                sqlConfig.getString(SqlStmt.DELETE_BOOL_ANSWER_BY_ID),
                sqlConfig.getString(SqlStmt.DELETE_TEXT_ANSWER_BY_ID)
        );
    }

    /**
     * Get the answer id. Requires the answer to be associated with given form instance.
     *
     * @param handle           the jdbi handle
     * @param formInstanceGuid the associated form instance guid
     * @param answerGuid       the answer guid
     * @return answer id, or null if guid not found
     */
    public Long getAnswerIdByGuids(Handle handle, String formInstanceGuid, String answerGuid) {
        Long answerId = null;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(answerIdByGuidsQuery)) {
            stmt.setString(1, formInstanceGuid);
            stmt.setString(2, answerGuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                answerId = rs.getLong(AnswerTable.ID);
                if (rs.next()) {
                    throw new RuntimeException("Found more than 1 answer id using guid "
                            + answerGuid + ", form instance " + formInstanceGuid);
                }
            } else {
                LOG.info("Answer id not found for guid {}, form instance {}", answerGuid, formInstanceGuid);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot find answer id for guid " + answerGuid
                    + ", form instance " + formInstanceGuid, e);
        }
        return answerId;
    }

    /**
     * Get guid of answers for question (any revision) in given form instance.
     *
     * @param handle           the jdbi handle
     * @param formInstanceGuid the associated form instance guid
     * @param stableId         the question stable id
     * @return list of guids, possibly empty if no answers exist
     */
    public List<String> getAnswerGuidsForQuestion(Handle handle, String formInstanceGuid, String stableId) {
        List<String> guids = new ArrayList<>();
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(answerGuidsForQuestionQuery)) {
            stmt.setString(1, formInstanceGuid);
            stmt.setString(2, stableId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                guids.add(rs.getString(AnswerTable.GUID));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot find answer guids for question " + stableId
                    + " in form instance " + formInstanceGuid, e);
        }
        return guids;
    }

    /**
     * Get all answers for a given form instance and question (regardless of revision).
     *
     * @param handle           the jdbi handle
     * @param formInstanceGuid the associated form instance guid
     * @param stableId         the question stable id
     * @return list of answers, possibly empty if no answers exist
     */
    public List<Answer> getAnswersForQuestion(Handle handle, String formInstanceGuid,
                                              String stableId, Long languageCodeId) {
        List<Long> answerIds = new ArrayList<>();
        List<QuestionType> questionTypes = new ArrayList<>();

        try (PreparedStatement stmt = handle.getConnection().prepareStatement(answersForQuestionQuery)) {
            stmt.setString(1, formInstanceGuid);
            stmt.setString(2, stableId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                answerIds.add(rs.getLong(AnswerTable.ID));
                questionTypes.add(QuestionType.valueOf(rs.getString(QuestionTypeTable.CODE)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not find answers for question " + stableId
                    + " in form instance " + formInstanceGuid, e);
        }

        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < answerIds.size(); i++) {
            answers.add(getAnswerByIdAndType(handle, answerIds.get(i), languageCodeId, questionTypes.get(i)));
        }

        LOG.info("Found {} answers for question {} in form instance {}", answers.size(), stableId, formInstanceGuid);
        return answers;
    }

    /**
     * Insert new answer.
     *
     * @param handle           the jdbi handle
     * @param answer           the answer object
     * @param operatorGuid     the operator who made the answer
     * @param formInstanceGuid the associated form instance guid
     * @return new answer guid or null if the value of {@code answer} is null
     */
    public String createAnswer(Handle handle, Answer answer, String operatorGuid, String formInstanceGuid) {
        if (answer == null) {
            return null;
        }
        Answer basicAnswer = createBasicAnswer(handle, answer, operatorGuid, formInstanceGuid);
        String guid = basicAnswer.getAnswerGuid();
        Long id = basicAnswer.getAnswerId();
        switch (answer.getQuestionType()) {
            case BOOLEAN:
                createBooleanAnswer(handle, guid, (BoolAnswer) answer);
                break;
            case PICKLIST:
                createPicklistAnswer(handle, id, (PicklistAnswer) answer, formInstanceGuid);
                break;
            case TEXT:
                createTextAnswer(handle, guid, (TextAnswer) answer);
                break;
            case DATE:
                JdbiDateAnswer dateAnswerDao = handle.attach(JdbiDateAnswer.class);
                dateAnswerDao.insertAnswer(id, ((DateAnswer) answer).getValue());
                break;
            case NUMERIC:
                createNumericAnswer(handle, id, (NumericAnswer) answer);
                break;
            case AGREEMENT:
                createAgreementAnswer(handle, id, (AgreementAnswer) answer);
                break;
            case COMPOSITE:
                createCompositeAnswer(handle, operatorGuid, (CompositeAnswer) answer, formInstanceGuid);
                break;
            default:
                throw new RuntimeException("Unhandled answer type " + answer.getQuestionType());
        }
        return guid;
    }

    /**
     * Update existing answer.
     *
     * @param handle       the jdbi handle
     * @param answerId     the answer primary id
     * @param answer       the answer object
     * @param operatorGuid the operator who made the change
     */
    public void updateAnswerById(Handle handle, String instanceGuid, long answerId, Answer answer, String operatorGuid) {
        updateBasicAnswerById(handle, answerId, operatorGuid);
        switch (answer.getQuestionType()) {
            case BOOLEAN:
                updateBooleanAnswerById(handle, answerId, (BoolAnswer) answer);
                break;
            case PICKLIST:
                updatePicklistAnswerById(handle, answerId, (PicklistAnswer) answer, instanceGuid);
                break;
            case TEXT:
                updateTextAnswerById(handle, answerId, (TextAnswer) answer);
                break;
            case DATE:
                JdbiDateAnswer dateAnswerDao = handle.attach(JdbiDateAnswer.class);
                dateAnswerDao.updateAnswerById(answerId, ((DateAnswer) answer).getValue());
                break;
            case NUMERIC:
                updateNumericAnswerById(handle, answerId, (NumericAnswer) answer);
                break;
            case COMPOSITE:
                updateCompositeAnswer(handle, instanceGuid, answerId, (CompositeAnswer) answer, operatorGuid);
                break;
            case AGREEMENT:
                updateAgreementAnswerById(handle, answerId, (AgreementAnswer) answer);
                break;
            default:
                throw new RuntimeException("Unhandled answer type " + answer.getQuestionType());
        }
    }

    private void updateCompositeAnswer(Handle handle, String instanceGuid, long answerId, CompositeAnswer parentAnswerFromUser,
                                       String operatorGuid) {
        JdbiCompositeAnswer compositeDao = handle.attach(JdbiCompositeAnswer.class);
        Optional<CompositeAnswerSummaryDto> optParentFromDb = compositeDao.findCompositeAnswerSummary(answerId);
        if (!optParentFromDb.isPresent()) {
            LOG.warn("Could not retrieve composite answer with id:" + answerId);
            return;
        }
        CompositeAnswerSummaryDto parentFromDb = optParentFromDb.get();
        List<List<Long>> childItemIds = new ArrayList<>();
        //Try to update existing answers. If they match by row number and stable id then update it
        //if they don't it is OK. Create new child answer
        for (int rowIdx = 0; rowIdx < parentAnswerFromUser.getValue().size(); rowIdx++) {
            AnswerRow rowOfUserAnswers = parentAnswerFromUser.getValue().get(rowIdx);
            final List<AnswerDto> rowOfDbAnswers;
            if (rowIdx < parentFromDb.getChildAnswers().size()) {
                rowOfDbAnswers = parentFromDb.getChildAnswers().get(rowIdx);
            } else {
                rowOfDbAnswers = null;
            }

            List<Long> rowOfAnswerIds = rowOfUserAnswers.getValues().stream().map(userAnswer -> {
                if (userAnswer == null) {
                    return null;
                }
                if (rowOfDbAnswers != null) {
                    Optional<AnswerDto> matchingAnswerFromDb = rowOfDbAnswers.stream()
                            .filter(answerFromDb -> answerFromDb.getQuestionStableId().equals(userAnswer.getQuestionStableId()))
                            .findFirst();
                    if (matchingAnswerFromDb.isPresent() && matchingAnswerFromDb.get().getId() != null) {
                        updateAnswerById(handle, instanceGuid, matchingAnswerFromDb.get().getId(), userAnswer, operatorGuid);
                        return matchingAnswerFromDb.get().getId();
                    }
                }
                createAnswer(handle, userAnswer, operatorGuid, instanceGuid);
                return userAnswer.getAnswerId();
            }).collect(toList());
            childItemIds.add(rowOfAnswerIds);
        }
        //remove existing references to child answers and recreate with the new guids
        compositeDao.deleteChildAnswerItems(answerId);
        //Let's delete the child answers that are will no longer be referenced by the parent composite answer
        List<AnswerDto> childAnswersFromDb =
                parentFromDb.getChildAnswers().stream().flatMap(Collection::stream).collect(toList());
        Set<Long> newChildItemIds = childItemIds.stream().flatMap(Collection::stream).collect(toSet());
        childAnswersFromDb.stream()
                .filter(childAnswer -> childAnswer != null && childAnswer.getId() != null)
                .filter(childAnswer -> !(newChildItemIds.contains(childAnswer.getId())))
                .forEach(childAnswer -> deleteAnswerByIdAndType(handle, childAnswer.getId(), childAnswer.getQuestionType()));

        compositeDao.insertChildAnswerItems(answerId, childItemIds);
    }

    /**
     * Delete an answer and its associated value.
     *
     * @param handle       the jdbi handle
     * @param id           the answer primary id
     * @param questionType the corresponding question type
     */
    public void deleteAnswerByIdAndType(Handle handle, long id, QuestionType questionType) {
        switch (questionType) {
            case BOOLEAN:
                deleteBooleanAnswerById(handle, id);
                break;
            case PICKLIST:
                deletePicklistAnswerById(handle, id);
                break;
            case TEXT:
                deleteTextAnswerById(handle, id);
                break;
            case DATE:
                JdbiDateAnswer dateAnswerDao = handle.attach(JdbiDateAnswer.class);
                dateAnswerDao.deleteAnswerById(id);
                break;
            case NUMERIC:
                deleteNumericAnswerById(handle, id);
                break;
            case AGREEMENT:
                deleteAgreementAnswerById(handle, id);
                break;
            case COMPOSITE:
                deleteCompositeAnswer(handle, id);
                break;
            default:
                throw new RuntimeException("Unhandled question type " + questionType);
        }
        deleteBasicAnswerById(handle, id);
    }

    /**
     * Get specific answer by its id.
     *
     * @param handle       the jdbi handle
     * @param id           the answer primary id
     * @param questionType the corresponding question type
     * @return answer of same type
     */
    private Answer getAnswerByIdAndType(Handle handle, long id, long languageCodeId, QuestionType questionType) {
        switch (questionType) {
            case BOOLEAN:
                return getBooleanAnswerById(handle, id);
            case PICKLIST:
                return handle.attach(PicklistAnswerDao.class)
                        .findByAnswerId(id)
                        .orElseThrow(() -> new DaoException("Could not find picklist answer with id " + id));
            case TEXT:
                return getTextAnswerById(handle, id);
            case DATE:
                JdbiDateAnswer dateAnswerDao = handle.attach(JdbiDateAnswer.class);
                return dateAnswerDao.getById(id).orElseThrow(
                        () -> new DaoException("Could not find date answer with id " + id));
            case NUMERIC:
                return handle.attach(JdbiNumericAnswer.class).findById(id)
                        .orElseThrow(() -> new DaoException("Could not find numeric answer with id " + id));
            case AGREEMENT:
                return getAgreementAnswerById(handle, id);
            case COMPOSITE:
                return getCompositeAnswerById(handle, languageCodeId, id);
            default:
                throw new RuntimeException("Unhandled question type " + questionType);
        }
    }

    /**
     * Get a boolean answer by its id.
     *
     * @param handle the jdbi handle
     * @param id     the answer primary id
     * @return the boolean answer
     */
    private BoolAnswer getBooleanAnswerById(Handle handle, long id) {
        BoolAnswer answer;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(boolAnswerByIdQuery)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Unable to find boolean answer with id " + id);
            }

            String guid = rs.getString(AnswerTable.GUID);
            boolean value = rs.getBoolean(AnswerTable.ANSWER);
            String questionStableId = rs.getString(QuestionTable.STABLE_ID);
            answer = new BoolAnswer(id, questionStableId, guid, value);

            if (rs.next()) {
                throw new RuntimeException("Too many rows found for boolean answer with id " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to get boolean answer with id " + id, e);
        }
        return answer;
    }

    private CompositeAnswer getCompositeAnswerById(Handle handle, long languageCodeId, long parentAnswerId) {
        JdbiCompositeAnswer compositeAnswerDao = handle.attach(JdbiCompositeAnswer.class);
        Optional<CompositeAnswerSummaryDto> optionalAnswerSummary =
                compositeAnswerDao.findCompositeAnswerSummary(parentAnswerId);
        if (optionalAnswerSummary.isPresent()) {
            CompositeAnswerSummaryDto summaryObj = optionalAnswerSummary.get();
            CompositeAnswer answer = new CompositeAnswer(summaryObj.getId(), summaryObj.getQuestionStableId(),
                    summaryObj.getGuid());
            summaryObj.getChildAnswers().forEach((List<AnswerDto> rowChildDtos) -> {
                List<Answer> rowOfAnswers = rowChildDtos.stream()
                        .map(childAnswerDto ->
                                //updated query gives us the question information if row exists but answer does not
                                //check for null then
                                childAnswerDto.getId() == null ? null : getAnswerByIdAndType(handle, childAnswerDto.getId(), languageCodeId,
                                        childAnswerDto.getQuestionType()))
                        .collect(toList());
                answer.addRowOfChildAnswers(rowOfAnswers);
            });
            return answer;

        } else {
            throw new DaoException("Unable to find CompositeAnswer with id" + parentAnswerId);
        }
    }

    /**
     * Get a text answer by its id.
     *
     * @param handle the jdbi handle
     * @param id     the answer primary id
     * @return the text answer
     */
    private TextAnswer getTextAnswerById(Handle handle, long id) {
        TextAnswer answer;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(textAnswerByIdQuery)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Unable to find text answer with id " + id);
            }

            String guid = rs.getString(AnswerTable.GUID);
            String answerText = rs.getString(AnswerTable.ANSWER);
            String questionStableId = rs.getString(QuestionTable.STABLE_ID);
            answer = new TextAnswer(id, questionStableId, guid, answerText);

            if (rs.next()) {
                throw new RuntimeException("Too many rows found for text answer with id " + id);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to get text answer with id " + id, e);
        }
        return answer;
    }

    /**
     * Get an agreement answer by its id.
     *
     * @param handle the jdbi handle
     * @param id     the answer primary id
     * @return the agreement answer
     */
    private AgreementAnswer getAgreementAnswerById(Handle handle, long id) {
        AgreementAnswerDto dto = handle.attach(JdbiAgreementAnswer.class).findDtoById(id).orElseThrow(
                () -> new DaoException("Could not find agreement answer with id " + id)
        );
        return new AgreementAnswer(id, dto.getQuestionStableId(), dto.getAnswerGuid(), dto.getAnswer());
    }

    /**
     * Takes an Answer object, creates an answer in the database and sets answer id.
     *
     * @param handle           the jdbi handle
     * @param answer           the answer object
     * @param operatorGuid     the operator who made the answer
     * @param formInstanceGuid the associated form instance guid
     * @return new answer guid
     */
    private Answer createBasicAnswer(Handle handle, Answer answer, String operatorGuid, String formInstanceGuid) {
        String guid = DBUtils.uniqueStandardGuid(handle, AnswerTable.TABLE_NAME, AnswerTable.GUID);
        long timestamp = Instant.now().getEpochSecond();
        try (PreparedStatement stmt = handle.getConnection()
                .prepareStatement(createAnswerStmt, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, answer.getQuestionStableId());
            stmt.setString(2, formInstanceGuid);
            stmt.setString(3, operatorGuid);
            stmt.setString(4, formInstanceGuid);
            stmt.setLong(5, timestamp);
            stmt.setLong(6, timestamp);
            stmt.setString(7, guid);
            int created = stmt.executeUpdate();

            if (created != 1) {
                throw new RuntimeException(fmt(ErrorMessages.UNEXPECTED_NUM_ROWS_CHANGED, "insert", 1, created));
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {

                if (generatedKeys.next()) {
                    answer.setAnswerId(generatedKeys.getLong(1));
                } else {
                    throw new RuntimeException(fmt("No generated id for answer with guid %s", answer.getAnswerGuid()));
                }

                if (generatedKeys.next()) {
                    throw new RuntimeException(fmt(ErrorMessages.UNEXPECTED_NUM_KEYS_GENERATED, 1, 2));
                }
            }

            answer.setAnswerGuid(guid);

        } catch (SQLException e) {
            throw new RuntimeException(fmt(ErrorMessages.COULD_NOT_OPERATE_ENTITY, "create", "answer") + " for "
                                               + "question " + answer.getQuestionStableId() + " in activity instance "
                                               + formInstanceGuid, e);
        }
        return answer;
    }

    /**
     * Insert the boolean answer.
     *
     * @param handle     the jdbi handle
     * @param answerGuid the guid of base answer
     * @param answer     the answer object
     */
    private void createBooleanAnswer(Handle handle, String answerGuid, BoolAnswer answer) {
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(createBoolAnswerStmt)) {
            stmt.setString(1, answerGuid);
            stmt.setBoolean(2, answer.getValue());
            int created = stmt.executeUpdate();
            if (created != 1) {
                throw new RuntimeException("Expected to insert 1 boolean answer but did " + created);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot create new boolean answer", e);
        }
    }

    /**
     * Insert the text answer.
     *
     * @param handle     the jdbi handle
     * @param answerGuid the guid of base answer
     * @param answer     the answer object
     */
    private void createTextAnswer(Handle handle, String answerGuid, TextAnswer answer) {
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(createTextAnswerStmt)) {
            stmt.setString(1, answerGuid);
            stmt.setString(2, answer.getValue());
            int created = stmt.executeUpdate();
            if (created != 1) {
                throw new RuntimeException("Expected to insert 1 text answer but did " + created);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot create new text answer", e);
        }
    }

    /**
     * Insert the numeric answer.
     *
     * @param handle   the jdbi handle
     * @param answerId the id of base answer
     * @param answer   the answer object
     */
    private void createNumericAnswer(Handle handle, long answerId, NumericAnswer answer) {
        if (answer.getNumericType() == NumericType.INTEGER) {
            int numInserted = handle.attach(JdbiNumericAnswer.class)
                    .insertNumericInteger(answerId, ((NumericIntegerAnswer) answer).getValue());
            if (numInserted != 1) {
                throw new DaoException("Expected to insert 1 numeric answer but did " + numInserted);
            }
        } else {
            throw new DaoException("Unhandled numeric type: " + answer.getNumericType());
        }
    }

    /**
     * Creates a picklist answer. By this moment the Answer entity is already created
     * so createPicklistAnswer() boils down to assigning picklist options to the answer.
     *
     * @param handle       the jdbi handle
     * @param answerId     id of the base answer (it's created beforehand)
     * @param answer       the picklist answer object
     * @param instanceGuid the activity instance guid
     */
    private void createPicklistAnswer(Handle handle, long answerId, PicklistAnswer answer, String instanceGuid) {
        LOG.info("Trying to assign picklist options to answer for question stable id {}", answer.getQuestionStableId());

        handle.attach(PicklistAnswerDao.class).assignOptionsToAnswerId(answerId, answer.getValue(), instanceGuid);

        LOG.info("Successfully assigned picklist options to answer with id {} for question stable id {}",
                answerId, answer.getQuestionStableId());
    }

    private void createCompositeAnswer(Handle handle, String operatorGuid, CompositeAnswer parentAnswer,
                                       String formInstanceGuid) {
        //todo look at parallelizing: can we do answer creation in parallel?
        JdbiCompositeAnswer compositeDao = handle.attach(JdbiCompositeAnswer.class);
        List<List<Long>> childAnswerIds = parentAnswer.getValue().stream()
                .map(rowOfAnswers -> rowOfAnswers.getValues().stream()
                        .map(childAnswer -> {
                            if (childAnswer == null) {
                                return null;
                            }
                            createAnswer(handle, childAnswer, operatorGuid, formInstanceGuid);
                            return childAnswer.getAnswerId();
                        })
                        .collect(toList()))
                .collect(toList());
        compositeDao.insertChildAnswerItems(parentAnswer.getAnswerId(), childAnswerIds);
    }

    /**
     * Insert the agreement answer.
     *
     * @param handle     the jdbi handle
     * @param answerId   the database id of base answer
     * @param answer     the answer object
     */
    private void createAgreementAnswer(Handle handle, long answerId, AgreementAnswer answer) {
        handle.attach(JdbiAgreementAnswer.class).insert(answerId, answer.getValue());
    }

    /**
     * Update the base answer.
     *
     * @param handle       the jdbi handle
     * @param id           the primary id of base answer
     * @param operatorGuid the operator who made the change
     */
    private void updateBasicAnswerById(Handle handle, long id, String operatorGuid) {
        long timestamp = Instant.now().getEpochSecond();
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(updateAnswerByIdStmt)) {
            stmt.setString(1, operatorGuid);
            stmt.setLong(2, timestamp);
            stmt.setLong(3, id);
            int updated = stmt.executeUpdate();
            if (updated != 1) {
                throw new RuntimeException("Expected to update 1 answer but did " + updated);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot update answer", e);
        }
    }

    /**
     * Update the boolean answer.
     *
     * @param handle the jdbi handle
     * @param id     the primary id of base answer
     * @param answer the answer object
     */
    private void updateBooleanAnswerById(Handle handle, long id, BoolAnswer answer) {
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(updateBoolAnswerByIdStmt)) {
            stmt.setBoolean(1, answer.getValue());
            stmt.setLong(2, id);
            int updated = stmt.executeUpdate();
            if (updated != 1) {
                throw new RuntimeException("Expected to update 1 boolean answer but did " + updated);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot update boolean answer", e);
        }
    }

    /**
     * Updates the picklist answer by deleting existing picklist option,
     * inserting new ones and saving the detail texts (if any).
     *
     * @param handle       the jdbi handle
     * @param answerId     the primary id of base answer
     * @param answer       the answer object
     * @param instanceGuid the activity instance guid
     */
    private void updatePicklistAnswerById(Handle handle, long answerId, PicklistAnswer answer, String instanceGuid) {
        PicklistAnswerDao dao = handle.attach(PicklistAnswerDao.class);

        int numDeleted = dao.unassignOptionsFromAnswerId(answerId);
        LOG.info("Unassigned {} picklist options from answer id {}", numDeleted, answerId);

        dao.assignOptionsToAnswerId(answerId, answer.getValue(), instanceGuid);
    }

    /**
     * Update the text answer.
     *
     * @param handle the jdbi handle
     * @param id     the primary id of base answer
     * @param answer the answer object
     */
    private void updateTextAnswerById(Handle handle, long id, TextAnswer answer) {
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(updateTextAnswerByIdStmt)) {
            stmt.setString(1, answer.getValue());
            stmt.setLong(2, id);
            int updated = stmt.executeUpdate();
            if (updated != 1) {
                throw new RuntimeException("Expected to update 1 text answer but did " + updated);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot update text answer", e);
        }
    }

    /**
     * Update the numeric answer.
     *
     * @param handle   the jdbi handle
     * @param answerId the primary id of base answer
     * @param answer   the answer object
     */
    private void updateNumericAnswerById(Handle handle, long answerId, NumericAnswer answer) {
        if (answer.getNumericType() == NumericType.INTEGER) {
            int numUpdated = handle.attach(JdbiNumericAnswer.class)
                    .updateNumericInteger(answerId, ((NumericIntegerAnswer) answer).getValue());
            if (numUpdated != 1) {
                throw new DaoException("Expected to update 1 numeric answer but did " + numUpdated);
            }
        } else {
            throw new DaoException("Unhandled numeric type: " + answer.getNumericType());
        }
    }

    /**
     * Update the agreement answer.
     *
     * @param handle the jdbi handle
     * @param id     the primary id of base answer
     * @param answer the answer object
     */
    private void updateAgreementAnswerById(Handle handle, long id, AgreementAnswer answer) {
        int updated = handle.attach(JdbiAgreementAnswer.class).updateById(id, answer.getValue());
        if (updated != 1) {
            throw new DaoException("Expected to update 1 agreement answer but did " + updated);
        }
    }

    /**
     * Delete the base answer.
     *
     * @param handle the jdbi handle
     * @param id     the answer primary id
     */
    private void deleteBasicAnswerById(Handle handle, long id) {
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(deleteAnswerByIdStmt)) {
            stmt.setLong(1, id);
            int deleted = stmt.executeUpdate();
            if (deleted != 1) {
                throw new UnexpectedNumberOfElementsException("Expected to delete 1 answer but did " + deleted);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot delete answer", e);
        }
    }

    /**
     * Delete the boolean answer.
     *
     * @param handle the jdbi handle
     * @param id     the answer primary id
     */
    private void deleteBooleanAnswerById(Handle handle, long id) {
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(deleteBoolAnswerByIdStmt)) {
            stmt.setLong(1, id);
            int deleted = stmt.executeUpdate();
            if (deleted != 1) {
                throw new RuntimeException("Expected to delete 1 boolean answer but did " + deleted);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot delete boolean answer", e);
        }
    }

    /**
     * Unassigns the picklist answer's options.
     *
     * @param handle the jdbi handle
     * @param id     id of the answer to delete
     */
    private void deletePicklistAnswerById(Handle handle, long id) {
        LOG.info("Attempting to delete picklist answer for answer id {}", id);
        int numDeleted = handle.attach(PicklistAnswerDao.class).unassignOptionsFromAnswerId(id);
        LOG.info("Unassigned {} picklist options from answer id {}", numDeleted, id);
    }

    /**
     * Delete the text answer value.
     *
     * @param handle the jdbi handle
     * @param id     the answer primary key
     */
    private void deleteTextAnswerById(Handle handle, long id) {
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(deleteTextAnswerByIdStmt)) {
            stmt.setLong(1, id);
            int deleted = stmt.executeUpdate();
            if (deleted != 1) {
                throw new RuntimeException("Expected to delete 1 text answer but did " + deleted);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot delete text answer", e);
        }
    }

    /**
     * Delete the numeric answer value.
     *
     * @param handle the jdbi handle
     * @param id     the answer primary key
     */
    private void deleteNumericAnswerById(Handle handle, long id) {
        int numDeleted = handle.attach(JdbiNumericAnswer.class).deleteById(id);
        if (numDeleted != 1) {
            throw new DaoException("Expected to delete 1 numeric answer but did " + numDeleted);
        }
    }

    /**
     * Delete the agreement answer.
     *
     * @param handle the jdbi handle
     * @param id     the answer primary id
     */
    private void deleteAgreementAnswerById(Handle handle, long id) {
        int deleted = handle.attach(JdbiAgreementAnswer.class).deleteById(id);
        if (deleted != 1) {
            throw new RuntimeException("Expected to delete 1 agreement answer but did " + deleted);
        }
    }

    private void deleteCompositeAnswer(Handle handle, long compositeAnswerId) {
        JdbiCompositeAnswer compositeAnswerDao = handle.attach(JdbiCompositeAnswer.class);
        Optional<CompositeAnswerSummaryDto> summaryObjOpt = compositeAnswerDao
                .findCompositeAnswerSummary(compositeAnswerId);
        summaryObjOpt.ifPresent(summaryObj -> {
            compositeAnswerDao.deleteChildAnswerItems(compositeAnswerId);
            summaryObj.getChildAnswers().forEach(rowOfAnswers ->
                    rowOfAnswers.forEach(childAnswer -> {
                        if (childAnswer.getId() != null) {
                            deleteAnswerByIdAndType(handle, childAnswer.getId(),
                                    childAnswer.getQuestionType());
                        }
                    })
            );

        });
    }
}
