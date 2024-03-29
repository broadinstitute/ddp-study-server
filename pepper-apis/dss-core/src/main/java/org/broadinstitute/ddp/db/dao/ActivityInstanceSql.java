package org.broadinstitute.ddp.db.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface ActivityInstanceSql extends SqlObject {

    @GetGeneratedKeys
    @SqlBatch("insert into activity_instance_substitution (activity_instance_id, variable_name, value)"
            + "values (:instanceId, :var, :value)")
    long[] bulkInsertSubstitutions(
            @Bind("instanceId") long instanceId,
            @Bind("var") List<String> variableNames,
            @Bind("value") List<String> values);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryFormResponsesWithAnswers")
    @RegisterConstructorMapper(value = FormResponse.class, prefix = "a")
    @UseRowReducer(FormResponsesWithAnswersForUsersReducer.class)
    Stream<FormResponse> findFormResponseWithAnswers(
            @Define("byId") boolean byId,
            @BindList(value = "instanceIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> instanceIds,
            @BindList(value = "instanceGuids", onEmpty = BindList.EmptyHandling.NULL) Set<String> instanceGuids);

    @UseStringTemplateSqlLocator
    @SqlQuery("bulkQueryFormResponsesSubsetWithAnswersByStudyId")
    @RegisterConstructorMapper(value = FormResponse.class, prefix = "a")
    @UseRowReducer(FormResponsesWithAnswersForUsersReducer.class)
    Stream<FormResponse> findFormResponsesWithAnswersByStudyIdAndUsersWithActivityCodes(
            @Bind("studyId") long studyId,
            @Define("selectAll") boolean selectAll,
            @Define("byId") boolean byId,
            @BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> userIds,
            @BindList(value = "userGuids", onEmpty = BindList.EmptyHandling.NULL) Set<String> userGuids,
            @Define("limitActivities") boolean limitActivities,
            @BindList(value = "activityCodes", onEmpty = BindList.EmptyHandling.NULL) Set<String> activityCodes);

    @UseStringTemplateSqlLocator
    @SqlQuery("bulkQueryFormResponsesWithAnswersByUsersAndActivityIds")
    @RegisterConstructorMapper(value = FormResponse.class, prefix = "a")
    @UseRowReducer(FormResponsesWithAnswersForUsersReducer.class)
    Stream<FormResponse> findFormResponsesWithAnswersByUsersAndActivityIds(
            @Define("byId") boolean byId,
            @BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> userIds,
            @BindList(value = "userGuids", onEmpty = BindList.EmptyHandling.NULL) Set<String> userGuids,
            @BindList(value = "activityIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> activityIds);

    class FormResponsesWithAnswersForUsersReducer implements LinkedHashMapRowReducer<Long, FormResponse> {
        private Map<Long, Answer> answerContainer = new HashMap<>();
        private AnswerDao.AnswerWithValueReducer answerReducer = new AnswerDao.AnswerWithValueReducer();

        @Override
        public void accumulate(Map<Long, FormResponse> container, RowView view) {
            long instanceId = view.getColumn("a_instance_id", Long.class);
            FormResponse response = container.computeIfAbsent(instanceId, id -> view.getRow(FormResponse.class));
            Long answerId = view.getColumn("answer_id", Long.class);
            if (answerId != null) {
                Answer answer = answerReducer.reduce(answerContainer, view);
                if (answer != null) {
                    response.putAnswer(answer);
                }
            }
        }
    }
}
