package org.broadinstitute.dsm.route;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public abstract class KitStatusChangeRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitStatusChangeRoute.class);

    protected NotificationUtil notificationUtil;
    protected KitPayload kitPayload;
    protected List<ScanError> scanErrorList;

    public KitStatusChangeRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        String userIdRequest = UserUtil.getUserId(request);
        QueryParamsMap queryParams = request.queryMap();
        String realm = queryParams.get(RoutePath.REALM).value();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        if (UserUtil.checkUserAccess(null, userId, "kit_shipping", userIdRequest) || UserUtil.checkUserAccess(null, userId, "kit_receiving",
                userIdRequest)) {
            scanErrorList = new ArrayList<>();
            List<ScanPayload> scanPayloads = ObjectMapperSingleton.readValue(requestBody, new TypeReference<List<ScanPayload>>() {});
            int labelCount = scanPayloads.size();
            if (labelCount > 0) {
                kitPayload = new KitPayload(scanPayloads, Integer.valueOf(userIdRequest), ddpInstanceDto);
                processRequest();
            }
            return scanErrorList;
        } else {
            response.status(500);
            return UserErrorMessages.NO_RIGHTS;
        }
    }

    protected abstract void processRequest();


    public static class ScanError {
        private String kit;
        private String error;

        public ScanError(String kit, String error) {
            this.kit = kit;
            this.error = error;
        }
    }
}
