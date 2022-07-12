package org.broadinstitute.dsm.model.dashboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.broadinstitute.dsm.db.dao.dashboard.DashboardDao;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public class DashboardUseCase {
    private DashboardDao dashboardDao;

    public DashboardUseCase(DashboardDao dashboardDao) {
        this.dashboardDao = dashboardDao;
    }

    public List<DashboardData> getByDdpInstance(DDPInstanceDto ddpInstanceDto) {
        List<DashboardData> result = new ArrayList<>();
        List<DashboardDto> dashboardDtos = dashboardDao.getByInstanceId(ddpInstanceDto.getDdpInstanceId());
        for (DashboardDto dashboardDto: dashboardDtos) {
            MultiSearchRequest request = new MultiSearchRequest();
            for (DashboardLabelDto label: dashboardDto.getLabels()) {
                SearchRequest searchRequest = new SearchRequest();
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                QueryBuildPayload queryBuildPayload = new QueryBuildPayload(ddpInstanceDto, label);
                QueryBuilderStrategyFactory queryBuilderStrategyFactory = new QueryBuilderStrategyFactory(queryBuildPayload);
                searchSourceBuilder.query(queryBuilderStrategyFactory.of().build());
                searchRequest.source(searchSourceBuilder);
                searchRequest.indices(ddpInstanceDto.getEsParticipantIndex());
                request.add(searchRequest);
            }
            MultiSearchResponse msearch = null;
            try {
                msearch = ElasticSearchUtil.getClientInstance().msearch(request, RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ChartStrategyPayload chartStrategyPayload = new ChartStrategyPayload(dashboardDto, msearch);
            ChartStrategyFactory chartStrategyFactory = new ChartStrategyFactory(chartStrategyPayload);
            Supplier<DashboardData> chartStrategy = chartStrategyFactory.of();
            result.add(chartStrategy.get());
        }
        Collections.sort(result);
        return result;
    }
}

