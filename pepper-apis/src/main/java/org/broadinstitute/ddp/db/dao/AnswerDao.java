package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.CompositeAnswerSummaryDto;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface AnswerDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(AnswerDao.class);
    String TABLE_NAME = "answer";
    String GUID_COLUMN = "answer_guid";

    @CreateSqlObject
    PicklistAnswerDao getPicklistAnswerDao();

    @CreateSqlObject
    JdbiCompositeAnswer getJdbiCompositeAnswer();

    @CreateSqlObject
    AnswerSql getAnswerSql();

    //
    // inserts
    //

    // Convenience method for creating answers using guids. Prefer the other method that takes ids.
    default Answer createAnswer(String operatorGuid, String instanceGuid, Answer answer) {
        long operatorId = getHandle().attach(JdbiUser.class).getUserIdByGuid(operatorGuid);
        long instanceId = getHandle().attach(JdbiActivityInstance.class).getActivityInstanceId(instanceGuid);
        return createAnswer(operatorId, instanceId, answer, null);
    }

    default Answer createAnswer(long operatorId, long instanceId, Answer answer) {
        return createAnswer(operatorId, instanceId, answer, null);
    }

    default Answer createAnswer(long operatorId, long instanceId, Answer answer, QuestionDef questionDef) {
        long now = Instant.now().toEpochMilli();
        Pair<String, Long> guidAndId = DBUtils.executeSqlInsertWithGeneratedGuid((guid) ->
                getAnswerSql().insertAnswerByQuestionStableId(guid, operatorId, instanceId, answer.getQuestionStableId(), now, now)
        );
        createAnswerValue(operatorId, instanceId, guidAndId.getRight(), answer, questionDef);
        answer.setAnswerId(guidAndId.getRight());
        answer.setAnswerGuid(guidAndId.getLeft());
        return answer;
    }

    private void createAnswerValue(long operatorId, long instanceId, long answerId, Answer answer, QuestionDef questionDef) {
        var answerSql = getAnswerSql();
        var type = answer.getQuestionType();
        if (type == QuestionType.AGREEMENT) {
            boolean value = ((AgreementAnswer) answer).getValue();
            DBUtils.checkInsert(1, answerSql.insertAgreementValue(answerId, value));
        } else if (type == QuestionType.BOOLEAN) {
            boolean value = ((BoolAnswer) answer).getValue();
            DBUtils.checkInsert(1, answerSql.insertBoolValue(answerId, value));
        } else if (type == QuestionType.COMPOSITE) {
            createAnswerCompositeValue(operatorId, instanceId, answerId, (CompositeAnswer) answer);
        } else if (type == QuestionType.DATE) {
            DateValue value = ((DateAnswer) answer).getValue();
            DBUtils.checkInsert(1, answerSql.insertDateValue(answerId, value));
        } else if (type == QuestionType.NUMERIC) {
            NumericAnswer ans = (NumericAnswer) answer;
            if (ans.getNumericType() != NumericType.INTEGER) {
                throw new DaoException("Unhandled numeric type: " + ans.getNumericType());
            }
            Long value = ((NumericIntegerAnswer) ans).getValue();
            DBUtils.checkInsert(1, answerSql.insertNumericIntValue(answerId, value));
        } else if (type == QuestionType.PICKLIST) {
            if (questionDef == null) {
                createAnswerPicklistValue(instanceId, answerId, (PicklistAnswer) answer);
            } else {
                createAnswerPicklistValue(answerId, (PicklistAnswer) answer, (PicklistQuestionDef) questionDef);
            }
        } else if (type == QuestionType.TEXT) {
            String value = ((TextAnswer) answer).getValue();
            DBUtils.checkInsert(1, answerSql.insertTextValue(answerId, value));
        } else {
            throw new DaoException("Unhandled answer type " + type);
        }
    }

    default Answer createAnswer(long operatorId, long instanceId, long questionId, Answer answer) {
        String guid = DBUtils.uniqueStandardGuid(getHandle(), TABLE_NAME, GUID_COLUMN);
        long now = Instant.now().toEpochMilli();
        long id = getAnswerSql().insertAnswer(guid, operatorId, instanceId, questionId, now, now);
        createAnswerValue(operatorId, instanceId, id, answer, null);
        return findAnswerById(id).orElseThrow(() -> new DaoException("Could not find answer with id " + id));
    }

    private void createAnswerCompositeValue(long operatorId, long instanceId, long answerId, CompositeAnswer answer) {
        List<List<Long>> childAnswerIds = answer.getValue().stream()
                .map(row -> row.getValues().stream()
                        .map(child -> child == null ? null : createAnswer(operatorId, instanceId, child, null).getAnswerId())
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
        getJdbiCompositeAnswer().insertChildAnswerItems(answerId, childAnswerIds);
    }

    private void createAnswerPicklistValue(long instanceId, long answerId, PicklistAnswer answer) {
        String instanceGuid = getHandle().attach(JdbiActivityInstance.class).getActivityInstanceGuid(instanceId);
        getPicklistAnswerDao().assignOptionsToAnswerId(answerId, answer.getValue(), instanceGuid);
    }

    private void createAnswerPicklistValue(long answerId, PicklistAnswer answer, PicklistQuestionDef questionDef) {
        getPicklistAnswerDao().assignOptionsToAnswerId(answerId, answer.getValue(), questionDef);
    }
    //
    // updates
    //

    default void updateAnswer(long operatorId, long answerId, Answer newAnswer) {
        updateAnswer(operatorId, answerId, newAnswer, null);
    }

    default void updateAnswer(long operatorId, long answerId, Answer newAnswer, QuestionDef questionDef) {
        long now = Instant.now().toEpochMilli();
        DBUtils.checkUpdate(1, getAnswerSql().updateAnswerById(answerId, operatorId, now));
        updateAnswerValue(operatorId, answerId, newAnswer, questionDef);
        newAnswer.setAnswerId(answerId);
    }

    private void updateAnswerValue(long operatorId, long answerId, Answer newAnswer, QuestionDef questionDef) {
        var answerSql = getAnswerSql();
        var type = newAnswer.getQuestionType();
        if (type == QuestionType.AGREEMENT) {
            boolean value = ((AgreementAnswer) newAnswer).getValue();
            DBUtils.checkInsert(1, answerSql.updateAgreementValueById(answerId, value));
        } else if (type == QuestionType.BOOLEAN) {
            boolean value = ((BoolAnswer) newAnswer).getValue();
            DBUtils.checkInsert(1, answerSql.updateBoolValueById(answerId, value));
        } else if (type == QuestionType.COMPOSITE) {
            updateAnswerCompositeValue(operatorId, answerId, (CompositeAnswer) newAnswer);
        } else if (type == QuestionType.DATE) {
            DateValue value = ((DateAnswer) newAnswer).getValue();
            DBUtils.checkInsert(1, answerSql.updateDateValueById(answerId, value));
        } else if (type == QuestionType.NUMERIC) {
            NumericAnswer ans = (NumericAnswer) newAnswer;
            if (ans.getNumericType() != NumericType.INTEGER) {
                throw new DaoException("Unhandled numeric type: " + ans.getNumericType());
            }
            Long value = ((NumericIntegerAnswer) ans).getValue();
            DBUtils.checkInsert(1, answerSql.updateNumericIntValueById(answerId, value));
        } else if (type == QuestionType.PICKLIST) {
            if (questionDef == null) {
                updateAnswerPicklistValue(answerId, (PicklistAnswer) newAnswer);
            } else {
                updateAnswerPicklistValue(answerId, (PicklistAnswer) newAnswer, (PicklistQuestionDef) questionDef);
            }
        } else if (type == QuestionType.TEXT) {
            String value = ((TextAnswer) newAnswer).getValue();
            DBUtils.checkInsert(1, answerSql.updateTextValueById(answerId, value));
        } else {
            throw new DaoException("Unhandled answer type " + type);
        }
    }

    private void updateAnswerCompositeValue(long operatorId, long answerId, CompositeAnswer newAnswer) {
        var jdbiCompositeAnswer = getJdbiCompositeAnswer();
        CompositeAnswerSummaryDto oldAnswerDto = jdbiCompositeAnswer.findCompositeAnswerSummary(answerId)
                .orElseThrow(() -> new DaoException("Could not find composite answer with id " + answerId));

        List<List<AnswerDto>> oldChildRows = oldAnswerDto.getChildAnswers();
        List<AnswerRow> newChildRows = newAnswer.getValue();
        List<List<Long>> newChildIdRows = new ArrayList<>();
        Set<Long> allNewChildIds = new HashSet<>();

        // Line up the rows
        while (oldChildRows.size() < newChildRows.size()) {
            oldChildRows.add(new ArrayList<>());
        }

        // Update existing child answers if row index and question stable id matches, otherwise create new child answer
        for (int rowIdx = 0; rowIdx < newChildRows.size(); rowIdx++) {
            AnswerRow newRow = newChildRows.get(rowIdx);
            List<AnswerDto> oldRow = oldChildRows.get(rowIdx);

            List<Long> newChildIds = newRow.getValues().stream().map(newChild -> {
                if (newChild == null) {
                    return null;
                }
                Long matchingOldChildId = oldRow.stream()
                        .filter(oldChild -> oldChild != null && oldChild.getQuestionStableId().equals(newChild.getQuestionStableId()))
                        .findFirst()
                        .map(AnswerDto::getId)
                        .orElse(null);
                if (matchingOldChildId != null) {
                    updateAnswer(operatorId, matchingOldChildId, newChild);
                    return matchingOldChildId;
                } else {
                    return createAnswer(operatorId, oldAnswerDto.getActivityInstanceId(), newChild, null).getAnswerId();
                }
            }).collect(Collectors.toList());

            newChildIdRows.add(newChildIds);
            allNewChildIds.addAll(newChildIds);
        }

        // Remove old child answers that are no longer referenced
        jdbiCompositeAnswer.deleteChildAnswerItems(answerId);
        oldChildRows.stream()
                .flatMap(Collection::stream)
                .filter(child -> child != null && child.getId() != null && !allNewChildIds.contains(child.getId()))
                .forEach(orphaned -> deleteAnswer(orphaned.getId()));

        // Recreate child answer references
        jdbiCompositeAnswer.insertChildAnswerItems(answerId, newChildIdRows);
    }

    private void updateAnswerPicklistValue(long answerId, PicklistAnswer newAnswer) {
        var answerSql = getAnswerSql();
        var picklistAnswerDao = getPicklistAnswerDao();
        String instanceGuid = answerSql.findInstanceGuidByAnswerId(answerId)
                .orElseThrow(() -> new DaoException("Could not find activity instance guid for answer id " + answerId));
        answerSql.deletePicklistSelectedByAnswerId(answerId);
        picklistAnswerDao.assignOptionsToAnswerId(answerId, newAnswer.getValue(), instanceGuid);
    }

    private void updateAnswerPicklistValue(long answerId, PicklistAnswer newAnswer, PicklistQuestionDef questionDef) {
        getAnswerSql().deletePicklistSelectedByAnswerId(answerId);
        getPicklistAnswerDao().assignOptionsToAnswerId(answerId, newAnswer.getValue(), questionDef);
    }

    //
    // deletes
    //

    default void deleteAnswer(long answerId) {
        deleteAnswers(Set.of(answerId));
    }

    default void deleteAnswers(Set<Long> answerIds) {
        var answerSql = getAnswerSql();
        var jdbiCompositeAnswer = getJdbiCompositeAnswer();

        Set<Long> childAnswerIds = new HashSet<>();
        Map<Long, QuestionType> typesByAnswerId = answerSql.findQuestionTypesByAnswerIds(answerIds);

        for (var answerId : answerIds) {
            QuestionType type = typesByAnswerId.get(answerId);
            if (type == null) {
                throw new DaoException("Could not find question type for answer with id " + answerId);
            }
            if (type == QuestionType.COMPOSITE) {
                CompositeAnswerSummaryDto parentDto = jdbiCompositeAnswer.findCompositeAnswerSummary(answerId)
                        .orElseThrow(() -> new DaoException("Could not find parent composite answer with id " + answerId));
                parentDto.getChildAnswers().stream()
                        .flatMap(Collection::stream)
                        .filter(child -> child != null && child.getId() != null)
                        .forEach(child -> childAnswerIds.add(child.getId()));
            }
        }

        // Delete parent answers first so it de-references the child answers
        DBUtils.checkDelete(answerIds.size(), answerSql.bulkDeleteAnswersById(answerIds));

        // Assuming child answers are not composites, otherwise can recursively delete
        DBUtils.checkDelete(childAnswerIds.size(), answerSql.bulkDeleteAnswersById(childAnswerIds));
    }

    default void deleteAllByInstanceIds(Set<Long> instanceIds) {
        deleteAnswers(getAnswerSql().findAllAnswerIdsByInstanceIds(instanceIds));
    }

    //
    // queries
    //

    @UseStringTemplateSqlLocator
    @SqlQuery("findAnswerById")
    @UseRowReducer(AnswerWithValueReducer.class)
    Optional<Answer> findAnswerById(@Bind("id") long answerId);

    @UseStringTemplateSqlLocator
    @SqlQuery("findAnswerByGuid")
    @UseRowReducer(AnswerWithValueReducer.class)
    Optional<Answer> findAnswerByGuid(@Bind("guid") String answerGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("findAnswerByInstanceIdAndQuestionStableId")
    @UseRowReducer(AnswerWithValueReducer.class)
    Optional<Answer> findAnswerByInstanceIdAndQuestionStableId(
            @Bind("instanceId") long instanceId,
            @Bind("questionStableId") String questionStableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("findAnswerByInstanceGuidAndQuestionStableId")
    @UseRowReducer(AnswerWithValueReducer.class)
    Optional<Answer> findAnswerByInstanceGuidAndQuestionStableId(
            @Bind("instanceGuid") String instanceGuid,
            @Bind("questionStableId") String questionStableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("findAnswerByLatestInstanceAndQuestionStableId")
    @UseRowReducer(AnswerWithValueReducer.class)
    Optional<Answer> findAnswerByLatestInstanceAndQuestionStableId(
            @Bind("userId") long userId,
            @Bind("studyId") long studyId,
            @Bind("questionStableId") String questionStableId);

    //
    // reducers
    //

    class AnswerWithValueReducer implements LinkedHashMapRowReducer<Long, Answer> {
        private Map<Long, Answer> childAnswers = new HashMap<>();

        @Override
        public void accumulate(Map<Long, Answer> container, RowView view) {
            reduce(container, view);
        }

        @Override
        public Stream<Answer> stream(Map<Long, Answer> container) {
            if (!childAnswers.isEmpty()) {
                // There are child answers that has not been consumed by a parent answer,
                // very likely we're querying for the child answers themselves so return them.
                container.putAll(childAnswers);
            }
            return container.values().stream();
        }

        /**
         * Same as accumulate, but also returns the answer that was reduced from the row.
         *
         * @param container mapping of answer id to answer object
         * @param view      the row view
         * @return reduced answer, or null if row is for a child answer
         */
        public Answer reduce(Map<Long, Answer> container, RowView view) {
            long answerId = view.getColumn("answer_id", Long.class);
            var answerGuid = view.getColumn("answer_guid", String.class);
            var questionStableId = view.getColumn("question_stable_id", String.class);
            var type = QuestionType.valueOf(view.getColumn("question_type", String.class));
            boolean isChildAnswer = view.getColumn("is_child_answer", Boolean.class);
            String actInstanceGuid = view.getColumn("activity_instance_guid", String.class);

            Answer answer;
            switch (type) {
                case AGREEMENT:
                    answer = new AgreementAnswer(answerId, questionStableId, answerGuid, view.getColumn("aa_value", Boolean.class),
                            actInstanceGuid);
                    break;
                case BOOLEAN:
                    answer = new BoolAnswer(answerId, questionStableId, answerGuid, view.getColumn("ba_value", Boolean.class),
                            actInstanceGuid);
                    break;
                case TEXT:
                    answer = new TextAnswer(answerId, questionStableId, answerGuid, view.getColumn("ta_value", String.class),
                            actInstanceGuid);
                    break;
                case DATE:
                    answer = new DateAnswer(answerId, questionStableId, answerGuid,
                            view.getColumn("da_year", Integer.class),
                            view.getColumn("da_month", Integer.class),
                            view.getColumn("da_day", Integer.class),
                            actInstanceGuid);
                    break;
                case NUMERIC:
                    var numericType = NumericType.valueOf(view.getColumn("na_numeric_type", String.class));
                    if (numericType == NumericType.INTEGER) {
                        answer = new NumericIntegerAnswer(answerId, questionStableId, answerGuid,
                                view.getColumn("na_int_value", Long.class), actInstanceGuid);
                    } else {
                        throw new DaoException("Unhandled numeric answer type " + numericType);
                    }
                    break;
                case PICKLIST:
                    var map = isChildAnswer ? childAnswers : container;
                    answer = map.computeIfAbsent(answerId, id ->
                            new PicklistAnswer(answerId, questionStableId, answerGuid, new ArrayList<>(), actInstanceGuid));
                    var optionStableId = view.getColumn("pa_option_stable_id", String.class);
                    if (optionStableId != null) {
                        var option = new SelectedPicklistOption(optionStableId, view.getColumn("pa_detail_text", String.class));
                        ((PicklistAnswer) answer).getValue().add(option);
                    }
                    break;
                case COMPOSITE:
                    answer = container.computeIfAbsent(answerId, id -> {
                        var ans = new CompositeAnswer(answerId, questionStableId, answerGuid, actInstanceGuid);
                        ans.setAllowMultiple(view.getColumn("ca_allow_multiple", Boolean.class));
                        ans.setUnwrapOnExport(view.getColumn("ca_unwrap_on_export", Boolean.class));
                        return ans;
                    });

                    Long childAnswerId = view.getColumn("ca_child_answer_id", Long.class);
                    if (childAnswerId != null) {
                        Answer childAnswer = childAnswers.get(childAnswerId);
                        if (childAnswer == null) {
                            throw new DaoException(String.format(
                                    "Could not find child answer with id=%d for composite answer %d", childAnswerId, answerId));
                        }

                        int childRow = view.getColumn("ca_child_row", Integer.class); // zero-indexed row number
                        int childCol = view.getColumn("ca_child_col", Integer.class); // zero-indexed column number
                        List<AnswerRow> rows = ((CompositeAnswer) answer).getValue();
                        while (rows.size() < childRow + 1) {
                            rows.add(new AnswerRow());
                        }
                        List<Answer> row = rows.get(childRow).getValues();
                        while (row.size() < childCol + 1) {
                            row.add(null);
                        }
                        row.set(childCol, childAnswer);

                        // Pull child answer out of map, indicating we have consumed it
                        childAnswers.remove(childAnswerId);
                    }
                    break;
                default:
                    throw new DaoException("Unhandled answer type " + type);
            }

            if (isChildAnswer) {
                childAnswers.put(answerId, answer);
                return null;
            } else {
                container.put(answerId, answer);
                return answer;
            }
        }
    }
}
