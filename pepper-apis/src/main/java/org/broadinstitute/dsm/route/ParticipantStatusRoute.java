package org.broadinstitute.dsm.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ParticipantStatus;
import org.broadinstitute.dsm.statics.RequestParameter;
import spark.Request;
import spark.Response;
import spark.Route;

public class ParticipantStatusRoute implements Route {//doesn't need to extend RequestHandler because that route goes into another before check

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String study = request.params(RequestParameter.REALM);
        String ddpParticipantId = request.params(RequestParameter.PARTICIPANTID);
        if (StringUtils.isNotBlank(study) && StringUtils.isNotBlank(ddpParticipantId)) {
            DDPInstance ddpInstance = DDPInstance.getDDPInstanceByRequestParameter(study);
            if (ddpInstance != null) {
                ParticipantStatus participantStatus = ParticipantStatus.getParticipantStatus(ddpParticipantId, ddpInstance.getDdpInstanceId());
                return participantStatus;
            }
            response.status(404);
            return null;
        }
        response.status(500);
        return null;
    }
}
