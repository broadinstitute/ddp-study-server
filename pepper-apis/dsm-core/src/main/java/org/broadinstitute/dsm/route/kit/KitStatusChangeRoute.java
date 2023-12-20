package org.broadinstitute.dsm.route.kit;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.kit.ScanResult;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public abstract class KitStatusChangeRoute extends RequestHandler {

    protected NotificationUtil notificationUtil;

    public KitStatusChangeRoute(NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    /**
     * Checks the auth, converts the payload, and then calls
     * {@link #processRequest(KitPayload)} and returns the resulting
     * list of scan results.
     */
    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        String userIdRequest = UserUtil.getUserId(request);
        QueryParamsMap queryParams = request.queryMap();
        String realm = queryParams.get(RoutePath.REALM).value();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        if (UserUtil.checkUserAccess(null, userId, "kit_shipping", userIdRequest) || UserUtil.checkUserAccess(null, userId, "kit_receiving",
                userIdRequest)) {
            List<ScanResult> scanResultList = new ArrayList<>();
            List<? extends ScanPayload> scanPayloads = getScanPayloads(requestBody);
            int labelCount = scanPayloads.size();
            if (labelCount > 0) {
                KitPayload kitPayload = new KitPayload(scanPayloads, Integer.valueOf(userIdRequest), ddpInstanceDto);
                scanResultList = processRequest(kitPayload);
            }
            return scanResultList;
        } else {
            response.status(500);
            return UserErrorMessages.NO_RIGHTS;
        }
    }

    /**
     * This is where the bulk of the route logic goes--consume the kit payload
     * and return a scanresult
     */
    protected abstract List<ScanResult> processRequest(KitPayload kitPayload);

    protected abstract List<? extends ScanPayload> getScanPayloads(String requestBody);


}
