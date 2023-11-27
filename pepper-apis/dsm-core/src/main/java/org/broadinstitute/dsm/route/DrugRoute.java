package org.broadinstitute.dsm.route;

import java.util.List;

import org.broadinstitute.dsm.db.Drug;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class DrugRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(DrugRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        try {
            List<String> drugList = Drug.getDrugList();
            return drugList;
        } catch (Exception e) {
            logger.error("Drug list attempt gave an error: ", e);
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
    }
}





