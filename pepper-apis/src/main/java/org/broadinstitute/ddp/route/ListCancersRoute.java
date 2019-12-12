package org.broadinstitute.ddp.route;

import java.util.List;

import org.broadinstitute.ddp.model.dsm.Cancer;
import org.broadinstitute.ddp.service.CancerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

public class ListCancersRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(ListCancersRoute.class);
    private final CancerService cancerService;

    public ListCancersRoute(CancerService cancerService) {
        this.cancerService = cancerService;
    }

    @Override
    public List<Cancer> handle(Request request, Response response) {
        return cancerService.fetchCancers();
    }

}
