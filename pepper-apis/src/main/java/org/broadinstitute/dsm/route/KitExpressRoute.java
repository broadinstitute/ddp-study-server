package org.broadinstitute.dsm.route;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.easypost.model.Shipment;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.EasypostLabelRate;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitType;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.*;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.Request;
import spark.Response;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class KitExpressRoute extends RequestHandler {

    public static final String EMAIL_TYPE = "GP_EXPRESS_NOTIFICATION";

    public static final String KITREQUEST_LINK = "/permalink/unsentOverview";

    private NotificationUtil notificationUtil;

    public KitExpressRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (UserUtil.checkUserAccess(null, userId, "kit_express")) {
            String kitRequestId = request.params(RequestParameter.KITREQUESTID);
            if (StringUtils.isNotBlank(kitRequestId)) {
                if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
                    return getRateForOvernightExpress(kitRequestId);
                }
                if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
                    String userIdRequest = UserUtil.getUserId(request);
                    if (!userId.equals(userIdRequest)) {
                        throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
                    }
                    expressKitRequest(kitRequestId, userIdRequest);
                    return new Result(200);
                }
                throw new RuntimeException("Request method not known");
            }
            else {
                throw new RuntimeException("KitRequestId was missing");
            }
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    public void expressKitRequest(@NonNull String kitRequestId, @NonNull String userId) {
        KitRequestShipping kitRequest = KitRequestShipping.getKitRequest(kitRequestId);
        //deactivate kit which is  already in db and refund the label
        KitRequestShipping.deactivateKitRequest(kitRequestId, KitRequestShipping.DEACTIVATION_REASON, DSMServer.getDDPEasypostApiKey(kitRequest.getRealm()), userId);
        //add new kit into db
        KitRequestShipping.reactivateKitRequest(kitRequestId);

        DDPInstance ddpInstance = DDPInstance.getDDPInstance(kitRequest.getRealm());

        EasyPostUtil easyPostUtil = new EasyPostUtil(kitRequest.getRealm());
        HashMap<String, KitType> kitTypes = org.broadinstitute.dsm.model.KitType.getKitLookup();
        String key = kitRequest.getKitType() + "_" + ddpInstance.getDdpInstanceId();
        KitType kitType = kitTypes.get(key);

        Map<Integer, KitRequestSettings> carrierServiceTypes = KitRequestSettings.getKitRequestSettings(ddpInstance.getDdpInstanceId());
        KitRequestSettings kitRequestSettings = carrierServiceTypes.get(kitType.getKitTypeId());

        String kitId = getKitId(kitRequestId);
        //trigger label creation
        createExpressLabelToParticipant(easyPostUtil, kitRequestSettings, kitType, kitId, kitRequest.getEasypostAddressId(), ddpInstance);
    }

    private void createExpressLabelToParticipant(@NonNull EasyPostUtil easyPostUtil, @NonNull KitRequestSettings kitRequestSettings,
                                                 @NonNull KitType kitType, @NonNull String kitId, @NonNull String addressToId, @NonNull DDPInstance ddpInstance) {
        String errorMessage = "";
        Shipment participantShipment = null;
        Address toAddress = null;
        try {
            toAddress = KitRequestShipping.getToAddressId(easyPostUtil, kitRequestSettings, addressToId, null);
            participantShipment = KitRequestShipping.getShipment(easyPostUtil, ddpInstance.getBillingReference(),
                    kitType, kitRequestSettings, toAddress, "FedEx", kitRequestSettings.getCarrierToId(), "FIRST_OVERNIGHT");
            doNotification(ddpInstance.getName());
        }
        catch (Exception e) {
            errorMessage = "To: " + e.getMessage();
        }
        KitRequestShipping.updateKit(kitId, participantShipment, null, errorMessage, toAddress, true);
    }

    private String getKitId(@NonNull String kitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_UPLOADED_KITS) + QueryExtension.KIT_BY_KIT_REQUEST_ID)) {
                stmt.setString(1, kitRequestId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.DSM_KIT_ID);
                    }
                }
            }
            catch (SQLException ex) {
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
            String key = kitRequest.getKitType() + "_" + ddpInstance.getDdpInstanceId();
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
        Recipient emailRecipient = new Recipient(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.EMAIL_GP_RECIPIENT));
        emailRecipient.setUrl(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.EMAIL_FRONTEND_URL_FOR_LINKS) + KITREQUEST_LINK);
        emailRecipient.setSurveyLinks(mapy);
        notificationUtil.queueCurrentAndFutureEmails(EMAIL_TYPE, emailRecipient, EMAIL_TYPE);
    }
}
