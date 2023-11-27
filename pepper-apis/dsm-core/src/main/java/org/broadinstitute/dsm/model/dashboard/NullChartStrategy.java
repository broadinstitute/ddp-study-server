package org.broadinstitute.dsm.model.dashboard;

import java.util.function.Supplier;

class NullChartStrategy implements Supplier<DashboardData> {

    @Override
    public DashboardData get() {
        return null;
    }
}
