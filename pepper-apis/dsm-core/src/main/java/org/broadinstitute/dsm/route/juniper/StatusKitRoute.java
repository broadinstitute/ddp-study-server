package org.broadinstitute.dsm.route.juniper;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponseError;
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
        KitResponse kitResponse;
        if (request.requestMethod().equals(RoutePath.RequestMethod.GET.toString()) && request.url().contains(RoutePath.KIT_STATUS_STUDY)) {
            String study = request.params(RequestParameter.STUDY);
            log.info(String.format("Got a request to return information of kits in non-pepper study %s", study));
            kitResponse = this.nonPepperStatusKitService.getKitsBasedOnStudyName(study);
        } else if (request.requestMethod().equals(RoutePath.RequestMethod.GET.toString())
                && request.url().contains(RoutePath.KIT_STATUS_JUNIPER_KIT_ID)) {
            String juniperKitId = request.params(RequestParameter.JUNIPER_KIT_ID);
            log.info(String.format("Got a request to return information of kit with Juniper Kit Id %s", juniperKitId));
            kitResponse = this.nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperKitId);
        } else if (request.requestMethod().equals(RoutePath.RequestMethod.GET.toString())
                && request.url().contains(RoutePath.KIT_STATUS_PARTICIPANT_ID)) {
            String participantId = request.params(RequestParameter.JUNIPER_PARTICIPANT_ID);
            log.info(String.format("Got a request to return information of kit with Juniper Kit Id %s", participantId));
            kitResponse = this.nonPepperStatusKitService.getKitsBasedOnParticipantId(participantId);
        } else if (request.requestMethod().equals(RoutePath.RequestMethod.POST.toString())
                && request.url().contains(RoutePath.KIT_STATUS_ENDPOINT_KIT_IDS)) {
            String[] kitIds = new Gson().fromJson(request.queryMap().get(RoutePath.JUNIPER_KIT_IDS).value(), String[].class);
            try {
                kitResponse = this.nonPepperStatusKitService.getKitsFromKitIds(kitIds);
            } catch (DSMBadRequestException e) {
                kitResponse = new KitResponseError(KitResponseError.ErrorMessage.MISSING_JUNIPER_KIT_ID, null, kitIds);
            }
        } else {
            response.status(400);
            return KitResponseError.ErrorMessage.NOT_IMPLEMENTED;
        }
        if (!(kitResponse instanceof KitResponseError)) {
            response.status(200);
        } else {
            response.status(400);
        }
        return kitResponse;
    }
}
