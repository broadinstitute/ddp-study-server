package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitStatus;
import org.broadinstitute.dsm.model.ParticipantKits;
import org.broadinstitute.dsm.model.ddp.DDPListOfParticipants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.SystemUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BatchKitsRoute implements Route {
    private static final Logger logger = LoggerFactory.getLogger(BatchKitsRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String study = request.params(RequestParameter.REALM);
        String ids = null;
        if (StringUtils.isNotBlank(study)) {
            HttpServletRequest rawRequest = request.raw();
            String content = SystemUtil.getBody(rawRequest);
            String[] ddpParticipantIds = new Gson().fromJson(content, DDPListOfParticipants.class).participantIds;
            DDPInstance ddpInstance = DDPInstance.getDDPInstance(study);
            List<ParticipantKits> results = new ArrayList<>();
            if (ddpInstance != null) {
                if (ddpParticipantIds != null && ddpParticipantIds.length != 0) {
                    Map<String, List<KitStatus>> kitRequests = KitStatus.getBatchOfSampleStatus(ddpInstance.getDdpInstanceId());

                    for (String ddpParticipantId : ddpParticipantIds) {
                        if (kitRequests.containsKey(ddpParticipantId)) {
                            List<KitStatus> samples = kitRequests.get(ddpParticipantId);
                            results.add(new ParticipantKits(ddpParticipantId, samples));
                        }
                    }

                }
                logger.info("Sending a list of " + results.size() + " kit status for batch of participants for study " + study);
                return results;
            }
            logger.error("No study found for: " + study);
            response.status(404);
            return results;
        }
        logger.error("No value was sent for realm");
        response.status(500);
        return new ArrayList<>();
    }
}
