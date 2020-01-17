package org.broadinstitute.ddp.db.dao;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface ActivityInstanceSql extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlQuery("queryFormResponseWithAnswers")
    @RegisterConstructorMapper(value = FormResponse.class, prefix = "a")
    @UseRowReducer(FormResponsesWithAnswersForUsersReducer.class)
    Optional<FormResponse> findFormResponseWithAnswers(
            @Define("byId") boolean byId,
            @Bind("instanceId") Long instanceId,
            @Bind("instanceGuid") String instanceGuid);

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
