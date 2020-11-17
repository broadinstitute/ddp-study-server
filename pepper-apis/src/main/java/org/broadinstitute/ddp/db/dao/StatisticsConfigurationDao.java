package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.model.statistics.StatisticsConfiguration;
import org.broadinstitute.ddp.model.statistics.StatisticsType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface StatisticsConfigurationDao extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into statistics_configuration (umbrella_study_id, statistics_configuration_type, question_stable_id, "
            + "answer_value) values (:studyId, (select statistics_type_id from statistics_type where statistics_type_code = :type), "
            + ":questionStableId, :answerValue)")
    long insertConfiguration(
            @Bind("studyId") long studyId,
            @Bind("type") StatisticsType statisticsType,
            @Bind("questionStableId") String questionStableId,
            @Bind("answerValue") String answerValue);

    @SqlQuery("SELECT sc.umbrella_study_id, st.statistics_type_code, sc.question_stable_id, sc.answer_value FROM "
            + "statistics_configuration sc join statistics_type st on sc.statistics_configuration_type = st.statistics_type_id "
            + "WHERE sc.umbrella_study_id = :umbrellaStudyId")
    @RegisterConstructorMapper(StatisticsConfiguration.class)
    List<StatisticsConfiguration> getStatisticsConfigurationForStudy(@Bind("umbrellaStudyId") long umbrellaStudyId);
}
