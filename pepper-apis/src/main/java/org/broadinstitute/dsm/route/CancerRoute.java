package org.broadinstitute.dsm.route;

import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.Cancer;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.ArrayList;
import java.util.List;

public class CancerRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(CancerRoute.class);
    @Override
    public Object handle(Request request, Response response) throws Exception {
        List<String> drugList = new ArrayList<>();
        try {
            drugList = Cancer.getCancers();
            return drugList;
        }
        catch(Exception e) {
            logger.error("Cancer list attempt gave an error: " , e);
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
    }
}
