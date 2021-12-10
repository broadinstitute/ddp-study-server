package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.BSPKit;
import org.broadinstitute.dsm.model.bsp.BSPKitStatus;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.NotificationUtil;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.Optional;

public class BSPKitRoute implements Route {

    private NotificationUtil notificationUtil;

    public BSPKitRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object handle(Request request, Response response) {
        String kitLabel = request.params(RequestParameter.LABEL);
        if (StringUtils.isBlank(kitLabel)) {
            throw new RuntimeException("Please include a kit label as a path parameter");
        }
        BSPKit bspKit = new BSPKit();
        if (!bspKit.canReceiveKit(kitLabel)) {
            Optional<BSPKitStatus> result = bspKit.getKitStatus(kitLabel, notificationUtil);
            if(result.isEmpty()){
                response.status(404);
                return null;
            }
            return result.get();
        }

        return bspKit.receiveBSPKit(kitLabel, this.notificationUtil).get();

    }
}
