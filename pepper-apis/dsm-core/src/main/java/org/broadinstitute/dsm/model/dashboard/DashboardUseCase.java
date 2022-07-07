package org.broadinstitute.dsm.model.dashboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.db.dao.dashboard.DashboardDao;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.broadinstitute.dsm.model.elastic.filter.FilterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseQueryBuilder;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public class DashboardUseCase {


    private DashboardDao dashboardDao;

    public DashboardUseCase(DashboardDao dashboardDao) {
        this.dashboardDao = dashboardDao;
    }

    public List<DashboardData> getByDdpInstance(DDPInstanceDto ddpInstanceDto) {
        List<DashboardDto> dashboardDtos = dashboardDao.getByInstanceId(ddpInstanceDto.getDdpInstanceId());
        AndOrFilterSeparator andOrFilterSeparator = new AndOrFilterSeparator(StringUtils.EMPTY);
        for (DashboardDto dashboardDto: dashboardDtos) {
            MultiSearchRequest request = new MultiSearchRequest();
            BaseQueryBuilder baseQueryBuilder = BaseQueryBuilderFactory.of(getNestedPath(dashboardDto));

            for (DashboardLabelDto label: dashboardDto.getLabels()) {
                SearchRequest searchRequest = new SearchRequest();
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                QueryBuilder queryBuilder = null;
                if (hasNestedPath(label)) {
                    if (hasAdditionalFilter(label)) {
                        andOrFilterSeparator.setFilter(label.getDashboardFilterDto().getAdditionalFilter());
                        Map<String, List<String>> andOrSeparated = andOrFilterSeparator.parseFiltersByLogicalOperators();
                        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
                        for (Map.Entry<String, List<String>> parsedFilter : andOrSeparated.entrySet()) {
                            FilterStrategy filterStrategy = FilterStrategy.of(parsedFilter.getKey());
                            for (String filterValue: parsedFilter.getValue()) {
                                Operator operator = Operator.extract(filterValue);
                                SplitterStrategy splitterStrategy = operator.getSplitterStrategy();
                                splitterStrategy.setFilter(filterValue);
                                String[] values = splitterStrategy.getValue();
                                QueryPayload queryPayload = new QueryPayload(label.getDashboardFilterDto().getEsNestedPath(),
                                        label.getDashboardFilterDto().getEsFilterPath(),
                                        values, ddpInstanceDto.getEsParticipantIndex());
                                filterStrategy.build(boolQueryBuilder, baseQueryBuilder.buildEachQuery(operator, queryPayload));
                            }
                        }
                        queryBuilder = boolQueryBuilder;
                    } else {
                        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery(label.getDashboardFilterDto().getEsFilterPath(),
                                label.getDashboardFilterDto().getEsFilterPathValue());
                        queryBuilder = new NestedQueryBuilder(label.getDashboardFilterDto().getEsFilterPath(), matchQuery, ScoreMode.Avg);
                    }
                }
                searchSourceBuilder.query(queryBuilder);
                searchRequest.source(searchSourceBuilder);
                searchRequest.indices(ddpInstanceDto.getEsParticipantIndex());
                request.add(searchRequest);
            }
            MultiSearchResponse msearch = ElasticSearchUtil.getClientInstance().msearch(request, RequestOptions.DEFAULT);
            List<String> x = new ArrayList<>();
            List<Long> y = new ArrayList<>();
            for (int i = 0; i < dashboardDto.getLabels().size(); i++) {
                x.add(dashboardDto.getLabels().get(i).getLabelName());
                MultiSearchResponse.Item response = msearch.getResponses()[i];
                y.add(response.getResponse().getHits().getTotalHits());
            }
        }
        BarChartData largeVerticalBarChart =
                new BarChartData(DisplayType.VERTICAL_BAR_CHART, List.of("green"), Size.LARGE, List.of("Your center"),
                        List.of(275), "Number of patients enrolled");
        BarChartData mediumBarChart = new BarChartData(DisplayType.HORIZONTAL_BAR_CHART, null, Size.MEDIUM, null, null, "Diagnosis");
        BarChartData mediumBarChart2 = new BarChartData(DisplayType.HORIZONTAL_BAR_CHART, null, Size.MEDIUM, null, null, "Age");
        BarChartData mediumBarChart3 = new BarChartData(DisplayType.HORIZONTAL_BAR_CHART, null, Size.SMALL, null, null, "Race");
        DonutData donutChart = new DonutData(DisplayType.DONUT, null, Size.SMALL, null, null, "Gender");
        return Arrays.asList(largeVerticalBarChart, mediumBarChart, mediumBarChart2, mediumBarChart3, donutChart);
    }

    private boolean hasAdditionalFilter(DashboardLabelDto label) {
        return StringUtils.isNotBlank(label.getDashboardFilterDto().getAdditionalFilter());
    }

    private boolean hasNestedPath(DashboardLabelDto label) {
        return StringUtils.isNotBlank(label.getDashboardFilterDto().getEsNestedPath());
    }

    private String getNestedPath(DashboardDto dashboardDto) {
        return dashboardDto.getLabels().stream()
                .map(DashboardLabelDto::getDashboardFilterDto)
                .map(DashboardLabelFilterDto::getEsNestedPath)
                .findFirst()
                .orElse(StringUtils.EMPTY);
    }
}
