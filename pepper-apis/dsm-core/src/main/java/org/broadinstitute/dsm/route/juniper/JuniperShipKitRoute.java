package org.broadinstitute.dsm.route.juniper;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.nonpepperkit.JuniperKitRequest;
import org.broadinstitute.dsm.model.nonpepperkit.KitResponse;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.model.nonpepperkit.ShipKitRequest;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.EasyPostUtil;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * This is the route that is called by Juniper to create a new kit in DSM
 */

@Slf4j
public class JuniperShipKitRoute implements Route {

    private final NonPepperKitCreationService kitCreationService;

    public JuniperShipKitRoute() {
        this.kitCreationService = new NonPepperKitCreationService();
    }

    public KitResponse createNonPepperKit(Request request, Response response) {
        try {
            ShipKitRequest shipKitRequest = new Gson().fromJson(request.body(), ShipKitRequest.class);

            JuniperKitRequest juniperKitRequest = shipKitRequest.getJuniperKitRequest();
            if (juniperKitRequest == null) {
                response.status(400);
                return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.EMPTY_REQUEST, null, null);
            }
            if (StringUtils.isBlank(shipKitRequest.getJuniperStudyGUID())) {
                response.status(400);
                return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.EMPTY_STUDY_NAME, null, null);
            }
            if (StringUtils.isBlank(shipKitRequest.getKitType())) {
                response.status(400);
                return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.EMPTY_KIT_TYPE, null, shipKitRequest.getKitType());
            }
            //getting the instance with isHasRole being set to true if the instance has role juniper_study
            String studyGuid = shipKitRequest.getJuniperStudyGUID();
            DDPInstance ddpInstance = DDPInstance.getDDPInstanceWithRoleByStudyGuid(studyGuid, DBConstants.JUNIPER_STUDY_INSTANCE_ROLE);
            if (ddpInstance == null) {
                log.warn(studyGuid + " is not a study!");
                response.status(400);
                return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.UNKNOWN_STUDY, juniperKitRequest.getJuniperKitId(),
                        studyGuid);
            }
            //check it the study is set to use this endpoint
            if (!ddpInstance.isHasRole()) {
                log.info(studyGuid + " is not a Juniper study!");
                response.status(400);
                return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.UNKNOWN_STUDY, juniperKitRequest.getJuniperKitId(),
                        studyGuid);
            }

            log.info("Setup EasyPost...");
            EasyPostUtil easyPostUtil = new EasyPostUtil(ddpInstance.getName());
            KitResponse kitResponse =
                    this.kitCreationService.createNonPepperKit(juniperKitRequest, shipKitRequest.getKitType(), easyPostUtil,
                            ddpInstance);
            if (!kitResponse.isError()) {
                response.status(200);
            } else {
                response.status(400);
            }
            return kitResponse;
        } catch (JsonSyntaxException exception) {
            response.status(400);
            log.warn("Bad Json Syntax exception, will return 400", exception);
            return KitResponse.makeKitResponseError(KitResponse.ErrorMessage.JSON_SYNTAX_EXCEPTION, null, exception);
        }
    }

    @Override
    public Object handle(Request request, Response response) {
        return createNonPepperKit(request, response);
    }
}
