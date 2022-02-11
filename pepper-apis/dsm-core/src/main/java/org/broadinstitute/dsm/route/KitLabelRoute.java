package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.KitRequestCreateLabel;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.util.Optional;

public class KitLabelRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
            return new Result(200, String.valueOf(DBUtil.getBookmark(KitUtil.BOOKMARK_LABEL_CREATION_RUNNING)));
        }
        else {
            String userIdRequest = UserUtil.getUserId(request);
            if (UserUtil.checkUserAccess(null, userId, "kit_shipping", userIdRequest)) {
                QueryParamsMap queryParams = request.queryMap();

                String realm = null;
                if (queryParams.value(RoutePath.REALM) != null) {
                    realm = queryParams.get(RoutePath.REALM).value();
                }

                DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(realm)
                        .orElseThrow();

                String requestBody = request.body();
                if (requestBody != null) {
                    KitRequestShipping[] kitRequests = new Gson().fromJson(requestBody, KitRequestShipping[].class);
                    if (kitRequests != null) {
                        KitRequestCreateLabel.updateKitLabelRequested(kitRequests, userIdRequest, ddpInstanceDto);
                        return new Result(200);
                    }
                }

                String kitType = null;
                if (queryParams.value(RoutePath.KIT_TYPE) != null) {
                    kitType = queryParams.get(RoutePath.KIT_TYPE).value();
                }
                KitRequestCreateLabel.updateKitLabelRequested(realm, kitType, userIdRequest, ddpInstanceDto);
                return new Result(200);
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
    }
}
