package org.broadinstitute.dsm.model.dashboard;

import static org.junit.Assert.assertEquals;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;
import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.junit.Test;

public class QueryBuildPayloadTest {

    @Test
    public void getFilterSeparator() {

        QueryBuildPayload queryBuildPayload = new QueryBuildPayload();

        DashboardLabelFilterDto dashboardLabelFilterDto =
                new DashboardLabelFilterDto.Builder().withEsNestedPath("dsm.kitRequestShipping").build();
        assertEquals(AndOrFilterSeparator.class, queryBuildPayload.getFilterSeparator().getClass());

        //      dashboardLabelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("status").build();
        //      assertEquals(NonDsmAndOrFilterSeparator.class, queryBuildPayload.getFilterSeparator(dashboardLabelFilterDto).getClass());
        //
        //      dashboardLabelFilterDto = new DashboardLabelFilterDto.Builder().withEsFilterPath("RACE")\.withEsNestedPath("activities
        //      .questionsAnswers").build();
        //      assertEquals(NonDsmAndOrFilterSeparator.class, queryBuildPayload.getFilterSeparator(dashboardLabelFilterDto).getClass());

    }
}
