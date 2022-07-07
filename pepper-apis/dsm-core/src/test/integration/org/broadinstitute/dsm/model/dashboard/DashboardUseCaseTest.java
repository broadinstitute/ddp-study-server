package org.broadinstitute.dsm.model.dashboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.dao.dashboard.DashboardDao;
import org.broadinstitute.dsm.db.dao.dashboard.DashboardDaoImpl;
import org.broadinstitute.dsm.db.dao.dashboard.DashboardDaoImplTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DashboardUseCaseTest {
    private static final String INDEX = "participants_structured.atcp.atcp";
    private static final int ATCP_ID = 16;
    public static final String ES_NESTED_PATH = "activities.questionsAnswers";
    public static final String ES_FILTER_PATH = "AGE";
    private static DDPInstanceDto ddpInstanceDto;

    private static DDPInstanceDao ddpInstanceDao;
    private static DashboardDao dashboardDao;
    private static DashboardDto expectedDashboardDto;
    
    private static ElasticSearchable elasticSearchable;
    private static List<String> testParticipantsGuids;


    @BeforeClass
    public static void setUp() {
        ddpInstanceDao = new DDPInstanceDao();
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName("atcp").orElseThrow();
        dashboardDao = new DashboardDaoImpl();
        elasticSearchable = new ElasticSearch();
        testParticipantsGuids = Arrays.asList(
                "TEST1111111111111112", "TEST1111111111111113", "TEST1111111111111114", "TEST1111111111111115", "TEST1111111111111116"
        );

        createDashboard();

        insertTestParticipantsToES();
    }

    @AfterClass
    public static void finish() {
        deleteDashboard();

        deleteTestParticipapntsFromES();
    }

    private static void createDashboard() {
        expectedDashboardDto = insertDashboard();
        expectedDashboardDto.setLabels(insertLabels(expectedDashboardDto));
    }

    private static DashboardDto insertDashboard() {
        DashboardDto dashboardDto = new DashboardDto.Builder()
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withOrder(DashboardDaoImplTest.ORDER)
                .withDisplayType(DisplayType.HORIZONTAL_BAR_CHART)
                .withSize(Size.MEDIUM)
                .build();
        dashboardDto.setDashboardId(dashboardDao.create(dashboardDto));
        return dashboardDto;
    }

    private static List<DashboardLabelDto> insertLabels(DashboardDto dashboardDto) {
        List<DashboardLabelDto> labels = new ArrayList<>();
        List<String> additionalFilters = Arrays.asList(
                "AND m.age >= 0 AND m.age <= 4",
                "AND m.age >= 5 AND m.age <= 10",
                "AND m.age >= 11 AND m.age <= 21",
                "AND m.age >= 22 AND m.age <= 39",
                "AND m.age >= 40 AND m.age <= 150"
        );
        for (int i = 0; i < 5; i++) {
            DashboardLabelDto dashboardLabelDto = new DashboardLabelDto.Builder()
                    .withLabelName("Label " + i)
                    .withDashboardId(dashboardDto.getDashboardId())
                    .build();
            dashboardLabelDto.setLabelId(dashboardDao.createLabel(dashboardLabelDto));
            dashboardLabelDto.setDashboardFilterDto(createLabelFilter(dashboardLabelDto.getLabelId(), additionalFilters.get(i)));
            labels.add(dashboardLabelDto);
        }
        return labels;
    }

    private static DashboardLabelFilterDto createLabelFilter(Integer labelId, String additionalFilter) {
        DashboardLabelFilterDto dashboardLabelFilterDto = new DashboardLabelFilterDto.Builder()
                .withLabelId(labelId)
                .withAdditionalFilter(additionalFilter)
                .withEsNestedPath(ES_NESTED_PATH)
                .withEsFilterPath(ES_FILTER_PATH)
                .build();
        dashboardLabelFilterDto.setLabelFilterId(dashboardDao.createFilter(dashboardLabelFilterDto));
        return  dashboardLabelFilterDto;
    }

    private static void deleteDashboard() {
        expectedDashboardDto.getLabels().stream()
                .map(DashboardLabelDto::getDashboardFilterDto)
                .map(DashboardLabelFilterDto::getLabelFilterId)
                .collect(Collectors.toList())
                .forEach(dashboardDao::deleteFilter);

        expectedDashboardDto.getLabels().stream()
                .map(DashboardLabelDto::getLabelId)
                .forEach(dashboardDao::deleteLabel);

        Optional.ofNullable(expectedDashboardDto)
                .map(DashboardDto::getDashboardId)
                .ifPresent(dashboardDao::delete);
    }

    private static void deleteTestParticipapntsFromES() {
        testParticipantsGuids.forEach(guid -> elasticSearchable.deleteDocumentById(ddpInstanceDto.getEsParticipantIndex(), guid));
    }

    private static void insertTestParticipantsToES() {
        Map<String, Integer> answer = new HashMap<>(Map.of("AGE", 4));
        Map<String, Object> questionsAnswers = Map.of("questionsAnswers", List.of(answer));
        Map<String, Object> activities = Map.of("activities", List.of(questionsAnswers));
        for (int i = 0; i < testParticipantsGuids.size(); i++) {
            String guid = testParticipantsGuids.get(i);
            answer.put("AGE", i * 10);
            elasticSearchable.createDocumentById(ddpInstanceDto.getEsParticipantIndex(), guid, activities);
        }
    }





    @Test
    public void getByInstanceId() {
        DashboardUseCase dashboardUseCase = new DashboardUseCase(new DashboardDaoImpl());
        List<DashboardData> dashboards = dashboardUseCase.getByDdpInstance(ddpInstanceDto);
        Assert.assertEquals(5, dashboards.size());
        Assert.assertEquals(3,
                dashboards.stream().filter(dashboardData -> DisplayType.HORIZONTAL_BAR_CHART == dashboardData.type).count());
        Assert.assertEquals(1, dashboards.stream().filter(dashboardData -> Size.LARGE == dashboardData.size).count());
        Assert.assertEquals(2, dashboards.stream().filter(dashboardData -> Size.MEDIUM == dashboardData.size).count());
        Assert.assertEquals(2, dashboards.stream().filter(dashboardData -> Size.SMALL == dashboardData.size).count());
        Assert.assertEquals(List.of("green"),
                dashboards.stream().filter(dashboardData -> Size.LARGE == dashboardData.size).findFirst().get().color);
    }

}