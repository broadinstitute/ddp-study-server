package org.broadinstitute.lddp.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.handlers.util.EmptyPayload;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.util.EDCClient;
import org.broadinstitute.lddp.util.Utility;
import org.broadinstitute.lddp.util.ApiRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;

import java.util.*;

public class GetDrugListHandler extends AbstractRequestHandler<EmptyPayload>
{
    private static final Logger logger = LoggerFactory.getLogger(GetDrugListHandler.class);

    // static list is essentially a cache since the drugs list will not change often
    private static List<String> drugs = new ArrayList<>();

    public GetDrugListHandler(EDCClient edc, Config config) {
        super(EmptyPayload.class, edc, config);

        synchronized (drugs) {
            if (drugs.isEmpty()) {
                drugs = getDrugs(config);
            }
        }
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, spark.Response response) {
        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(drugs));
    }

    public static List<String> getDrugs(Config config) {
        List<String> drugList = null;

        try {
            // retrieval of vault values is manual for Gen2 MBC right now, so we only check that it's present
            if (config.hasPath("dsm.defaultSecret")) {

                // Call the DSM endpoint, and put the results into drugList
                String url = config.getString("dsm.url");
                String requestUrlString = url + "/app/drugs";
                String requestName = "get drug list";

                Map<String, String> headers =  ApiRequest.buildHeaders(config.getString("dsm.defaultSecret"));
                String content = ApiRequest.performGet(requestUrlString, requestName, headers);

                if (StringUtils.isBlank(content)) {
                    throw new RuntimeException("Call to retrieve drug list did not return data.");
                }

                drugList = new Gson().fromJson(content, List.class);

                if ((drugList == null)||(drugList.isEmpty())) {
                    throw new RuntimeException("Unable to create drug list.");
                }
            }
            else if ((config.getString("portal.environment").equals(Utility.Deployment.UNIT_TEST.toString()))||
                    (config.getString("portal.environment").equals(Utility.Deployment.LOCAL_DEV.toString()))||
                    (config.getString("portal.environment").equals(Utility.Deployment.REMOTE_DEV.toString()))
                    ) {
                logger.warn("DRUG LIST - Using fake DSM drug list...");
                drugList = config.getStringList("testDrugList");
            }
            else {
                throw new RuntimeException("Required DSM drug list configuration missing.");
            }
        }
        catch (Exception ex) {
            throw new RuntimeException("An error occurred while attempting to load the drug list.", ex);
        }

        return drugList;
    }
}

