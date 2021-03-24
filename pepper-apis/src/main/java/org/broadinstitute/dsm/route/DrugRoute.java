package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.db.Drug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.statics.UserErrorMessages;

import java.util.List;

public class DrugRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(DrugRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        try {
            List<String> drugList = Drug.getDrugList();
            return drugList;
        }
        catch(Exception e) {
            logger.error("Drug list attempt gave an error: " , e);
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
    }
}





