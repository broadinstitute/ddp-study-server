package org.broadinstitute.dsm.route;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.dsm.util.SecurityUtil;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.HashMap;
import java.util.Map;
//TODO pegah remove when the whole Authentication is done, works as a dummy endpoint now

public class DSSTestingRoute implements Route {
    private static final Logger logger = LoggerFactory.getLogger(DSSTestingRoute.class);
    @Override
    public Object handle(Request request, Response response) throws Exception {
        String participantId = request.params("participantId");
        String studyGuid = "cmi-pancan";
        String basicUrl = "https://pepper-dev.datadonationplatform.org/pepper/v1/user/user_guid/studies/study_guid/activities";
        String sendRequest = basicUrl.replace("study_guid", studyGuid).replace("user_guid", participantId);
        logger.info("Requesting data from dss" + " w/ " + sendRequest);
        org.apache.http.client.fluent.Request getRequest = SecurityUtil.createGetRequestNoToken(sendRequest);
        Map<String, String> headers = new HashMap<>();
        String tokenFromHeader = Utility.getTokenFromHeader(request);
        headers.put("Authorization", "Bearer " + tokenFromHeader);
        if (headers != null) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                getRequest = getRequest.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
        HttpResponse  objects = getRequest.execute().returnResponse();;
        HttpEntity entity = objects.getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");
        System.out.println(responseString);
        if (objects != null) {
            logger.info("Got response back");
        }
        return responseString;
    }
}

