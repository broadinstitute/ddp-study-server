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
import org.broadinstitute.dsm.Util;
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
import org.broadinstitute.dsm.statics.DBConstants;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DashboardUseCaseTest {
    static final String ES_NESTED_PATH = "activities.questionsAnswers";
    static final String ES_FILTER_PATH_AGE = "AGE";
    static final String AGE = "Age";
    static final DisplayType DISPLAY_TYPE = DisplayType.HORIZONTAL_BAR_CHART;
    static final Size SIZE = Size.MEDIUM;
    static final List<String> ADDITIONAL_FILTERS = Arrays.asList(
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
            DisplayType.DONUT_CHART, Arrays.asList("WHITE", "ASIAN", "OTHER", "INDIAN", "NOT REPORTED")
    );

    static final Integer VERTICAL_BAR_CHART_ORDER = 2;
    static final String GENDER = "GENDER";
    static final String ES_FILTER_PATH_VALUE = "FEMALE";
    private static final Integer DONUT_CHART_ORDER = 3;
    static final String RACE = "RACE";
    static final Integer HORIZONTAL_BAR_CHART_2_ORDER = 4;
    static final Size HORIZONTAL_BAR_CHART_2_SIZE = Size.SMALL;
    static final String HORIZONTAL_BAR_CHART_2_DISPLAY_TEXT = "Diagnosis";
    private static DDPInstanceDto ddpInstanceDto;

    private static DDPInstanceDao ddpInstanceDao;
    private static DashboardDao dashboardDao;
    private static DashboardDto expectedAdditionalFilterDashboardDto;
    
    private static ElasticSearchable elasticSearchable;
    private static List<String> testParticipantsGuids;
    private static DashboardDto expectedNoAdditionalFilterDashboardDto;
    private static DashboardDto expectedNoAdditionalFilterNestedDashboardDto;
    private static DashboardDto expectedSingleWithAdditionalFilter;


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
        expectedAdditionalFilterDashboardDto.setLabels(insertLabels(expectedAdditionalFilterDashboardDto));

        expectedNoAdditionalFilterDashboardDto = insertDashboard(buildVerticalBarChart());
        expectedNoAdditionalFilterDashboardDto.setLabels(insertLabels(expectedNoAdditionalFilterDashboardDto));

        expectedNoAdditionalFilterNestedDashboardDto = insertDashboard(buildDonutChart());
        expectedNoAdditionalFilterNestedDashboardDto.setLabels(insertLabels(expectedNoAdditionalFilterNestedDashboardDto));

        expectedSingleWithAdditionalFilter = insertDashboard(buildHorizontalBarChart2());
        expectedSingleWithAdditionalFilter.setLabels(insertLabels(expectedSingleWithAdditionalFilter));
    }

    private static DashboardDto buildHorizontalBarChart2() {
        return new DashboardDto.Builder()
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withOrder(HORIZONTAL_BAR_CHART_2_ORDER)
                .withDisplayType(DISPLAY_TYPE)
                .withSize(HORIZONTAL_BAR_CHART_2_SIZE)
                .withDisplayText(HORIZONTAL_BAR_CHART_2_DISPLAY_TEXT)
                .build();
    }

    private static DashboardDto buildDonutChart() {
        return new DashboardDto.Builder()
                .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withOrder(DONUT_CHART_ORDER)
                .withDisplayType(DisplayType.DONUT_CHART)
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

    private static List<DashboardLabelDto> insertLabels(DashboardDto dashboardDto) {
        List<DashboardLabelDto> labels = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            DashboardLabelDto dashboardLabelDto = new DashboardLabelDto.Builder()
                    .withLabelName("Label " + i)
                    .withDashboardId(dashboardDto.getDashboardId())
                    .withColor("Label " + i + " color")
                    .build();
            dashboardLabelDto.setLabelId(dashboardDao.createLabel(dashboardLabelDto));
            switch (dashboardDto.getDisplayType()) {
                case HORIZONTAL_BAR_CHART:
                    if (dashboardDto.getSize() == Size.SMALL) {
                        dashboardLabelDto.setDashboardFilterDto(
                                createLabelFilterForSingleWithAdditionalFilter(dashboardLabelDto.getLabelId(), ADDITIONAL_FILTERS.get(i)));
                    } else {
                        dashboardLabelDto.setDashboardFilterDto(
                                createLabelFilterWithAdditionalFilter(dashboardLabelDto.getLabelId(), ADDITIONAL_FILTERS.get(i)));
                    }
                    break;
                case VERTICAL_BAR_CHART:
                    dashboardLabelDto.setDashboardFilterDto(
                            createLabelFilterWithoutAdditionalFilter(dashboardLabelDto.getLabelId(),
                                    ES_FILTER_PATH_VALUES.get(dashboardDto.getDisplayType()).get(i)));
                    break;
                case DONUT_CHART:
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

    private static DashboardLabelFilterDto createLabelFilterForSingleWithAdditionalFilter(Integer labelId, String additionalFilter) {
        DashboardLabelFilterDto dashboardLabelFilterDto = new DashboardLabelFilterDto.Builder()
                .withLabelId(labelId)
                .withAdditionalFilter(additionalFilter)
                .withEsFilterPath(String.join(DBConstants.ALIAS_DELIMITER, "dsm.surveyQuestion", ES_FILTER_PATH_AGE))
                .build();
        dashboardLabelFilterDto.setLabelFilterId(dashboardDao.createFilter(dashboardLabelFilterDto));
        return  dashboardLabelFilterDto;
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
                .withEsFilterPath(ES_FILTER_PATH_AGE)
                .build();
        dashboardLabelFilterDto.setLabelFilterId(dashboardDao.createFilter(dashboardLabelFilterDto));
        return  dashboardLabelFilterDto;
    }

    private static void deleteDashboards() {
        deleteDashboard(expectedAdditionalFilterDashboardDto);
        deleteDashboard(expectedNoAdditionalFilterDashboardDto);
        deleteDashboard(expectedNoAdditionalFilterNestedDashboardDto);
        deleteDashboard(expectedSingleWithAdditionalFilter);
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
        Map<String, Object> answer2 = new HashMap<>(Map.of(
                GENDER, GENDER,
                "AGE", 4
        ));
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
            answer2.put("AGE", i * 10);
            answer3.put(RACE, RACES.get(i));
            elasticSearchable.createDocumentById(ddpInstanceDto.getEsParticipantIndex(), guid, activities);
        }
        try {
            Util.waitForCreationInElasticSearch();
        } catch (InterruptedException e) {
            Assert.fail();
        }
    }


    @Test
    public void getByDdpInstance() {
        DashboardUseCase dashboardUseCase = new DashboardUseCase(new DashboardDaoImpl(), new ElasticSearch());
        List<DashboardData> dashboards = dashboardUseCase.getByDdpInstance(ddpInstanceDto, null, null, true);
        assertEquals(4, dashboards.size());

        testOrdering(dashboards);

        testHorizontalBarChartNestedWithAdditionalFilter(dashboards);

        testVerticalBarChartSingleWithoutAdditionalFilter(dashboards);

        testDonutChartNestedWithoutAdditionalFilter(dashboards);

        testHorizontalBarChartSingleWithAdditionalFilter(dashboards);
    }

    private void testOrdering(List<DashboardData> dashboards) {
        for (int i = 0; i < dashboards.size(); i++) {
            assertEquals(Integer.valueOf(i + 1), dashboards.get(i).getOrdering());
        }
    }

    private void testDonutChartNestedWithoutAdditionalFilter(List<DashboardData> dashboards) {
        DashboardData dashboardData = dashboards.stream()
                .filter(data -> DisplayType.DONUT_CHART == data.getType())
                .findFirst()
                .orElseThrow();

        assertEquals(DisplayType.DONUT_CHART, dashboardData.getType());
        assertEquals(SIZE, dashboardData.getSize());
        assertEquals(RACE, dashboardData.getTitle());
        assertEquals(DONUT_CHART_ORDER, dashboardData.getOrdering());
        DonutData donutChart = (DonutData) dashboardData;
        List<String> expectedLabels = Arrays.asList("Label 0", "Label 1", "Label 2", "Label 3", "Label 4");
        List<String> expectedColors = Arrays.asList("Label 0 color", "Label 1 color", "Label 2 color", "Label 3 color", "Label 4 color");
        List<Long> expectedValues = Arrays.asList(1L, 1L, 1L, 2L, 0L);
        assertArrayEquals(expectedLabels.toArray(), donutChart.getLabels().toArray());
        assertArrayEquals(expectedValues.toArray(), donutChart.getValues().toArray());
        assertArrayEquals(expectedColors.toArray(), donutChart.getColor().toArray());
    }

    private void testVerticalBarChartSingleWithoutAdditionalFilter(List<DashboardData> dashboards) {
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
        assertArrayEquals(expectedLabels2.toArray(), verticalBarChartData.getValuesX().toArray());
        assertArrayEquals(expectedValues2.toArray(), verticalBarChartData.getValuesY().toArray());
    }

    private void testHorizontalBarChartNestedWithAdditionalFilter(List<DashboardData> dashboards) {
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
        assertArrayEquals(expectedLabels.toArray(), barChartData.getValuesY().toArray());
        assertArrayEquals(expectedValues.toArray(), barChartData.getValuesX().toArray());
    }

    private void testHorizontalBarChartSingleWithAdditionalFilter(List<DashboardData> dashboards) {
        DashboardData horizontalBarChart = dashboards.stream()
                .filter(data -> DisplayType.HORIZONTAL_BAR_CHART == data.getType())
                .collect(Collectors.toList())
                .get(1);
        assertEquals(DISPLAY_TYPE, horizontalBarChart.getType());
        assertEquals(HORIZONTAL_BAR_CHART_2_SIZE, horizontalBarChart.getSize());
        assertEquals(HORIZONTAL_BAR_CHART_2_DISPLAY_TEXT, horizontalBarChart.getTitle());
        assertEquals(HORIZONTAL_BAR_CHART_2_ORDER, horizontalBarChart.getOrdering());
        BarChartData barChartData = (BarChartData) horizontalBarChart;
        List<String> expectedLabels = Arrays.asList("Label 0", "Label 1", "Label 2", "Label 3", "Label 4");
        List<Long> expectedValues = Arrays.asList(1L, 1L, 1L, 1L, 1L);
        assertArrayEquals(expectedLabels.toArray(), barChartData.getValuesY().toArray());
        assertArrayEquals(expectedValues.toArray(), barChartData.getValuesX().toArray());
    }

}
