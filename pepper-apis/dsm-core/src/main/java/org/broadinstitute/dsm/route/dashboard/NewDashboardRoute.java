package org.broadinstitute.dsm.route.dashboard;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.dashboard.DashboardDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.dashboard.DashboardUseCase;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import spark.Request;
import spark.Response;

public class NewDashboardRoute extends RequestHandler {

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = Optional.ofNullable(request.queryMap().get(RoutePath.REALM).value()).orElseThrow().toLowerCase();
        String startDate = null;
        String endDate = null;
        if (request.queryMap().hasKey("startDate") && request.queryMap().hasKey("endDate")) {
            startDate = request.queryMap().get("startDate").value();
            endDate = request.queryMap().get("endDate").value();
        }
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        String part = Optional.ofNullable(request.queryMap().get("part").value()).orElse("CHART").toUpperCase();
        DashboardUseCase dashboardUseCase = new DashboardUseCase(new DashboardDaoImpl(), new ElasticSearch());
        return dashboardUseCase.getByDdpInstance(ddpInstanceDto, startDate, endDate, part.equals("COUNT") ? true : false);
    }
}
