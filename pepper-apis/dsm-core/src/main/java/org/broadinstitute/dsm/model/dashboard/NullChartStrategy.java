package org.broadinstitute.dsm.model.dashboard;

import java.util.function.Supplier;

public class NullChartStrategy implements Supplier<DashboardData> {

    @Override
    public DashboardData get() {
        return null;
    }
}
