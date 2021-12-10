package org.broadinstitute.lddp.handlers;

import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.datstat.SurveyConfig;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.handlers.util.SurveyInfo;
import org.broadinstitute.lddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;
import org.broadinstitute.lddp.handlers.util.EmptyPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GetFollowUpListHandler extends AbstractRequestHandler<EmptyPayload> {

    private static final Logger logger = LoggerFactory.getLogger(GetFollowUpListHandler.class);

    private static List<SurveyInfo> followUpList = null;

    public GetFollowUpListHandler(EDCClient edc, Config conf) {
        super(EmptyPayload.class, edc, conf);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            buildList();
        }
        catch (Exception ex) {
            logger.error("An error occurred trying to create the follow-up survey list.", ex);
            return new Result(500);
        }

        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(followUpList));
    }

    private synchronized void buildList() {
        if (followUpList == null) {
            followUpList = new ArrayList<>();
            for (SurveyConfig config: ((DatStatUtil)edc).getSurveyConfigMap().values()) {
                if (config.getFollowUpType() != SurveyConfig.FollowUpType.NONE) {
                    followUpList.add(new SurveyInfo(config.getSurveyPathName(), config.getFollowUpType()));
                }
            }
        }
    }
}