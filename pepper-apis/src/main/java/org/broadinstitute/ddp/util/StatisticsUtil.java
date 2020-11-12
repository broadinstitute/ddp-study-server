package org.broadinstitute.ddp.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.db.dao.StatisticsConfigurationDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.StatisticsResponse;
import org.broadinstitute.ddp.model.statistics.StatisticsConfiguration;
import org.broadinstitute.ddp.model.statistics.StatisticsTypes;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsUtil {
    private static final Logger LOG = LoggerFactory.getLogger(StatisticsUtil.class);

    private static final Map<StatisticsTypes, String> STATISTICS_QUERIES = new HashMap<>();

    static {
        STATISTICS_QUERIES.put(StatisticsTypes.PARTICIPANTS,
                "select "
                    + "    case when ug.user_governance_id is null then 'self' else 'governed' end as user_type, "
                    + "    count(1) as user_count "
                    + "from "
                    + "    user_study_enrollment se "
                    + "    left join umbrella_study us on se.study_id = us.umbrella_study_id "
                    + "    left join user_governance ug on ug.participant_user_id = se.user_id "
                    + "    left join user_study_governance usg on usg.umbrella_study_id = us.umbrella_study_id "
                    + "        and usg.user_governance_id = ug.user_governance_id and ug.is_active = 1 "
                    + "where "
                    + "    us.umbrella_id = ? and "
                    + "    se.enrollment_status_type_id = 2 "
                    + "group by 1 ");

        STATISTICS_QUERIES.put(StatisticsTypes.DISTRIBUTION,
                "select "
                    + "po.picklist_option_stable_id as answer, "
                    + "    count(1) as count "
                    + "from "
                    + "question_stable_code qsc "
                    + "join umbrella_study us on qsc.umbrella_study_id = us.umbrella_study_id "
                    + "    join question q on qsc.question_stable_code_id = q.question_stable_code_id "
                    + "    join picklist_question pq on pq.question_id = q.question_id "
                    + "    join answer a on a.question_id = q.question_id "
                    + "    join activity_instance ai on ai.activity_instance_id = a.activity_instance_id "
                    + "    join user_study_enrollment se on ai.participant_id = se.user_id "
                    + "join picklist_option__answer poa on poa.answer_id = a.answer_id "
                    + "    join picklist_option po on po.picklist_option_id = poa.picklist_option_id "
                    + "where "
                    + "    us.umbrella_id = ? and "
                    + "    qsc.stable_id = ? and "
                    + "    se.enrollment_status_type_id = 2 "
                    + "group by 1 ");

        STATISTICS_QUERIES.put(StatisticsTypes.MAILING_LIST,
                "select "
                    + "    'subscriptions' as name, "
                    + "    count(1) as value "
                    + "from "
                    + "    study_mailing_list sml "
                    + "    join umbrella_study us on sml.umbrella_study_id = us.umbrella_study_id "
                    + "where "
                    + "     us.umbrella_id = ? ");

        STATISTICS_QUERIES.put(StatisticsTypes.SPECIFIC_ANSWER,
                "select "
                    + "    'answers' as name, "
                    + "    count(1) as value "
                    + "from "
                    + "    question_stable_code qsc "
                    + "    join umbrella_study us on qsc.umbrella_study_id = us.umbrella_study_id "
                    + "    join question q on qsc.question_stable_code_id = q.question_stable_code_id "
                    + "    join answer a on a.question_id = q.question_id "
                    + "    join activity_instance ai on ai.activity_instance_id = a.activity_instance_id "
                    + "    join user_study_enrollment se on ai.participant_id = se.user_id "
                    + "    left join picklist_option__answer poa on poa.answer_id = a.answer_id "
                    + "    left join picklist_option po on po.picklist_option_id = poa.picklist_option_id "
                    + "    left join picklist_question pq on pq.question_id = q.question_id "
                    + "    left join boolean_answer ba on ba.answer_id = a.answer_id "
                    + "where "
                    + "    us.umbrella_id = ? and "
                    + "    qsc.stable_id = ? and "
                    + "    se.enrollment_status_type_id = 2 and "
                    + "    ? = coalesce(po.picklist_option_stable_id, export_set(ba.answer, 'true', 'false', '', 1)) "
                    + "group by 1 ");

        STATISTICS_QUERIES.put(StatisticsTypes.KITS,
                "select "
                    + "    kt.name as name, "
                    + "    count(1) as value "
                    + "from "
                    + "    kit_request kr "
                    + "    join umbrella_study us on kr.study_id = us.umbrella_study_id "
                    + "    left join kit_type kt on kt.kit_type_id = kr.kit_type_id "
                    + "where "
                    + "    us.umbrella_id = ? "
                    + "group by 1 ");
    }

    public static List<StatisticsResponse> generateStatisticsForStudy(Handle handle, StudyDto study) {
        StatisticsConfigurationDao statConfigDao = handle.attach(StatisticsConfigurationDao.class);
        List<StatisticsConfiguration> statConfigs = statConfigDao.getStatisticsConfigurationForStudy(study.getId());
        List<StatisticsResponse> studyStatistics = new ArrayList<>();
        for (StatisticsConfiguration statConfig : statConfigs) {
            studyStatistics.add(generateStatisticsItem(handle, study.getUmbrellaId(), statConfig));
        }
        return studyStatistics;
    }

    private static StatisticsResponse generateStatisticsItem(Handle handle, long umbrellaId, StatisticsConfiguration statConfig) {
        Map<String, Object> statisticsItem = new HashMap<>();
        try (PreparedStatement stmt
                     = handle.getConnection().prepareStatement(STATISTICS_QUERIES.get(statConfig.getType()))) {
            int paramIndex = 1;
            stmt.setLong(paramIndex++, umbrellaId);
            if (statConfig.getQuestionStableId() != null) {
                stmt.setString(paramIndex++, statConfig.getQuestionStableId());
            }
            if (statConfig.getAnswerValue() != null) {
                stmt.setString(paramIndex, statConfig.getAnswerValue());
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                statisticsItem.put(name, value);
            }
        } catch (SQLException e) {
            LOG.warn("Could not compute statistics item {} for study {} ", statConfig.getType(), statConfig.getStudyId());
            throw new DDPException(e);
        }
        return new StatisticsResponse(statConfig, statisticsItem);
    }
}
