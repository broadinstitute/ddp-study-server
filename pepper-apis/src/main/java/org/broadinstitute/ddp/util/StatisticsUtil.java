package org.broadinstitute.ddp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.JdbiQuestionCached;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.StatisticsConfigurationDao;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.statistics.StatisticsResponse;
import org.broadinstitute.ddp.json.statistics.StatisticsResponseItem;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.statistics.StatisticsConfiguration;
import org.broadinstitute.ddp.model.statistics.StatisticsType;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;

public class StatisticsUtil {
    //private static final Logger LOG = LoggerFactory.getLogger(StatisticsUtil.class);
    private static final String QUERIES_PATH = "org/broadinstitute/ddp/db/dao/Statistics.sql.stg";

    private static final Map<StatisticsType, String> STATISTICS_QUERIES = new HashMap<>();

    static {
        STATISTICS_QUERIES.put(StatisticsType.PARTICIPANTS, "getParticipantStatistics");
        STATISTICS_QUERIES.put(StatisticsType.DISTRIBUTION, "getAnswersDistributionByQueryStableId");
        STATISTICS_QUERIES.put(StatisticsType.MAILING_LIST, "getMailingListStatistics");
        STATISTICS_QUERIES.put(StatisticsType.SPECIFIC_ANSWER, "getSpecificAnswerStatistics");
        STATISTICS_QUERIES.put(StatisticsType.KITS, "getKitStatistics");
    }

    public static List<StatisticsResponse> generateStatisticsForStudy(Handle handle, StudyDto study,
                                                                      I18nContentRenderer renderer,
                                                                      ContentStyle style, long langId) {
        StatisticsConfigurationDao statConfigDao = handle.attach(StatisticsConfigurationDao.class);
        List<StatisticsConfiguration> statConfigs = statConfigDao.getStatisticsConfigurationForStudy(study.getId());
        List<StatisticsResponse> studyStatistics = new ArrayList<>();
        for (StatisticsConfiguration statConfig : statConfigs) {
            studyStatistics.add(generateStatisticsItem(handle, study.getUmbrellaId(), renderer, statConfig, style, langId));
        }
        return studyStatistics;
    }

    private static StatisticsResponse generateStatisticsItem(Handle handle, long umbrellaId,
                                                             I18nContentRenderer renderer,
                                                             StatisticsConfiguration statConfig,
                                                             ContentStyle style, long langId) {
        List<StatisticsResponseItem> statisticsItems;
        String query = StringTemplateSqlLocator.findStringTemplate(QUERIES_PATH, STATISTICS_QUERIES.get(statConfig.getType())).render();
        statisticsItems = handle.createQuery(query)
                .bind("studyId", umbrellaId)
                .bind("stableId", statConfig.getQuestionStableId())
                .bind("value", statConfig.getAnswerValue())
                .registerRowMapper(StatisticsResponseItem.class, (rs, ctx) -> {
                    String name = rs.getString(1);
                    Map<String, Object> data = new HashMap<>();
                    data.put("count", rs.getString(2));
                    return new StatisticsResponseItem(name, data);
                }).mapTo(StatisticsResponseItem.class).list();

        if (StatisticsType.DISTRIBUTION.equals(statConfig.getType())) {
            var jdbiQuestion = new JdbiQuestionCached(handle);
            QuestionDto questionDto = jdbiQuestion
                    .findLatestDtoByStudyIdAndQuestionStableId(umbrellaId, statConfig.getQuestionStableId())
                    .orElseThrow(() -> new DaoException(String.format(
                            "Could not find question with stable id %s in study %d",
                            statConfig.getQuestionStableId(), umbrellaId)));
            List<PicklistOptionDto> optionDtos =
                    handle.attach(QuestionDao.class).getPicklistQuestionDao().getJdbiPicklistOption()
                            .findAllActiveOrderedOptionsByQuestionId(questionDto.getId());
            List<PicklistOption> options = new ArrayList<>();

            Map<String, PicklistOption> optionByStableId = new HashMap<>();
            for (PicklistOptionDto optionDto : optionDtos) {
                PicklistOption option = new PicklistOption(optionDto.getStableId(),
                        optionDto.getOptionLabelTemplateId(), optionDto.getTooltipTemplateId(), optionDto.getDetailLabelTemplateId(),
                        optionDto.getAllowDetails(), optionDto.isExclusive());
                options.add(option);
                optionByStableId.put(option.getStableId(), option);
            }
            renderer.bulkRenderAndApply(handle, options, style, langId);
            statisticsItems.forEach((item) -> item.getData().put("optionDetails", optionByStableId.get(item.getName())));
        }

        return new StatisticsResponse(statConfig, statisticsItems);
    }
}
