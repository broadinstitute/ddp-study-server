package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.model.gp.BSPKit;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitStatus;
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
        Optional<BSPKitDto> optionalBSPKitDto = bspKit.canReceiveKit(kitLabel);
        //kit does not exist in ddp_kit table
        if (optionalBSPKitDto.isEmpty()) {
            response.status(404);
            return null;
        }

        //check if kit is from a pt which is withdrawn
        Optional<BSPKitStatus> result = bspKit.getKitStatus(optionalBSPKitDto.get(), notificationUtil);
        if (!result.isEmpty()) {
            return result.get();
        }

        //kit found in ddp_kit table
        return bspKit.receiveKit(kitLabel, optionalBSPKitDto.get(), this.notificationUtil).get();
    }
}
