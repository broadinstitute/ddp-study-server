package org.broadinstitute.dsm.route;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.Cancer;

import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class CancerRoute implements Route {

    @Override
    public Object handle(Request request, Response response) throws Exception {
        return Cancer.getCancers();
    }
}
