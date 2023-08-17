package org.broadinstitute.dsm.route.juniper;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponseError;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.model.nonpepperkit.ShipKitRequest;
import org.broadinstitute.dsm.util.EasyPostUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
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
        //getting the instance with isHasRole being set to true if the instance has role juniper_study
        String studyGuid = shipKitRequest.getJuniperStudyGUID();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(studyGuid, "juniper_study");
        if (ddpInstance == null) {
            log.error(studyGuid + " is not a study!");
            response.status(400);
            return new KitResponseError(KitResponse.UsualErrorMessage.UNKNOWN_STUDY.getMessage(), juniperKitRequest.getJuniperKitId(),
                    studyGuid);
        }
        if (!ddpInstance.isHasRole()) {
            log.error(studyGuid + " is not a Juniper study!");
            response.status(400);
            return new KitResponseError(KitResponse.UsualErrorMessage.UNKNOWN_STUDY.getMessage(), juniperKitRequest.getJuniperKitId(),
                    studyGuid);
        }

        log.info("Setup EasyPost...");
        EasyPostUtil easyPostUtil = new EasyPostUtil(ddpInstance.getName());
        KitResponse kitResponse = this.kitCreationService.createNonPepperKit(juniperKitRequest, shipKitRequest.getKitType(), easyPostUtil,
                ddpInstance);
        if (!(kitResponse instanceof KitResponseError)) {
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
