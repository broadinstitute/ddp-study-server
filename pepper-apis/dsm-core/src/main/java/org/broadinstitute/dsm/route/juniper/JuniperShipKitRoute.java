package org.broadinstitute.dsm.route.juniper;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponseError;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.model.nonpepperkit.ShipKitRequest;
import spark.Request;
import spark.Response;
import spark.Route;

public class JuniperShipKitRoute implements Route {

    NonPepperKitCreationService kitCreationService;

    public JuniperShipKitRoute(NonPepperKitCreationService kitCreationService) {
        this.kitCreationService = kitCreationService;
    }

    public KitResponse createNonPepperKit(Request request, Response response) {
        ShipKitRequest shipKitRequest = new Gson().fromJson(request.body(), ShipKitRequest.class);
        JuniperKitRequest juniperKitRequest = shipKitRequest.getJuniperKitRequest();
        if (juniperKitRequest == null) {
            response.status(400);
            return new KitResponseError("EMPTY_REQUEST", null, juniperKitRequest);
        }
        if (StringUtils.isBlank(shipKitRequest.getJuniperStudyGUID())) {
            response.status(400);
            return new KitResponseError("EMPTY_STUDY_NAME", null, null);
        }
        if (StringUtils.isBlank(shipKitRequest.getKitType())) {
            response.status(400);
            return new KitResponseError("EMPTY_KIT_TYPE", null, shipKitRequest.getKitType());
        }
        KitResponse kitResponse = this.kitCreationService.createNonPepperKit(juniperKitRequest, shipKitRequest.getJuniperStudyGUID(),
                shipKitRequest.getKitType());
        if (kitResponse instanceof KitResponseError) {
            response.status(200);
        } else {
            response.status(400);
        }
        return kitResponse;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        return createNonPepperKit(request, response);
    }
}
