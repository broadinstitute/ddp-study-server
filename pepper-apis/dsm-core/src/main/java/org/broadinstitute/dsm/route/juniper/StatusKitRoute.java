package org.broadinstitute.dsm.route.juniper;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class StatusKitRoute implements Route {
    NonPepperStatusKitService nonPepperStatusKitService;

    public StatusKitRoute(NonPepperStatusKitService nonPepperStatusKitService) {
        this.nonPepperStatusKitService = nonPepperStatusKitService;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        if (request.url().contains(RoutePath.STATUS_KIT_ENDPOINT_STUDY)) {
            String study = request.params(RequestParameter.STUDY);
            log.info(String.format("Got a request to return information of kits in non-pepper study %s", study));
            return this.nonPepperStatusKitService.getKitsBasedOnStudyName(study);
        }
        if (request.url().contains(RoutePath.STATUS_KIT_ENDPOINT_JUNIPER_KIT_ID)) {
            String juniperKitId = request.params(RequestParameter.JUNIPER_KIT_ID);
            log.info(String.format("Got a request to return information of kit with Juniper Kit Id %s", juniperKitId));
            return this.nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperKitId);
        }
        if (request.url().contains(RoutePath.STATUS_KIT_ENDPOINT_PARTICIPANT_KIT_ID)) {
            String participantId = request.params(RequestParameter.JUNIPER_PARTICIPANT_ID);
            log.info(String.format("Got a request to return information of kit with Juniper Kit Id %s", participantId));
            return this.nonPepperStatusKitService.getKitsBasedOnParticipantId(participantId);
        }

        return null;
    }
}
