package org.broadinstitute.dsm.model.dashboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dao.dashboard.DashboardDao;
import org.broadinstitute.dsm.db.dao.dashboard.DashboardDaoImpl;
import org.broadinstitute.dsm.db.dao.dashboard.DashboardDaoImplTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.Util;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.statics.DBConstants;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DashboardUseCaseTest {
    public static final String ES_NESTED_PATH = "activities.questionsAnswers";
    public static final String ES_FILTER_PATH = "AGE";
    public static final String AGE = "Age";
    public static final DisplayType DISPLAY_TYPE = DisplayType.HORIZONTAL_BAR_CHART;
    public static final Size SIZE = Size.MEDIUM;
    public static final List<String> ADDITIONAL_FILTERS = Arrays.asList(
            "AND m.age >= 0 AND m.age <= 4",
            "AND m.age >= 5 AND m.age <= 10",
            "AND m.age >= 11 AND m.age <= 21",
            "AND m.age >= 22 AND m.age <= 39",
            "AND m.age >= 40 AND m.age <= 150"
    );
    private static final List<String> GENDERS = Arrays.asList("OTHER", "OTHER", "MALE", "FEMALE", "NON_BINARY");
    private static final List<String> RACES = Arrays.asList("INDIAN", "OTHER", "INDIAN", "ASIAN", "WHITE");
    private static final Map<DisplayType, List<String>> ES_FILTER_PATH_VALUES = Map.of(
            DisplayType.VERTICAL_BAR_CHART, Arrays.asList("NOT REPORTED", "OTHER", "MALE", "FEMALE", "NON_BINARY"),
            DisplayType.DONUT, Arrays.asList("WHITE", "ASIAN", "OTHER", "INDIAN", "NOT REPORTED")
    );

    public static final Integer VERTICAL_BAR_CHART_ORDER = 2;
    public static final String GENDER = "GENDER";
    public static final String ES_FILTER_PATH_VALUE = "FEMALE";
    private static final Integer DONUT_CHART_ORDER = 3;
    public static final String RACE = "RACE";
    private static DDPInstanceDto ddpInstanceDto;

    private static DDPInstanceDao ddpInstanceDao;
    private static DashboardDao dashboardDao;
    private static DashboardDto expectedAdditionalFilterDashboardDto;
    
    private static ElasticSearchable elasticSearchable;
    private static List<String> testParticipantsGuids;
    private static DashboardDto expectedNoAdditionalFilterDashboardDto;
    private static DashboardDto expectedNoAdditionalFilterNestedDashboardDto;


    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
        ddpInstanceDao = new DDPInstanceDao();
        ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName("atcp").orElseThrow();
        dashboardDao = new DashboardDaoImpl();
        elasticSearchable = new ElasticSearch();
        testParticipantsGuids = Arrays.asList(
                "TEST1111111111111112", "TEST1111111111111113", "TEST1111111111111114", "TEST1111111111111115", "TEST1111111111111116"
        );

        createDashboards();

        insertTestParticipantsToES();
    }

    @AfterClass
    public static void finish() {
        deleteDashboards();

        deleteTestParticipapntsFromES();
    }

    private static void createDashboards() {
        expectedAdditionalFilterDashboardDto = insertDashboard(buildHorizontalBarChart());
        expectedAdditionalFilterDashboardDto.setLabels(insertLabels(expectedAdditionalFilterDashboardDto, true));

        expectedNoAdditionalFilterDashboardDto = insertDashboard(buildVerticalBarChart());
        expectedNoAdditionalFilterDashboardDto.setLabels(insertLabels(expectedNoAdditionalFilterDashboardDto, false));

        expectedNoAdditionalFilterNestedDashboardDto = insertDashboard(buildDonutChart());
        expectedNoAdditionalFilterNestedDashboardDto.setLabels(insertLabels(expectedNoAdditionalFilterNestedDashboardDto, true));
    }

    private static DashboardDto buildDonutChart() {
        return new DashboardDto.Builder()
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withOrder(DONUT_CHART_ORDER)
                .withDisplayType(DisplayType.DONUT)
                .withSize(SIZE)
                .withDisplayText(RACE)
                .build();
    }

    private static DashboardDto buildVerticalBarChart() {
        return new DashboardDto.Builder()
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withOrder(VERTICAL_BAR_CHART_ORDER)
                .withDisplayType(DisplayType.VERTICAL_BAR_CHART)
                .withSize(SIZE)
                .withDisplayText(GENDER)
                .build();
    }

    private static DashboardDto insertDashboard(DashboardDto dashboardDto) {
        dashboardDto.setDashboardId(dashboardDao.create(dashboardDto));
        return dashboardDto;
    }

    private static DashboardDto buildHorizontalBarChart() {
        return new DashboardDto.Builder()
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withOrder(DashboardDaoImplTest.ORDER)
                .withDisplayType(DISPLAY_TYPE)
                .withSize(SIZE)
                .withDisplayText(AGE)
                .build();
    }

    private static List<DashboardLabelDto> insertLabels(DashboardDto dashboardDto, boolean nestedPath) {
        List<DashboardLabelDto> labels = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            DashboardLabelDto dashboardLabelDto = new DashboardLabelDto.Builder()
                    .withLabelName("Label " + i)
                    .withDashboardId(dashboardDto.getDashboardId())
                    .build();
            dashboardLabelDto.setLabelId(dashboardDao.createLabel(dashboardLabelDto));
            switch (dashboardDto.getDisplayType()) {
                case HORIZONTAL_BAR_CHART:
                    dashboardLabelDto.setDashboardFilterDto(
                            createLabelFilterWithAdditionalFilter(dashboardLabelDto.getLabelId(), ADDITIONAL_FILTERS.get(i)));
                    break;
                case VERTICAL_BAR_CHART:
                    dashboardLabelDto.setDashboardFilterDto(
                            createLabelFilterWithoutAdditionalFilter(dashboardLabelDto.getLabelId(),
                                    ES_FILTER_PATH_VALUES.get(dashboardDto.getDisplayType()).get(i)));
                    break;
                case DONUT:
                    dashboardLabelDto.setDashboardFilterDto(
                            createLabelFilterWithoutAdditionalFilterForNested(dashboardLabelDto.getLabelId(),
                                    ES_FILTER_PATH_VALUES.get(dashboardDto.getDisplayType()).get(i)));
                    break;
                default:
                    break;
            }
            labels.add(dashboardLabelDto);
        }
        return labels;
    }

    private static DashboardLabelFilterDto createLabelFilterWithoutAdditionalFilterForNested(Integer labelId, String esFilterPathValue) {
        DashboardLabelFilterDto dashboardLabelFilterDto = new DashboardLabelFilterDto.Builder()
                .withLabelId(labelId)
                .withEsNestedPath(ES_NESTED_PATH)
                .withEsFilterPath(RACE)
                .withEsFilterPathValue(esFilterPathValue)
                .build();
        dashboardLabelFilterDto.setLabelFilterId(dashboardDao.createFilter(dashboardLabelFilterDto));
        return dashboardLabelFilterDto;
    }

    private static DashboardLabelFilterDto createLabelFilterWithoutAdditionalFilter(Integer labelId, String esFilterPathValue) {
        DashboardLabelFilterDto dashboardLabelFilterDto = new DashboardLabelFilterDto.Builder()
                .withLabelId(labelId)
                .withEsFilterPath(String.join(DBConstants.ALIAS_DELIMITER, "dsm.surveyQuestion", GENDER))
                .withEsFilterPathValue(esFilterPathValue)
                .build();
        dashboardLabelFilterDto.setLabelFilterId(dashboardDao.createFilter(dashboardLabelFilterDto));
        return dashboardLabelFilterDto;
    }

    private static DashboardLabelFilterDto createLabelFilterWithAdditionalFilter(Integer labelId, String additionalFilter) {
        DashboardLabelFilterDto dashboardLabelFilterDto = new DashboardLabelFilterDto.Builder()
                .withLabelId(labelId)
                .withAdditionalFilter(additionalFilter)
                .withEsNestedPath(ES_NESTED_PATH)
                .withEsFilterPath(ES_FILTER_PATH)
                .build();
        dashboardLabelFilterDto.setLabelFilterId(dashboardDao.createFilter(dashboardLabelFilterDto));
        return  dashboardLabelFilterDto;
    }

    private static void deleteDashboards() {
        deleteDashboard(expectedAdditionalFilterDashboardDto);
        deleteDashboard(expectedNoAdditionalFilterDashboardDto);
        deleteDashboard(expectedNoAdditionalFilterNestedDashboardDto);
    }

    private static void deleteDashboard(DashboardDto dashboardDto) {
        dashboardDto.getLabels().stream()
                .map(DashboardLabelDto::getDashboardFilterDto)
                .map(DashboardLabelFilterDto::getLabelFilterId)
                .collect(Collectors.toList())
                .forEach(dashboardDao::deleteFilter);

        dashboardDto.getLabels().stream()
                .map(DashboardLabelDto::getLabelId)
                .forEach(dashboardDao::deleteLabel);

        Optional.of(dashboardDto)
                .map(DashboardDto::getDashboardId)
                .ifPresent(dashboardDao::delete);
    }

    private static void deleteTestParticipapntsFromES() {
        testParticipantsGuids.forEach(guid -> elasticSearchable.deleteDocumentById(ddpInstanceDto.getEsParticipantIndex(), guid));
    }

    private static void insertTestParticipantsToES() {
        Map<String, Integer> answer = new HashMap<>(Map.of("AGE", 4));
        Map<String, String> answer2 = new HashMap<>(Map.of(GENDER, GENDER));
        Map<String, String> answer3 = new HashMap<>(Map.of(RACE, RACE));
        Map<String, Object> surveyQuestion = new HashMap<>(Map.of("surveyQuestion", answer2));
        Map<String, Object> questionsAnswers = Map.of("questionsAnswers", List.of(answer, answer3));
        Map<String, Object> activities = Map.of(
                "activities", List.of(questionsAnswers),
                "dsm", surveyQuestion
        );
        for (int i = 0; i < testParticipantsGuids.size(); i++) {
            String guid = testParticipantsGuids.get(i);
            answer.put("AGE", i * 10);
            answer2.put(GENDER, GENDERS.get(i));
            answer3.put(RACE, RACES.get(i));
            elasticSearchable.createDocumentById(ddpInstanceDto.getEsParticipantIndex(), guid, activities);
        }
        Util.waitForCreationInElasticSearch();
    }


    @Test
    public void getByDdpInstance() {
        DashboardUseCase dashboardUseCase = new DashboardUseCase(new DashboardDaoImpl());
        List<DashboardData> dashboards = dashboardUseCase.getByDdpInstance(ddpInstanceDto);
        assertEquals(3, dashboards.size());

        testHorizontalBarChartWithAdditionalFilter(dashboards);

        testVerticalBarChartWithoutAdditionalFilter(dashboards);

        testDonutChartNestedWithoutAdditionalFilter(dashboards);
    }

    private void testDonutChartNestedWithoutAdditionalFilter(List<DashboardData> dashboards) {
        DashboardData dashboardData = dashboards.stream()
                .filter(data -> DisplayType.DONUT == data.getType())
                .findFirst()
                .orElseThrow();

        assertEquals(DisplayType.DONUT, dashboardData.getType());
        assertEquals(SIZE, dashboardData.getSize());
        assertEquals(RACE, dashboardData.getTitle());
        assertEquals(DONUT_CHART_ORDER, dashboardData.getOrdering());
        DonutData donutChart = (DonutData) dashboardData;
        List<String> expectedLabels = Arrays.asList("Label 0", "Label 1", "Label 2", "Label 3", "Label 4");
        List<Long> expectedValues = Arrays.asList(1L, 1L, 1L, 2L, 0L);
        assertArrayEquals(expectedLabels.toArray(), donutChart.getLabels().toArray());
        assertArrayEquals(expectedValues.toArray(), donutChart.getValues().toArray());
    }

    private void testVerticalBarChartWithoutAdditionalFilter(List<DashboardData> dashboards) {
        DashboardData verticalBarChart = dashboards.stream()
                .filter(data -> DisplayType.VERTICAL_BAR_CHART == data.getType())
                .findFirst()
                .orElseThrow();

        assertEquals(DisplayType.VERTICAL_BAR_CHART, verticalBarChart.getType());
        assertEquals(SIZE, verticalBarChart.getSize());
        assertEquals(GENDER, verticalBarChart.getTitle());
        assertEquals(VERTICAL_BAR_CHART_ORDER, verticalBarChart.getOrdering());
        BarChartData verticalBarChartData = (BarChartData) verticalBarChart;
        List<String> expectedLabels2 = Arrays.asList("Label 0", "Label 1", "Label 2", "Label 3", "Label 4");
        List<Long> expectedValues2 = Arrays.asList(0L, 2L, 1L, 1L, 1L);
        assertArrayEquals(expectedLabels2.toArray(), verticalBarChartData.getX().toArray());
        assertArrayEquals(expectedValues2.toArray(), verticalBarChartData.getY().toArray());
    }

    private void testHorizontalBarChartWithAdditionalFilter(List<DashboardData> dashboards) {
        DashboardData horizontalBarChart = dashboards.stream()
                .filter(data -> DisplayType.HORIZONTAL_BAR_CHART == data.getType())
                .findFirst().orElseThrow();
        assertEquals(DISPLAY_TYPE, horizontalBarChart.getType());
        assertEquals(SIZE, horizontalBarChart.getSize());
        assertEquals(AGE, horizontalBarChart.getTitle());
        assertEquals(DashboardDaoImplTest.ORDER, horizontalBarChart.getOrdering());
        BarChartData barChartData = (BarChartData) horizontalBarChart;
        List<String> expectedLabels = Arrays.asList("Label 0", "Label 1", "Label 2", "Label 3", "Label 4");
        List<Long> expectedValues = Arrays.asList(1L, 1L, 1L, 1L, 1L);
        assertArrayEquals(expectedLabels.toArray(), barChartData.getY().toArray());
        assertArrayEquals(expectedValues.toArray(), barChartData.getX().toArray());
    }

}
