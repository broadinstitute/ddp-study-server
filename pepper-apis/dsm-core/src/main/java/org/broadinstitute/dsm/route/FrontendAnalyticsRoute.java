package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.analytics.GoogleAnalyticsMetrics;
import org.broadinstitute.dsm.analytics.GoogleAnalyticsMetricsTracker;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class FrontendAnalyticsRoute extends RequestHandler {

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();

        String realm = null;
        int timer = 0;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
            if(queryParams.value("timer") != null){
                timer = (int) Math.ceil(Double.parseDouble(queryParams.get("timer").value())/1000);
                GoogleAnalyticsMetricsTracker.getInstance().sendAnalyticsMetrics(realm, GoogleAnalyticsMetrics.EVENT_CATEGORY_PARTICIPANT_LIST,
                        GoogleAnalyticsMetrics.EVENT_PARTICIPANT_LIST_FRONTEND_LOAD_TIME, GoogleAnalyticsMetrics.EVENT_PARTICIPANT_LIST_FRONTEND_LOAD_TIME,  timer);
                return new Result(200);
            }else{
                throw new RuntimeException("timer should not be empty");
            }
        }
        else{
            throw new RuntimeException("Realm should not be empty");
        }

    }
}
