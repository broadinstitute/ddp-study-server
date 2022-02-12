package org.broadinstitute.dsm.route;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.easypost.model.Shipment;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.EasypostLabelRate;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.email.Recipient;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

public class KitExpressRoute extends RequestHandler {

    public static final String EMAIL_TYPE = "GP_EXPRESS_NOTIFICATION";

    public static final String KITREQUEST_LINK = "/permalink/unsentOverview";

    private NotificationUtil notificationUtil;

    public KitExpressRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String userIdRequest = UserUtil.getUserId(request);
        if (UserUtil.checkUserAccess(null, userId, "kit_express", userIdRequest)) {
            String kitRequestId = request.params(RequestParameter.KITREQUESTID);
            if (StringUtils.isNotBlank(kitRequestId)) {
                if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
                    return getRateForOvernightExpress(kitRequestId);
                }
                if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
                    expressKitRequest(kitRequestId, userIdRequest);
                    return new Result(200);
                }
                throw new RuntimeException("Request method not known");
            } else {
                throw new RuntimeException("KitRequestId was missing");
            }
        } else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    public void expressKitRequest(@NonNull String kitRequestId, @NonNull String userId) {
        KitRequestShipping kitRequest = KitRequestShipping.getKitRequest(kitRequestId);

        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(kitRequest.getRealm()).orElseThrow();

        //deactivate kit which is  already in db and refund the label
        KitRequestShipping.deactivateKitRequest(Long.parseLong(kitRequestId), KitRequestShipping.DEACTIVATION_REASON,
                DSMServer.getDDPEasypostApiKey(kitRequest.getRealm()), userId, ddpInstanceDto);
        //add new kit into db
        KitRequestShipping.reactivateKitRequest(kitRequestId, ddpInstanceDto);


        EasyPostUtil easyPostUtil = new EasyPostUtil(kitRequest.getRealm());
        HashMap<String, KitType> kitTypes = org.broadinstitute.dsm.model.KitType.getKitLookup();
        String key = kitRequest.getKitTypeName() + "_" + ddpInstanceDto.getDdpInstanceId();
        KitType kitType = kitTypes.get(key);

        Map<Integer, KitRequestSettings> carrierServiceTypes =
                KitRequestSettings.getKitRequestSettings(String.valueOf(ddpInstanceDto.getDdpInstanceId()));
        KitRequestSettings kitRequestSettings = carrierServiceTypes.get(kitType.getKitTypeId());

        String kitId = getKitId(kitRequestId);
        //trigger label creation
        createExpressLabelToParticipant(easyPostUtil, kitRequestSettings, kitType, kitId, kitRequest.getEasypostAddressId(),
                ddpInstanceDto);
    }

    private void createExpressLabelToParticipant(@NonNull EasyPostUtil easyPostUtil, @NonNull KitRequestSettings kitRequestSettings,
                                                 @NonNull KitType kitType, @NonNull String kitId, @NonNull String addressToId,
                                                 DDPInstanceDto ddpInstanceDto) {
        String errorMessage = "";
        Shipment participantShipment = null;
        Address toAddress = null;
        try {
            toAddress = KitRequestShipping.getToAddressId(easyPostUtil, kitRequestSettings, addressToId, null);
            participantShipment = KitRequestShipping.getShipment(easyPostUtil, ddpInstanceDto.getBillingReference(),
                    kitType, kitRequestSettings, toAddress, "FedEx", kitRequestSettings.getCarrierToId(), "FIRST_OVERNIGHT");
            doNotification(ddpInstanceDto.getInstanceName());
        } catch (Exception e) {
            errorMessage = "To: " + e.getMessage();
        }
        KitRequestShipping.updateKit(kitId, participantShipment, null, errorMessage, toAddress, true, ddpInstanceDto);
    }

    private String getKitId(@NonNull String kitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    ConfigUtil.getSqlFromConfig(ApplicationConfigConstants.GET_UPLOADED_KITS) + QueryExtension.KIT_BY_KIT_REQUEST_ID)) {
                stmt.setString(1, kitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.DSM_KIT_ID);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting dsm_kit_id for kit w/ dsm_kit_request_id " + kitRequestId, results.resultException);
        }
        return (String) results.resultValue;
    }

    private EasypostLabelRate getRateForOvernightExpress(@NonNull String kitRequestId) throws EasyPostException {
        KitRequestShipping kitRequest = KitRequestShipping.getKitRequest(kitRequestId);
        if (StringUtils.isNotBlank(kitRequest.getEasypostToId())) {
            DDPInstance ddpInstance = DDPInstance.getDDPInstance(kitRequest.getRealm());

            HashMap<String, KitType> kitTypes = org.broadinstitute.dsm.model.KitType.getKitLookup();
            String key = kitRequest.getKitTypeName() + "_" + ddpInstance.getDdpInstanceId();
            KitType kitType = kitTypes.get(key);

            Map<Integer, KitRequestSettings> carrierServiceTypes = KitRequestSettings.getKitRequestSettings(ddpInstance.getDdpInstanceId());
            KitRequestSettings kitRequestSettings = carrierServiceTypes.get(kitType.getKitTypeId());

            return EasyPostUtil.getExpressRate(kitRequest.getEasypostToId(), DSMServer.getDDPEasypostApiKey(kitRequest.getRealm()),
                    kitRequestSettings.getCarrierTo(), kitRequestSettings.getServiceTo());
        }
        return null;
    }

    private void doNotification(String realm) {
        String message = "An express label for " + realm + " was created.<br>";
        Map<String, String> mapy = new HashMap<>();
        mapy.put(":customText", message);
        Recipient emailRecipient = new Recipient(ConfigUtil.getSqlFromConfig(ApplicationConfigConstants.EMAIL_GP_RECIPIENT));
        emailRecipient.setUrl(ConfigUtil.getSqlFromConfig(ApplicationConfigConstants.EMAIL_FRONTEND_URL_FOR_LINKS) + KITREQUEST_LINK);
        emailRecipient.setSurveyLinks(mapy);
        notificationUtil.queueCurrentAndFutureEmails(EMAIL_TYPE, emailRecipient, EMAIL_TYPE);
    }
}
