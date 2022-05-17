package org.broadinstitute.ddp.route;

import java.util.List;

import lombok.AllArgsConstructor;
import org.broadinstitute.ddp.model.dsm.Cancer;
import org.broadinstitute.ddp.service.CancerService;

import spark.Request;
import spark.Response;
import spark.Route;

@AllArgsConstructor
public class ListCancersRoute implements Route {
    private final CancerService cancerService;

    @Override
    public List<Cancer> handle(Request request, Response response) {
        return cancerService.fetchCancers();
    }
}
