package org.broadinstitute.dsm.db.dao.dashboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;
import org.broadinstitute.dsm.model.dashboard.DisplayType;
import org.broadinstitute.dsm.model.dashboard.Size;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DashboardDaoImplTest {

    private static final String ES_FILTER_PATH = "TEST_CANCER_DIAGNOSED_AGE";
    private static final String ES_NESTED_PATH = "test.activities.questionsAnswers";
    private static final String ADDITIONAL_FILTER = "AND m.age >= 0 AND m.age <= 4";
    private static final String ES_FILTER_PATH_VALUE = "TEST_VALUE";
    public static final String DISPLAY_TEXT = "TestDiagnosis";
    public static final DisplayType DISPLAY_TYPE = DisplayType.HORIZONTAL_BAR_CHART;
    public static final Integer ORDER = 1;
    public static final Size SIZE = Size.MEDIUM;
    public static final Integer DDP_INSTANCE_ID = 16;

    private static Integer createdDashboardId;

    private static List<DashboardLabelDto> labels = new ArrayList<>();
    private static List<DashboardLabelFilterDto> labelFilters = new ArrayList<>();
    private static DashboardDao dashboardDao = new DashboardDaoImpl();


    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
        createDashboard();
    }

    @AfterClass
    public static void finish() {
        //such ordering of deletion is necessary because of foreign key connections
        if (labelFilters.size() > 0) {
            labelFilters.stream().map(DashboardLabelFilterDto::getLabelFilterId).forEach(dashboardDao::deleteFilter);
        }
        if (labels.size() > 0) {
            labels.stream().map(DashboardLabelDto::getLabelId).forEach(dashboardDao::deleteLabel);
        }
        if (createdDashboardId > 0) {
            dashboardDao.delete(createdDashboardId);
        }
    }

    public static void createDashboard() {
        DashboardDto dashboardDto = new DashboardDto.Builder()
                .withDdpInstanceId(DDP_INSTANCE_ID)
                .withDisplayText(DISPLAY_TEXT)
                .withDisplayType(DISPLAY_TYPE)
                .withOrder(ORDER)
                .withSize(SIZE)
                .build();
        createdDashboardId = dashboardDao.create(dashboardDto);
        if (createdDashboardId > 0) {
            setLabels(createdDashboardId);
            dashboardDto.setLabels(labels);
        }
    }

    private static void setLabels(int dashboardId) {
        for (int i = 0; i < 5; i++) {
            DashboardLabelDto dashboardLabelDto = new DashboardLabelDto.Builder()
                    .withDashboardId(dashboardId)
                    .withLabelName("Label" + i)
                    .build();
            int labelId = dashboardDao.createLabel(dashboardLabelDto);
            dashboardLabelDto.setLabelId(labelId);
            DashboardLabelFilterDto dashboardLabelFilter = getDashboardLabelFilter(labelId);
            dashboardLabelDto.setDashboardFilterDto(dashboardLabelFilter);
            labelFilters.add(dashboardLabelFilter);
            labels.add(dashboardLabelDto);
        }
    }

    private static DashboardLabelFilterDto getDashboardLabelFilter(int labelId) {
        DashboardLabelFilterDto labelFilterDto = new DashboardLabelFilterDto.Builder()
                .withLabelId(labelId)
                .withEsFilterPath(ES_FILTER_PATH)
                .withEsNestedPath(ES_NESTED_PATH)
                .withAdditionalFilter(ADDITIONAL_FILTER)
                .withEsFilterPathValue(ES_FILTER_PATH_VALUE)
                .build();
        labelFilterDto.setLabelFilterId(dashboardDao.createFilter(labelFilterDto));
        return labelFilterDto;
    }

    @Test
    public void get() {
        try {
            DashboardDto dashboardDto = dashboardDao.get(createdDashboardId).orElseThrow();
            testDashboard(dashboardDto);
        } catch (Exception e) {
            Assert.fail();
        }
    }

    private void testDashboard(DashboardDto dashboardDto) {
        assertEquals(DDP_INSTANCE_ID, dashboardDto.getDdpInstanceId());
        assertEquals(createdDashboardId, dashboardDto.getDashboardId());
        assertEquals(ORDER, dashboardDto.getOrder());
        assertEquals(DISPLAY_TEXT, dashboardDto.getDisplayText());
        assertEquals(DISPLAY_TYPE, dashboardDto.getDisplayType());
        testLabels(dashboardDto.getLabels());
        testFilters(dashboardDto.getLabels().stream().map(DashboardLabelDto::getDashboardFilterDto).collect(Collectors.toList()));
    }

    private void testFilters(List<DashboardLabelFilterDto> actualFilters) {
        assertArrayEquals(labelFilters.toArray(), actualFilters.toArray());
    }

    private void testLabels(List<DashboardLabelDto> actualLabels) {
        assertArrayEquals(labels.toArray(), actualLabels.toArray());
    }

    @Test
    public void getByInstanceId() {
        List<DashboardDto> byInstanceId = dashboardDao.getByInstanceId(DDP_INSTANCE_ID, true);
        try {
            DashboardDto dashboardDto = byInstanceId.get(0);
            testDashboard(dashboardDto);
        } catch (Exception e) {
            Assert.fail();
        }
    }

}
