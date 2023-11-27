package org.broadinstitute.dsm.db.dao.dashboard;

import java.util.List;

import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;

public interface DashboardDao extends Dao<DashboardDto> {
    List<DashboardDto> getByInstanceId(int instanceId, boolean charts);

    int createLabel(DashboardLabelDto dashboardLabelDto);

    int createFilter(DashboardLabelFilterDto dashboardLabelFilterDto);

    int deleteLabel(int labelId);

    int deleteFilter(int filterId);

}
