package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
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

public interface AnswerHistoryDao extends SqlObject {

    @CreateSqlObject
    PicklistAnswerDao getPicklistAnswerDao();

    @CreateSqlObject
    JdbiCompositeAnswer getJdbiCompositeAnswer();

    @CreateSqlObject
    AnswerSql getAnswerSql();

    @CreateSqlObject
    AnswerHistorySql getAnswerHistorySql();

    default long saveAnswer(long answerId) {
        QuestionType type = getAnswerSql().findQuestionTypesByAnswerIds(Set.of(answerId)).get(answerId);
        if (type == null) {
            throw new DaoException("Could not find question type for answer with id " + answerId);
        }

        if (type == QuestionType.COMPOSITE) {
            return saveAnswerWithCompositeValue(answerId);
        } else if (type == QuestionType.PICKLIST) {
            return saveAnswerWithPicklistValue(answerId);
        } else {
            return saveAnswerWithSimpleValue(answerId);
        }
    }

    private long saveAnswerWithSimpleValue(long answerId) {
        return getAnswerHistorySql().saveAnswerWithSimpleValue(answerId);
    }

    private long saveAnswerWithPicklistValue(long answerId) {
        AnswerHistorySql answerHistorySql = getAnswerHistorySql();
        long answerHistId = answerHistorySql.saveBaseAnswer(answerId);
        answerHistorySql.saveAnswerPicklistValue(answerId, answerHistId);
        return answerHistId;
    }

    private long saveAnswerWithCompositeValue(long answerId) {
        // todo
        // CompositeAnswerSummaryDto parentDto = jdbiCompositeAnswer.findCompositeAnswerSummary(answerId)
        //         .orElseThrow(() -> new DaoException("Could not find parent composite answer with id " + answerId));
        // parentDto.getChildAnswers().stream()
        //         .flatMap(Collection::stream)
        //         .filter(child -> child != null && child.getId() != null)
        //         .forEach(child -> childAnswerIds.add(child.getId()));
        AnswerHistorySql answerHistorySql = getAnswerHistorySql();
        long answerHistId = answerHistorySql.saveBaseAnswer(answerId);
        return answerHistId;
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("findHistoryByAnswerId")
    @UseRowReducer(HistoryAnswersWithValueReducer.class)
    List<Answer> findHistoryByAnswerId(@Bind("answerId") long answerId);

    class HistoryAnswersWithValueReducer implements LinkedHashMapRowReducer<Long, Answer> {
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
            long histId = view.getColumn("answer_history_id", Long.class);
            long answerId = view.getColumn("answer_id", Long.class);
            var answerGuid = view.getColumn("answer_guid", String.class);
            var questionStableId = view.getColumn("question_stable_id", String.class);
            var type = QuestionType.valueOf(view.getColumn("question_type", String.class));
            boolean isChildAnswer = view.getColumn("is_child_answer", Boolean.class);
            long updatedAt = view.getColumn("updated_at", Long.class);

            Answer answer;
            switch (type) {
                case AGREEMENT:
                    answer = new AgreementAnswer(answerId, questionStableId, answerGuid, view.getColumn("bool_value", Boolean.class));
                    break;
                case BOOLEAN:
                    answer = new BoolAnswer(answerId, questionStableId, answerGuid, view.getColumn("bool_value", Boolean.class));
                    break;
                case TEXT:
                    answer = new TextAnswer(answerId, questionStableId, answerGuid, view.getColumn("text_value", String.class));
                    break;
                case DATE:
                    answer = new DateAnswer(answerId, questionStableId, answerGuid,
                            view.getColumn("date_year", Integer.class),
                            view.getColumn("date_month", Integer.class),
                            view.getColumn("date_day", Integer.class));
                    break;
                case NUMERIC:
                    var numericType = NumericType.valueOf(view.getColumn("na_numeric_type", String.class));
                    if (numericType == NumericType.INTEGER) {
                        answer = new NumericIntegerAnswer(answerId, questionStableId, answerGuid,
                                view.getColumn("na_int_value", Long.class));
                    } else {
                        throw new DaoException("Unhandled numeric answer type " + numericType);
                    }
                    break;
                case PICKLIST:
                    var map = isChildAnswer ? childAnswers : container;
                    answer = map.computeIfAbsent(histId, id ->
                            new PicklistAnswer(answerId, questionStableId, answerGuid, new ArrayList<>()));
                    var optionStableId = view.getColumn("pa_option_stable_id", String.class);
                    if (optionStableId != null) {
                        var option = new SelectedPicklistOption(optionStableId, view.getColumn("pa_detail_text", String.class));
                        ((PicklistAnswer) answer).getValue().add(option);
                    }
                    break;
                case COMPOSITE:
                    answer = container.computeIfAbsent(histId, id -> {
                        var ans = new CompositeAnswer(answerId, questionStableId, answerGuid);
                        ans.setAllowMultiple(view.getColumn("ca_allow_multiple", Boolean.class));
                        ans.setUnwrapOnExport(view.getColumn("ca_unwrap_on_export", Boolean.class));
                        return ans;
                    });

                    // todo
                    // Long childAnswerId = view.getColumn("ca_child_answer_id", Long.class);
                    // if (childAnswerId != null) {
                    //     Answer childAnswer = childAnswers.get(childAnswerId);
                    //     if (childAnswer == null) {
                    //         throw new DaoException(String.format(
                    //                 "Could not find child answer with id=%d for composite answer %d", childAnswerId, answerId));
                    //     }
                    //
                    //     int childRow = view.getColumn("ca_child_row", Integer.class); // zero-indexed row number
                    //     int childCol = view.getColumn("ca_child_col", Integer.class); // zero-indexed column number
                    //     List<AnswerRow> rows = ((CompositeAnswer) answer).getValue();
                    //     while (rows.size() < childRow + 1) {
                    //         rows.add(new AnswerRow());
                    //     }
                    //     List<Answer> row = rows.get(childRow).getValues();
                    //     while (row.size() < childCol + 1) {
                    //         row.add(null);
                    //     }
                    //     row.set(childCol, childAnswer);
                    //
                    //     // Pull child answer out of map, indicating we have consumed it
                    //     childAnswers.remove(childAnswerId);
                    // }
                    break;
                default:
                    throw new DaoException("Unhandled answer type " + type);
            }

            answer.setUpdatedAt(updatedAt);
            if (isChildAnswer) {
                childAnswers.put(histId, answer);
                return null;
            } else {
                container.put(histId, answer);
                return answer;
            }
        }
    }
}
