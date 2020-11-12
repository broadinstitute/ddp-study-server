package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.model.statistics.StatisticsConfiguration;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface StatisticsConfigurationDao extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into statistics_configuration (umbrella_study_id, statistics_configuration_type, question_stable_id,"
            + "answer_value) values (:studyId, :typeId, :questionStableId, :answerValue)")
    long insertConfiguration(
            @Bind("studyId") long studyId,
            @Bind("typeId") long typeId,
            @Bind("questionStableId") String questionStableId,
            @Bind("answerValue") String answerValue);

    @SqlQuery("SELECT sc.umbrella_study_id, st.statistics_type_code, sc.question_stable_id, sc.answer_value FROM "
            + "statistics_configuration sc left join statistics_type st on sc.statistics_configuration_type = st.statistics_type_id "
            + "WHERE sc.umbrella_study_id = :umbrellaStudyId")
    @RegisterConstructorMapper(StatisticsConfiguration.class)
    List<StatisticsConfiguration> getStatisticsConfigurationForStudy(@Bind("umbrellaStudyId") long umbrellaStudyId);
}
