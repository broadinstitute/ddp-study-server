package org.broadinstitute.lddp.handlers;

import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.Recipient;
import org.broadinstitute.lddp.handlers.util.EmptyPayload;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.util.EDCClient;
import org.broadinstitute.lddp.util.MedicalInfoCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Map;

public class GetParticipantMedicalInfoHandler extends AbstractRequestHandler<EmptyPayload>  {

    private static final Logger logger = LoggerFactory.getLogger(GetParticipantMedicalInfoHandler.class);

    private static final String LOG_PREFIX = "PROCESS MEDICAL INFO REQUEST - ";

    private MedicalInfoCollector medicalInfoCollector;

    public GetParticipantMedicalInfoHandler(EDCClient edc, Config config, @NonNull MedicalInfoCollector medicalInfoCollector) {
        super(EmptyPayload.class, edc, config);

        this.medicalInfoCollector = medicalInfoCollector;
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            logger.info(LOG_PREFIX + "Start processing request...");

            DatStatUtil datStatUtil = (DatStatUtil)edc;

            Recipient recipient = datStatUtil.getSimpleParticipantInfoByAltPid(pathParams.get(":id"));

            if (recipient == null)
            {
                logger.error(LOG_PREFIX + "Unable to find medical info for participant using id = " + pathParams.get(":id"));
                return new Result(404);
            }

            String json = new GsonBuilder().serializeNulls().create().toJson(medicalInfoCollector.generateMedicalInfo(recipient, datStatUtil));

            return new Result(200, json);
        }
        catch (Exception e) {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}
