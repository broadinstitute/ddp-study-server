package org.broadinstitute.ddp.route;

import java.util.List;

import lombok.AllArgsConstructor;
import org.broadinstitute.ddp.db.dto.CancerItem;
import org.broadinstitute.ddp.service.CancerService;

import spark.Request;
import spark.Response;
import spark.Route;

import static org.broadinstitute.ddp.util.RouteUtil.getUserLanguage;

@AllArgsConstructor
public class ListCancersRoute implements Route {
    private final CancerService cancerService;

    @Override
    public List<CancerItem> handle(Request request, Response response) {
        String cancerLanguage = getUserLanguage(request).getIsoCode();
        return cancerService.fetchCancers(cancerLanguage);
    }
}
