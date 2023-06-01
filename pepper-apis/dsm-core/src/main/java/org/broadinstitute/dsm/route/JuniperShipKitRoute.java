package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitStatus;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.model.nonpepperkit.ShipKitRequest;
import org.broadinstitute.dsm.security.RequestHandler;
import spark.Request;
import spark.Response;

public class JuniperShipKitRoute extends RequestHandler {

    NonPepperKitCreationService kitCreationService;

    public JuniperShipKitRoute(NonPepperKitCreationService kitCreationService) {
        this.kitCreationService = kitCreationService;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        return createNonPepperKit(request, response);
    }

    public KitResponse createNonPepperKit(Request request, Response response) {
        ShipKitRequest shipKitRequest = new Gson().fromJson(request.body(), ShipKitRequest.class);
        JuniperKitRequest juniperKitRequest = shipKitRequest.getJuniperKitRequest();
        if (juniperKitRequest == null) {
            response.status(400);
            return new KitResponse("EMPTY_REQUEST", null, juniperKitRequest);
        }
        if (StringUtils.isBlank(shipKitRequest.getJuniperStudyGUID())) {
            response.status(400);
            return new KitResponse("EMPTY_STUDY_NAME", null, null);
        }
        if (StringUtils.isBlank(shipKitRequest.getKitType())) {
            response.status(400);
            return new KitResponse("EMPTY_KIT_TYPE", null, shipKitRequest.getKitType());
        }
        KitResponse kitResponse = this.kitCreationService.createNonPepperKit(juniperKitRequest, shipKitRequest.getJuniperStudyGUID(),
                shipKitRequest.getKitType());
        if (kitResponse instanceof JuniperKitStatus) {
            response.status(200);
        } else {
            response.status(400);
        }
        return kitResponse;
    }


}
