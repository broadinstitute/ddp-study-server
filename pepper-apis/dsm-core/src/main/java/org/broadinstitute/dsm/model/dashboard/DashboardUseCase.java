package org.broadinstitute.dsm.model.dashboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.dao.dashboard.DashboardDao;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardUseCase {

    private static final Logger logger = LoggerFactory.getLogger(DashboardUseCase.class);
    private DashboardDao dashboardDao;
    private ElasticSearchable elasticSearchable;

    public DashboardUseCase(DashboardDao dashboardDao, ElasticSearchable elasticSearchable) {
        this.dashboardDao = dashboardDao;
        this.elasticSearchable = elasticSearchable;
    }

    public List<DashboardData> getByDdpInstance(DDPInstanceDto ddpInstanceDto, String startDate, String endDate, boolean charts) {
        List<DashboardData> result = new ArrayList<>();
        List<DashboardDto> dashboardDtos = dashboardDao.getByInstanceId(ddpInstanceDto.getDdpInstanceId(), charts);
        logger.info("Collecting dashboard graphs for instance: " + ddpInstanceDto.getInstanceName());
        for (DashboardDto dashboardDto: dashboardDtos) {
            List<QueryBuilder> queryBuilders = getQueryBuildersFromDashboardDto(ddpInstanceDto, dashboardDto, startDate, endDate);
            MultiSearchResponse msearch = elasticSearchable
                    .executeMultiSearch(ddpInstanceDto.getEsParticipantIndex(), queryBuilders);
            ChartStrategyPayload chartStrategyPayload = new ChartStrategyPayload(dashboardDto, msearch);
            ChartStrategyFactory chartStrategyFactory = new ChartStrategyFactory(chartStrategyPayload);
            Supplier<DashboardData> chartStrategy = chartStrategyFactory.of();
            DashboardData dashboardData = chartStrategy.get();
            if (dashboardData != null) {
                result.add(dashboardData);
            }
        }
        Collections.sort(result);
        return result;
    }

    private List<QueryBuilder> getQueryBuildersFromDashboardDto(DDPInstanceDto ddpInstanceDto, DashboardDto dashboardDto,
                                                                String startDate, String endDate) {
        return dashboardDto.getLabels().stream()
                .map(labelDto -> new QueryBuildPayload(ddpInstanceDto, dashboardDto.getDisplayType(), labelDto, startDate, endDate))
                .map(QueryBuilderStrategyFactory::new)
                .map(factory -> factory.of().build())
                .collect(Collectors.toList());
    }
}
