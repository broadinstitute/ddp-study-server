package org.broadinstitute.dsm.route.juniper;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class StatusKitRoute implements Route {
    private final NonPepperStatusKitService nonPepperStatusKitService;

    public StatusKitRoute() {
        this.nonPepperStatusKitService = new NonPepperStatusKitService();
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        KitResponse kitResponse;
        if (request.requestMethod().equals(RoutePath.RequestMethod.GET.toString())) {
            if (request.url().contains(RoutePath.KIT_STATUS_STUDY)) {
                String study = request.params(RequestParameter.STUDY);
                log.info(String.format("Got a request to return information of kits in non-pepper study %s", study));
                kitResponse = this.nonPepperStatusKitService.getKitsByStudyName(study);
            } else if (request.url().contains(RoutePath.KIT_STATUS_JUNIPER_KIT_ID)) {
                String juniperKitId = request.params(RequestParameter.JUNIPER_KIT_ID);
                log.info(String.format("Got a request to return information of kit with Juniper Kit Id %s", juniperKitId));
                kitResponse = this.nonPepperStatusKitService.getKitsBasedOnJuniperKitId(juniperKitId);
            } else if (request.url().contains(RoutePath.KIT_STATUS_PARTICIPANT_ID)) {
                String participantId = request.params(RequestParameter.JUNIPER_PARTICIPANT_ID);
                log.info(String.format("Got a request to return information of kit with Juniper Kit Id %s", participantId));
                kitResponse = this.nonPepperStatusKitService.getKitsBasedOnParticipantId(participantId);
            } else {
                response.status(400);
                return KitResponse.ErrorMessage.NOT_IMPLEMENTED;
            }
        } else if (request.requestMethod().equals(RoutePath.RequestMethod.POST.toString())
                && request.url().contains(RoutePath.KIT_STATUS_ENDPOINT_KIT_IDS)) {
            try {
                String[] kitIds = new Gson().fromJson(request.queryMap().get(RoutePath.JUNIPER_KIT_IDS).value(), String[].class);
                kitResponse = getStatusByKitIdList(kitIds);
            } catch (JsonSyntaxException e) {
                log.warn("Json does not have the expected syntax", e);
                return new KitResponse().makeKitResponseError(KitResponse.ErrorMessage.JSON_SYNTAX_EXCEPTION, null, e);
            }
        } else {
            response.status(400);
            return new KitResponse().makeKitResponseError(KitResponse.ErrorMessage.NOT_IMPLEMENTED, null, null);
        }
        if (!kitResponse.isError()) {
            response.status(200);
        } else {
            response.status(400);
        }
        return kitResponse;
    }

    private KitResponse getStatusByKitIdList(String[] kitIds) {
        try {
            return this.nonPepperStatusKitService.getKitsFromKitIds(kitIds);
        } catch (DSMBadRequestException e) {
            return new KitResponse().makeKitResponseError(KitResponse.ErrorMessage.MISSING_JUNIPER_KIT_ID, null, kitIds);
        }
    }
}
